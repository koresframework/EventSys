/*
 *      EventImpl - Event implementation generator written on top of CodeAPI
 *
 *         The MIT License (MIT)
 *
 *      Copyright (c) 2017 ProjectSandstone <https://github.com/ProjectSandstone/EventImpl>
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
import com.github.jonathanxd.iutils.type.TypeUtil
import com.github.projectsandstone.eventsys.event.Event
import com.github.projectsandstone.eventsys.event.EventListener
import com.github.projectsandstone.eventsys.event.EventManager
import com.github.projectsandstone.eventsys.event.ListenerSpec
import com.github.projectsandstone.eventsys.event.annotation.Listener
import com.github.projectsandstone.eventsys.gen.event.CommonEventGenerator
import com.github.projectsandstone.eventsys.gen.event.EventGenerator
import com.github.projectsandstone.eventsys.logging.Level
import com.github.projectsandstone.eventsys.logging.LoggerInterface
import com.github.projectsandstone.eventsys.logging.MessageType
import com.github.projectsandstone.eventsys.util.getEventType
import com.github.projectsandstone.eventsys.util.mh.MethodDispatcher
import java.lang.reflect.Method
import java.util.Comparator
import java.util.TreeSet
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory

/**
 * Common Event Manager implementation
 *
 * @param generateDispatchClass True to generate classes that delegates directly to the listener method (faster),
 * false to use Java 7 MethodHandles to delegate to listener methods (slower) (see [MethodDispatcher]).
 * @param sorter Sort event listeners.
 * @param threadFactory Thread factory for async dispatch
 * @param logger Logger interface for error logging.
 * @param eventGenerator Event generator instance to generate listener methods.
 */
open class CommonEventManager @JvmOverloads constructor(
        val generateDispatchClass: Boolean = true,
        sorter: Comparator<EventListener<*>>,
        threadFactory: ThreadFactory,
        val logger: LoggerInterface,
        override val eventGenerator: EventGenerator) : EventManager {

    private val listeners: MutableSet<EventListenerContainer<*>> = TreeSet(Comparator { o1, o2 ->
        sorter.compare(o1.eventListener, o2.eventListener)
    })

    private val executor = Executors.newCachedThreadPool(threadFactory)

    override fun <T : Event> dispatch(event: T, owner: Any, phase: Int) {
        this.dispatch_(event, owner, phase, isAsync = false)
    }

    protected fun <T : Event> dispatch_(event: T, owner: Any, phase: Int, isAsync: Boolean) {

        val eventType = getEventType(event)

        fun <T : Event> tryDispatch(eventListenerContainer: EventListenerContainer<*>,
                                    event: T,
                                    owner: Any,
                                    phase: Int) {

            if (isAsync) {
                executor.execute {
                    dispatchDirect(eventListenerContainer, event, eventType, owner, phase)
                }
            } else {
                dispatchDirect(eventListenerContainer, event, eventType, owner, phase)
            }
        }

        listeners.filter {
            this.check(container = it, eventType = eventType, phase = phase)
        }.forEach {
            tryDispatch(it, event, owner, phase)
        }

    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun <T : Event> dispatchDirect(eventListenerContainer: EventListenerContainer<*>,
                                                  event: T,
                                                  eventType: TypeInfo<*>,
                                                  owner: Any,
                                                  phase: Int) {
        try {
            eventListenerContainer.eventListener.helpOnEvent(event, owner)
        } catch (throwable: Throwable) {
            logger.log("Cannot dispatch event $event (type: $eventType) to listener " +
                    "${eventListenerContainer.eventListener} (of event type: ${eventListenerContainer.eventType}) of owner " +
                    "$owner. " +
                    "(Source: $owner, phase: $phase)",
                    MessageType.EXCEPTION_IN_LISTENER,
                    throwable)

        }
    }

    private fun check(container: EventListenerContainer<*>, eventType: TypeInfo<*>, phase: Int): Boolean {

        fun checkType(): Boolean {
            return container.eventType.isAssignableFrom(eventType)
                    ||
                    (container.eventType.related.isEmpty()
                            && container.eventType.typeClass.isAssignableFrom(eventType.typeClass))
        }

        val listenerPhase = container.eventListener.phase

        return checkType() && (listenerPhase < 0 || phase < 0 || listenerPhase == phase)
    }

    override fun <T : Event> dispatchAsync(event: T, owner: Any, phase: Int) {
        this.dispatch_(event, owner, phase, isAsync = true)
    }

    override fun getListeners(): Set<Pair<TypeInfo<*>, EventListener<*>>> {
        return this.listeners
                .map { Pair(it.eventType, it.eventListener) }
                .toSet()
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : Event> getListeners(eventType: TypeInfo<T>): Set<Pair<TypeInfo<T>, EventListener<T>>> {
        return this.listeners
                .filter { eventType.isAssignableFrom(it.eventType) }
                .map { Pair(it.eventType, it.eventListener) }
                .toSet() as Set<Pair<TypeInfo<T>, EventListener<T>>>
    }

    override fun <T : Event> registerListener(owner: Any, eventType: TypeInfo<T>, eventListener: EventListener<T>) {
        val find = this.findListener(owner, eventType, eventListener)

        if (find == null) {
            this.listeners.add(EventListenerContainer(owner, eventType, eventListener))
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Event> registerGenericListener(owner: Any, eventType: TypeInfo<*>, eventListener: EventListener<*>) {
        this.registerListener(owner, eventType as TypeInfo<T>, eventListener as EventListener<T>)
    }

    override fun registerListeners(owner: Any, listener: Any) {
        this.createMethodListeners(owner, listener).forEach {
            this.registerGenericListener<Event>(owner, it.eventType, it.eventListener)
        }
    }

    override fun registerMethodListener(owner: Any, instance: Any?, method: Method) {
        if (instance != null && owner == instance) {

        } else {
            this.createMethodListener(
                    owner = owner,
                    instance = instance,
                    method = method).let {
                this.registerGenericListener<Event>(owner, it.eventType, it.eventListener)
            }
        }
    }

    internal fun <T : Event> findListener(owner: Any, eventType: TypeInfo<T>, eventListener: EventListener<T>) =
            this.listeners.find { it.owner == owner && it.eventType.compareTo(eventType) == 0 && it.eventListener == eventListener }

    @Suppress("UNCHECKED_CAST")
    private fun createMethodListener(owner: Any,
                                     instance: Any?,
                                     method: Method): EventListenerContainer<*> {


        return EventListenerContainer(owner,
                TypeUtil.toTypeInfo(method.genericParameterTypes[0]) as TypeInfo<Event>,
                this.eventGenerator.createMethodListener(owner, method, instance, ListenerSpec.fromMethod(method)))
    }


    private fun createMethodListeners(owner: Any,
                                      instance: Any): List<EventListenerContainer<*>> {

        return instance::class.java.declaredMethods.filter {
            it.getDeclaredAnnotation(Listener::class.java) != null
                    && it.parameterCount > 0
                    && Event::class.java.isAssignableFrom(it.parameterTypes[0])
        }.map {
            if (this.generateDispatchClass) {
                return@map this.createMethodListener(
                        owner = owner,
                        method = it,
                        instance = instance)
            } else {
                val data = ListenerSpec.fromMethod(it)

                @Suppress("UNCHECKED_CAST")
                return@map EventListenerContainer(
                        owner = owner,
                        eventType = data.eventType as TypeInfo<Event>,
                        eventListener = MethodDispatcher(data, it, instance))
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Event> EventListener<T>.helpOnEvent(event: Any, owner: Any) {
        this.onEvent(event as T, owner)
    }

}

class DefaultEventManager : CommonEventManager(true, COMMON_SORTER, COMMON_THREAD_FACTORY, COMMON_LOGGER, COMMON_EVENT_GENERATOR) {

    companion object {
        private val COMMON_SORTER = Comparator.comparing(EventListener<*>::priority)
        private val COMMON_THREAD_FACTORY = Executors.defaultThreadFactory()
        private val COMMON_LOGGER = CommonLogger()
        private val COMMON_EVENT_GENERATOR = CommonEventGenerator(COMMON_LOGGER)
    }
}

class CommonLogger : LoggerInterface {
    override fun log(messages: List<String>, messageType: MessageType) {
        log(messages.joinToString("\n"), messageType)
    }

    override fun log(messages: List<String>, messageType: MessageType, throwable: Throwable) {
        log(messages.joinToString("\n"), messageType, throwable)
    }

    override fun log(message: String, messageType: MessageType) {
        if (messageType.level == Level.FATAL) {
            throw IllegalStateException("Fatal error occurred: $message")
        } else {
            System.err.println(message)
        }
    }

    override fun log(message: String, messageType: MessageType, throwable: Throwable) {
        System.err.println(message)

        if (messageType.level == Level.FATAL) {
            throw throwable
        } else {
            throwable.printStackTrace()
        }
    }
}
