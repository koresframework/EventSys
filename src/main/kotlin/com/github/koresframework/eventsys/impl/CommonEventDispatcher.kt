/*
 *      EventSys - Event implementation generator written on top of Kores
 *
 *         The MIT License (MIT)
 *
 *      Copyright (c) 2019 ProjectSandstone <https://github.com/ProjectSandstone/EventSys>
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

import com.github.jonathanxd.kores.type.GenericType
import com.github.jonathanxd.kores.type.asGeneric
import com.github.jonathanxd.kores.type.isAssignableFrom
import com.github.koresframework.eventsys.error.EventCancelledError
import com.github.koresframework.eventsys.error.ExceptionListenError
import com.github.koresframework.eventsys.event.Cancellable
import com.github.koresframework.eventsys.event.Event
import com.github.koresframework.eventsys.event.EventDispatcher
import com.github.koresframework.eventsys.event.EventListener
import com.github.koresframework.eventsys.logging.LoggerInterface
import com.github.koresframework.eventsys.logging.MessageType
import com.github.koresframework.eventsys.result.DispatchResult
import com.github.koresframework.eventsys.result.ListenExecutionResult
import com.github.koresframework.eventsys.result.ListenResult
import com.github.koresframework.eventsys.util.isGenericAssignableFrom
import java.lang.reflect.Type
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.function.Supplier

class CommonEventDispatcher(
        threadFactory: ThreadFactory,
        override val logger: LoggerInterface,
        private val listenersProvider: () -> Collection<EventListenerContainer<*>>
) : HelperEventDispatcher() {

    private val executor = Executors.newCachedThreadPool(threadFactory)

    override fun <T : Event> dispatch(
            event: T,
            eventType: Type,
            dispatcher: Any,
            channel: String,
            isAsync: Boolean
    ): DispatchResult<T> {

        val lazyCancelled = lazy { (event as Cancellable).isCancelled }
        val eventIsCancelled =
                if (event is Cancellable) ({ lazyCancelled.value })
                else ({ false })

        fun tryDispatch(eventListenerContainer: EventListenerContainer<*>): CompletableFuture<ListenExecutionResult<T>> =
                if (isAsync) {
                    CompletableFuture.supplyAsync(Supplier {
                        dispatchDirect(eventListenerContainer, event, eventType, dispatcher, channel)
                    }, this.executor)
                } else if (eventListenerContainer.eventListener.cancelAffected && eventIsCancelled()) {
                    CompletableFuture.completedFuture(ListenExecutionResult(
                            eventListenerContainer,
                            event,
                            eventType,
                            dispatcher,
                            channel,
                            ListenResult.Failed(EventCancelledError())
                    ))
                } else {
                    CompletableFuture.completedFuture(dispatchDirect(
                            eventListenerContainer,
                            event,
                            eventType,
                            dispatcher,
                            channel
                    ))
                }

        val dispatches = listenersProvider().filter {
            this.check(container = it, eventType = eventType, channel = channel)
        }.map {
            tryDispatch(it)
        }

        return DispatchResult(dispatches)
    }
}

abstract class HelperEventDispatcher : EventDispatcher {

    protected abstract val logger: LoggerInterface

    @Suppress("NOTHING_TO_INLINE")
    protected inline fun <T : Event> dispatchDirect(
            eventListenerContainer: EventListenerContainer<*>,
            event: T,
            eventType: Type,
            dispatcher: Any,
            channel: String
    ): ListenExecutionResult<T> {
        return try {
            val result = eventListenerContainer.eventListener.helpOnEvent(event, dispatcher)
            ListenExecutionResult(eventListenerContainer, event, eventType, dispatcher, channel, result)
        } catch (throwable: Throwable) {
            logger.log(
                    "Cannot dispatch event $event (of type: ${event.eventType})" +
                            " with provided type '$eventType' to listener " +
                            "${eventListenerContainer.eventListener} (of event type: ${eventListenerContainer.eventType}) of owner " +
                            "${eventListenerContainer.owner}. " +
                            "(Dispatcher: $dispatcher, channel: $channel)",
                    MessageType.EXCEPTION_IN_LISTENER,
                    throwable
            )
            ListenExecutionResult(eventListenerContainer, event, eventType, dispatcher, channel, ListenResult.Failed(ExceptionListenError(throwable)))
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

        return checkType() && (listenerPhase == "@all" || channel == "@all" || listenerPhase == channel)
    }

    @Suppress("UNCHECKED_CAST")
    protected fun <T : Event> EventListener<T>.helpOnEvent(event: Any, dispatcher: Any): ListenResult {
        return this.onEvent(event as T, dispatcher)
    }
}