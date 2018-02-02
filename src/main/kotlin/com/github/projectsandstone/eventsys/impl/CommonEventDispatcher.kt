/*
 *      EventSys - Event implementation generator written on top of Kores
 *
 *         The MIT License (MIT)
 *
 *      Copyright (c) 2018 ProjectSandstone <https://github.com/ProjectSandstone/EventSys>
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
package com.github.projectsandstone.eventsys.impl

import com.github.jonathanxd.iutils.type.TypeInfo
import com.github.projectsandstone.eventsys.event.Event
import com.github.projectsandstone.eventsys.event.EventDispatcher
import com.github.projectsandstone.eventsys.event.EventListener
import com.github.projectsandstone.eventsys.logging.LoggerInterface
import com.github.projectsandstone.eventsys.logging.MessageType
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory

class CommonEventDispatcher(threadFactory: ThreadFactory,
                            override val logger: LoggerInterface,
                            private val listenersProvider: () -> Collection<EventListenerContainer<*>>) : HelperEventDispatcher() {

    private val executor = Executors.newCachedThreadPool(threadFactory)


    override fun <T : Event> dispatch(event: T, eventType: TypeInfo<T>, dispatcher: Any, channel: Int, isAsync: Boolean) {
        fun tryDispatch(eventListenerContainer: EventListenerContainer<*>) {

            if (isAsync) {
                executor.execute {
                    dispatchDirect(eventListenerContainer, event, eventType, dispatcher, channel)
                }
            } else {
                dispatchDirect(eventListenerContainer, event, eventType, dispatcher, channel)
            }
        }

        listenersProvider().filter {
            this.check(container = it, eventType = eventType, channel = channel)
        }.forEach {
            tryDispatch(it)
        }
    }
}

abstract class HelperEventDispatcher : EventDispatcher {

    protected abstract val logger: LoggerInterface

    @Suppress("NOTHING_TO_INLINE")
    protected inline fun <T : Event> dispatchDirect(eventListenerContainer: EventListenerContainer<*>,
                                                    event: T,
                                                    eventType: TypeInfo<*>,
                                                    dispatcher: Any,
                                                    channel: Int) {
        try {
            eventListenerContainer.eventListener.helpOnEvent(event, dispatcher)
        } catch (throwable: Throwable) {
            logger.log("Cannot dispatch event $event (of type: ${event.eventTypeInfo})" +
                    " with provided type '$eventType' to listener " +
                    "${eventListenerContainer.eventListener} (of event type: ${eventListenerContainer.eventType}) of owner " +
                    "${eventListenerContainer.owner}. " +
                    "(Dispatcher: $dispatcher, channel: $channel)",
                    MessageType.EXCEPTION_IN_LISTENER,
                    throwable)

        }
    }

    protected fun check(container: EventListenerContainer<*>, eventType: TypeInfo<*>, channel: Int): Boolean {

        fun checkType(): Boolean {
            return container.eventType.isAssignableFrom(eventType)
                    ||
                    (container.eventType.typeParameters.isEmpty()
                            && container.eventType.typeClass.isAssignableFrom(eventType.typeClass))
        }

        val listenerPhase = container.eventListener.channel

        return checkType() && (listenerPhase < 0 || channel < 0 || listenerPhase == channel)
    }

    @Suppress("UNCHECKED_CAST")
    protected fun <T : Event> EventListener<T>.helpOnEvent(event: Any, dispatcher: Any) {
        this.onEvent(event as T, dispatcher)
    }
}