/*
 *      EventSys - Event implementation generator written on top of Kores
 *
 *         The MIT License (MIT)
 *
 *      Copyright (c) 2021 ProjectSandstone <https://github.com/ProjectSandstone/EventSys>
 *      Copyright (c) contributors
 *
 *
 *      Permission is hereby granted, free of charge, to any person obtaining a copy
 *      of this software and associated documentation files (the "Software"), to deal
 *      in the Software without restriction, including without limitation the rights
 *      to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *      copies of the Software, and to permit persons to whom the Software is
 *      furnished to do so, subject to the following conditions:
 *
 *      The above copyright notice and this permission notice shall be included in
 *      all copies or substantial portions of the Software.
 *
 *      THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *      IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *      FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *      AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *      LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *      OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *      THE SOFTWARE.
 */
package com.github.koresframework.eventsys.impl

import com.koresframework.kores.type.GenericType
import com.koresframework.kores.type.asGeneric
import com.koresframework.kores.type.isAssignableFrom
import com.github.koresframework.eventsys.channel.ChannelSet
import com.github.koresframework.eventsys.context.EnvironmentContext
import com.github.koresframework.eventsys.error.EventCancelledError
import com.github.koresframework.eventsys.error.ExceptionListenError
import com.github.koresframework.eventsys.event.*
import com.github.koresframework.eventsys.event.EventListener
import com.github.koresframework.eventsys.gen.event.EventGenerator
import com.github.koresframework.eventsys.logging.LoggerInterface
import com.github.koresframework.eventsys.logging.MessageType
import com.github.koresframework.eventsys.result.DispatchResult
import com.github.koresframework.eventsys.result.ListenExecutionResult
import com.github.koresframework.eventsys.result.ListenResult
import com.github.koresframework.eventsys.util.isGenericAssignableFrom
import kotlinx.coroutines.*
import java.lang.reflect.Type
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.function.Supplier
import kotlin.Comparator
import kotlin.coroutines.CoroutineContext

/**
 * Common Event dispatcher implementation
 *
 * @param threadFactory Thread factory for async dispatch
 * @param logger Logger interface for error logging.
 * @param eventGenerator Event generator instance to generate listener methods.
 * @param context The coroutine context to use to concurrently run event handlers.
 */
open class CommonEventDispatcher(
        threadFactory: ThreadFactory,
        override val eventGenerator: EventGenerator,
        override val logger: LoggerInterface,
        override val context: CoroutineContext,
        val eventListenerRegistry: EventListenerRegistry
) : AbstractEventDispatcher() {

    override val executor = Executors.newCachedThreadPool(threadFactory)

    override fun <T : Event> getListeners(event: T, eventType: Type, channel: String): Iterable<EventListenerContainer<*>> {
        return this.eventListenerRegistry.getListenersContainers<T>(event, eventType, channel);
    }
}

abstract class AbstractEventDispatcher : EventDispatcher {

    protected abstract val logger: LoggerInterface
    protected abstract val executor: Executor
    protected abstract val eventGenerator: EventGenerator
    protected abstract val context: CoroutineContext

    protected abstract fun <T : Event> getListeners(
            event: T,
            eventType: Type,
            channel: String
    ): Iterable<EventListenerContainer<*>>

    override suspend fun <T : Event> dispatch(
            event: T,
            eventType: Type,
            dispatcher: Any,
            channel: String,
            isAsync: Boolean,
            ctx: EnvironmentContext
    ): DispatchResult<T> {

        val lazyCancelled = lazy { (event as Cancellable).isCancelled }
        val eventIsCancelled =
                if (event is Cancellable) ({ lazyCancelled.value })
                else ({ false })

        suspend fun tryDispatch(eventListenerContainer: EventListenerContainer<*>): Deferred<ListenExecutionResult<T>> =
                if (isAsync) {
                    CoroutineScope(this.context).async {
                        dispatchDirect(eventListenerContainer, event, eventType, dispatcher, channel, ctx)
                    }
                } else if (eventListenerContainer.eventListener.cancelAffected && eventIsCancelled()) {
                    CompletableDeferred(ListenExecutionResult(
                            eventListenerContainer,
                            event,
                            eventType,
                            dispatcher,
                            channel,
                            ListenResult.Failed(EventCancelledError()),
                            ctx
                    ))
                } else {
                    CompletableDeferred(dispatchDirect(
                            eventListenerContainer,
                            event,
                            eventType,
                            dispatcher,
                            channel,
                            ctx
                    ))
                }

        val dispatches = this.getListeners(event, eventType, channel).filter {
            this.check(container = it, eventType = eventType, channel = channel)
        }.map {
            tryDispatch(it)
        }

        return DispatchResult(this.context, dispatches)
    }

    @Suppress("NOTHING_TO_INLINE")
    protected suspend inline fun <T : Event> dispatchDirect(
            eventListenerContainer: EventListenerContainer<*>,
            event: T,
            eventType: Type,
            dispatcher: Any,
            channel: String,
            ctx: EnvironmentContext
    ): ListenExecutionResult<T> {
        return try {
            val result = eventListenerContainer.eventListener.helpOnEvent(event, dispatcher)
            ListenExecutionResult(eventListenerContainer, event, eventType, dispatcher, channel, result, ctx)
        } catch (throwable: Throwable) {
            logger.log(
                    "Cannot dispatch event $event (of type: ${event.eventType})" +
                            " with provided type '$eventType' to listener " +
                            "${eventListenerContainer.eventListener} (of event type: ${eventListenerContainer.eventType}) of owner " +
                            "${eventListenerContainer.owner}. " +
                            "(Dispatcher: $dispatcher, channel: $channel)",
                    MessageType.EXCEPTION_IN_LISTENER,
                    throwable,
                    ctx
            )
            ListenExecutionResult(eventListenerContainer, event, eventType, dispatcher, channel, ListenResult.Failed(ExceptionListenError(throwable)), ctx)
        }
    }

    protected fun check(
            container: EventListenerContainer<*>,
            eventType: Type,
            channel: String
    ): Boolean {
        fun checkType(): Boolean {
            return (container.eventType is GenericType
                    && (container.eventType as GenericType).isGenericAssignableFrom(eventType))
                    || (container.eventType !is GenericType
                    && container.eventType.isAssignableFrom(eventType))
                    || (container.eventType.asGeneric.bounds.isEmpty()
                    && container.eventType.isAssignableFrom(eventType))
        }

        val listenerPhase = container.eventListener.channel

        return checkType() && (ChannelSet.Expression.isAll(listenerPhase) || ChannelSet.Expression.isAll(channel) || listenerPhase == channel)
    }

    @Suppress("UNCHECKED_CAST")
    protected suspend fun <T : Event> EventListener<T>.helpOnEvent(event: Any, dispatcher: Any): ListenResult {
        return this.onEvent(event as T, dispatcher)
    }
}

fun eventListenerContainerComparator(sorter: Comparator<EventListener<*>>) =
        Comparator<EventListenerContainer<*>> { o1, o2 ->
            val sort = sorter.compare(o1.eventListener, o2.eventListener)

            if (sort == 0)
                -1
            else sort
        }