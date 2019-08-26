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
package com.github.koresframework.eventsys.event

import com.github.koresframework.eventsys.event.annotation.Listener
import com.github.koresframework.eventsys.gen.event.EventGenerator
import com.github.koresframework.eventsys.impl.EventListenerContainer
import com.github.koresframework.eventsys.util.getEventType
import java.lang.reflect.Method
import java.lang.reflect.Type

/**
 * Event manager.
 *
 * [EventManager] register event listeners and dispatches events to listeners.
 * Events can be dispatched in different channels, the channel -1 is the default channel.
 * If a negative channel is provided, the dispatcher will dispatch event to all listeners. Listeners
 * that listen to negative channels will always listen to all channels.
 */
interface EventManager {

    /**
     * Event generator.
     */
    val eventGenerator: EventGenerator

    /**
     * Event dispatcher
     */
    val eventDispatcher: EventDispatcher

    /**
     * Register a [EventListener] for a [Event].
     *
     * To register class instance listeners use [registerListeners], to register a specific method use
     * [registerMethodListener].
     *
     * @param owner Owner of the [eventListener]
     * @param eventType Type of event
     * @param eventListener Listener of the event.
     * @see [registerListeners]
     */
    fun <T : Event> registerListener(owner: Any, eventType: Type, eventListener: EventListener<T>)

    /**
     * Register all method event listeners inside the [listener] instance.
     *
     * Listener methods must be annotated with [Listener] annotation.
     *
     * @param owner Owner of the [listener].
     * @param listener Listener instance to be used to create a [MethodEventListener].
     */
    fun registerListeners(owner: Any, listener: Any)

    /**
     * Register [method] as [EventListener]. This method must be annotated with [Listener] annotation.
     *
     * @param owner Owner of the [method]
     * @param instance Instance used to invoke method.
     * @param method Method to register
     */
    fun registerMethodListener(owner: Any,
                               eventClass: Type,
                               instance: Any?,
                               method: Method)

    /**
     * Dispatch an [Event] to all [EventListener]s that listen to the [event] in [channel].
     *
     * @param event [Event] to dispatch do listeners.
     * @param dispatcher Dispatcher of the [event].
     * @param channel Channel of listeners to receive event.
     */
    fun <T : Event> dispatch(event: T, dispatcher: Any, channel: String) =
        this.dispatch(event, getEventType(event), dispatcher, channel)

    /**
     * Dispatch an [Event] to all [EventListener]s that listen to the [event] (all channels).
     *
     * All listeners will be called (no matter the channel it listen).
     *
     * @param event [Event] to dispatch do listeners.
     * @param dispatcher Dispatcher of the [event].
     */
    fun <T : Event> dispatch(event: T, dispatcher: Any) =
        this.dispatch(event, dispatcher, "@all")


    /**
     * Dispatch an [Event] to all [EventListener]s that listen to the [event] in [channel].
     *
     * This dispatch also includes [generic type information][type], normally EventSys infer the type
     * from generated event class, but if inference fails, or the class does not have generic information,
     * you need to use this method to dispatch events.
     *
     * @param event [Event] to dispatch do listeners.
     * @param type Information of generic event type.
     * @param dispatcher Dispatcher of the [event].
     * @param channel Channel of listeners to receive event.
     */
    fun <T : Event> dispatch(event: T, type: Type, dispatcher: Any, channel: String) =
        this.eventDispatcher.dispatch(event, type, dispatcher, channel, false)

    /**
     * Dispatch an [Event] to all [EventListener]s that listen to the [event] (all channels).
     *
     * This dispatch also includes [generic type information][type], normally EventSys infer the type
     * from generated event class, but if inference fails, or the class does not have generic information,
     * you need to use this method to dispatch events.
     *
     * All listeners will be called (no matter the channel it listen).
     *
     * @param event [Event] to dispatch do listeners.
     * @param type Information of generic event type.
     * @param dispatcher Dispatcher of the [event].
     */
    fun <T : Event> dispatch(event: T, type: Type, dispatcher: Any) {
        this.dispatch(event, type, dispatcher, "@all")
    }

    //////////// Async

    /**
     * Dispatch an [Event] to all [EventListener]s that listen to the [event] in [channel].
     *
     * Non blocking asynchronous dispatch.
     *
     * @param event [Event] to dispatch do listeners.
     * @param dispatcher Dispatcher of the [event].
     * @param channel Channel to dispatch event (`-1` = all).
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Event> dispatchAsync(event: T, dispatcher: Any, channel: String) {
        this.dispatchAsync(event, getEventType(event), dispatcher, channel)
    }

    /**
     * Dispatch an [Event] to all [EventListener]s that listen to the [event] (all channels).
     *
     * Non blocking asynchronous dispatch
     *
     * All listeners will be called (no matter the channel it listen).
     *
     * @param event [Event] to dispatch do listeners.
     * @param dispatcher Dispatcher of the [event].
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Event> dispatchAsync(event: T, dispatcher: Any) =
        this.dispatchAsync(event, dispatcher, "@all")


    /**
     * Dispatch an [Event] to all [EventListener]s that listen to the [event] in [channel].
     *
     * This dispatch also includes [generic type information][type], normally EventSys infer the type
     * from generated event class, but if inference fails, or the class does not have generic information,
     * you need to use this method to dispatch events.
     *
     * Non blocking asynchronous dispatch.
     *
     * @param event [Event] to dispatch do listeners.
     * @param type Information of generic event type.
     * @param dispatcher Dispatcher of the [event].
     * @param channel Channel to dispatch event.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Event> dispatchAsync(event: T, type: Type, dispatcher: Any, channel: String) =
        this.eventDispatcher.dispatch(event, type, dispatcher, channel, false)

    /**
     * Dispatch an [Event] to all [EventListener]s that listen to the [event].
     *
     * This dispatch also includes [generic type information][type], normally EventSys infer the type
     * from generated event class, but if inference fails, or the class does not have generic information,
     * you need to use this method to dispatch events.
     *
     * Non blocking asynchronous dispatch.
     *
     * All listeners will be called (no matter the channel it listen).
     *
     * @param event [Event] to dispatch do listeners.
     * @param type Information of generic event type.
     * @param dispatcher Dispatcher of the [event].
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Event> dispatchAsync(event: T, type: Type, dispatcher: Any) =
        this.dispatchAsync(event, type, dispatcher, "@all")


    //////////// /Async

    /**
     * Gets listeners of a specific event.
     *
     * @param eventType Type of event.
     * @return Listeners of event ([eventType])
     */
    fun <T : Event> getListeners(eventType: Type): Set<Pair<Type, EventListener<T>>>

    /**
     * Gets all listeners of events
     */
    fun getListeners(): Set<Pair<Type, EventListener<*>>>

    /**
     * Gets all containers of listeners (immutable).
     */
    fun getListenersContainers(): Set<EventListenerContainer<*>>
}

/**
 * Dispatcher of events. Dispatcher is the class which implements the dispatch logic,
 * listeners should be registered through [EventManager].
 */
interface EventDispatcher {

    /**
     * Dispatch [event] to all listeners which listen to [event] in [channel] (negative channel for
     * all listeners), if [isAsync] is true, each listener may be called on different threads,
     * the behavior depends on implementation, but the dispatch will never block current thread.
     */
    fun <T: Event> dispatch(event: T, eventType: Type, dispatcher: Any, channel: String, isAsync: Boolean)
}
