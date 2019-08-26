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

import com.github.jonathanxd.kores.type.compareTo
import com.github.jonathanxd.kores.type.isAssignableFrom
import com.github.koresframework.eventsys.event.Event
import com.github.koresframework.eventsys.event.EventDispatcher
import com.github.koresframework.eventsys.event.EventListener
import com.github.koresframework.eventsys.event.EventManager
import com.github.koresframework.eventsys.event.annotation.Filter
import com.github.koresframework.eventsys.event.annotation.Listener
import com.github.koresframework.eventsys.gen.event.CommonEventGenerator
import com.github.koresframework.eventsys.gen.event.EventGenerator
import com.github.koresframework.eventsys.gen.event.EventGeneratorOptions
import com.github.koresframework.eventsys.logging.Level
import com.github.koresframework.eventsys.logging.LoggerInterface
import com.github.koresframework.eventsys.logging.MessageType
import com.github.koresframework.eventsys.util.hasEventFirstArg
import com.github.koresframework.eventsys.util.mh.MethodDispatcher
import java.lang.reflect.Method
import java.lang.reflect.Type
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory

/**
 * Common Event Manager implementation
 *
 * @param sorter Sort event listeners.
 * @param threadFactory Thread factory for async dispatch
 * @param logger Logger interface for error logging.
 * @param eventGenerator Event generator instance to generate listener methods.
 */
open class CommonEventManager(
    sorter: Comparator<EventListener<*>>,
    threadFactory: ThreadFactory,
    val logger: LoggerInterface,
    override val eventGenerator: EventGenerator
) : EventManager {

    override val eventDispatcher: EventDispatcher = CommonEventDispatcher(threadFactory, logger) {
        this.unmodListeners
    }

    private val listeners: MutableSet<EventListenerContainer<*>> = TreeSet(Comparator { o1, o2 ->
        val sort = sorter.compare(o1.eventListener, o2.eventListener)

        if (sort == 0)
            -1
        else sort
    })

    private val unmodListeners = Collections.unmodifiableSet(this.listeners)

    override fun getListenersContainers(): Set<EventListenerContainer<*>> = unmodListeners

    override fun getListeners(): Set<Pair<Type, EventListener<*>>> {
        return this.listeners
            .map { Pair(it.eventType, it.eventListener) }
            .toSet()
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : Event> getListeners(eventType: Type): Set<Pair<Type, EventListener<T>>> {
        return this.listeners
            .filter { eventType.isAssignableFrom(it.eventType) }
            .map { Pair(it.eventType, it.eventListener) }
            .toSet() as Set<Pair<Type, EventListener<T>>>
    }

    override fun <T : Event> registerListener(
        owner: Any,
        eventType: Type,
        eventListener: EventListener<T>
    ) {
        val find = this.findListener(owner, eventType, eventListener)

        if (find == null) {
            this.listeners.add(EventListenerContainer(owner, eventType, eventListener))
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Event> registerGenericListener(
        owner: Any,
        eventType: Type,
        eventListener: EventListener<*>
    ) {
        this.registerListener(owner, eventType, eventListener as EventListener<T>)
    }

    override fun registerListeners(owner: Any, listener: Any) {
        this.createMethodListeners(owner, listener).forEach {
            this.registerGenericListener<Event>(owner, it.eventType, it.eventListener)
        }
    }

    override fun registerMethodListener(
        owner: Any,
        eventClass: Type,
        instance: Any?,
        method: Method
    ) {
        this.createMethodListener(
            listenerClass = eventClass,
            owner = owner,
            instance = instance,
            method = method
        ).let {
            this.registerGenericListener<Event>(owner, it.eventType, it.eventListener)
        }
    }

    private fun <T : Event> findListener(
        owner: Any,
        eventType: Type,
        eventListener: EventListener<T>
    ) =
        this.listeners.find { it.owner == owner && it.eventType.compareTo(eventType) == 0 && it.eventListener == eventListener }

    @Suppress("UNCHECKED_CAST")
    private fun createMethodListener(
        owner: Any,
        listenerClass: Type,
        instance: Any?,
        method: Method
    ): EventListenerContainer<*> {
        return this.eventGenerator.createListenerSpecFromMethod(method).let { spec ->
            EventListenerContainer(
                owner,
                spec.eventType,
                this.eventGenerator.createMethodListener(
                    listenerClass,
                    method,
                    instance,
                    spec
                ).resolve()
            )
        }
    }


    private fun createMethodListeners(
        owner: Any,
        instance: Any
    ): List<EventListenerContainer<*>> {

        return instance::class.java.declaredMethods.filter {
            val reqArg = it.getDeclaredAnnotation(Filter::class.java).hasEventFirstArg()
            if (it.getDeclaredAnnotation(Listener::class.java) != null)
                if (reqArg)
                    it.parameterCount > 0
                            && Event::class.java.isAssignableFrom(it.parameterTypes[0])
                else true
            else false

        }.map {
            if (this.eventGenerator.options[EventGeneratorOptions.USE_METHOD_HANDLE_LISTENER]) {
                val data = this.eventGenerator.createListenerSpecFromMethod(it)

                @Suppress("UNCHECKED_CAST")
                return@map EventListenerContainer(
                    owner = owner,
                    eventType = data.eventType,
                    eventListener = MethodDispatcher(data, it, instance)
                )
            } else {
                return@map this.createMethodListener(
                    listenerClass = instance::class.java,
                    owner = owner,
                    method = it,
                    instance = instance
                )
            }
        }
    }

}

class DefaultEventManager : CommonEventManager(
    COMMON_SORTER,
    COMMON_THREAD_FACTORY,
    COMMON_LOGGER,
    COMMON_EVENT_GENERATOR
) {

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
