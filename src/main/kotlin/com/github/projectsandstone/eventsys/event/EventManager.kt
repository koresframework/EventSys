/*
 *      EventSys - Event implementation generator written on top of CodeAPI
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
package com.github.projectsandstone.eventsys.event

import com.github.jonathanxd.iutils.type.AbstractTypeInfo
import com.github.jonathanxd.iutils.type.TypeInfo
import com.github.projectsandstone.eventsys.event.annotation.Listener
import com.github.projectsandstone.eventsys.gen.event.EventGenerator
import java.lang.reflect.Method

/**
 * Event manager.
 *
 * [EventManager] register event listeners and dispatches events to listeners.
 * Events can be dispatched in from dispatch phase, the phase -1 is the default phase.
 * If a negative phase is provided, the dispatcher will dispatch event to all listeners. Listeners
 * that listen to negative phases will always handle all phases.
 */
interface EventManager {

    /**
     * Event generator.
     */
    val eventGenerator: EventGenerator

    /**
     * Register a [EventListener] for a [Event].
     *
     * If you wan't to register an instance as [EventListener] use [registerListeners].
     *
     * @param owner Owner of the [eventListener]
     * @param eventType Type of event
     * @param eventListener Listener of the event.
     * @see [registerListeners]
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Event> registerListener(owner: Any, eventType: Class<T>, eventListener: EventListener<T>) {
        this.registerListener(owner, TypeInfo.of(eventType), eventListener)
    }

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
    fun <T : Event> registerListener(owner: Any, eventType: TypeInfo<T>, eventListener: EventListener<T>)

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
                               instance: Any?,
                               method: Method)

    /**
     * Dispatch an [Event] to all [EventListener]s that listen to the [event] and specified [listening phase][phase].
     *
     * @param event [Event] to dispatch do listeners.
     * @param owner Owner of the [event].
     * @param phase Phase of the dispatch, only listeners that listen to this phase will be called.
     */
    fun <T : Event> dispatch(event: T, owner: Any, phase: Int)

    /**
     * Dispatch an [Event] to all [EventListener]s that listen to the [event] (ignore phases).
     *
     * All listeners will be called (no matter the phase it listen).
     *
     * @param event [Event] to dispatch do listeners.
     * @param owner Instance of the [event].
     */
    fun <T : Event> dispatch(event: T, owner: Any) {
        this.dispatch(event, owner, -1)
    }

    /**
     * Dispatch an [Event] to all [EventListener]s that listen to the [event] and specified [listening phase][phase].
     *
     * This dispatch also includes [generic type information][typeInfo], normally EventSys infer the type
     * from generated event class, but if inference fails, or the class does not have generic information,
     * you need to use this method to dispatch events.
     *
     * @param event [Event] to dispatch do listeners.
     * @param typeInfo Information of generic event type.
     * @param owner Owner of the [event].
     * @param phase Phase of the dispatch, only listeners that listen to this phase will be called.
     */
    fun <T : Event> dispatch(event: T, typeInfo: TypeInfo<T>, owner: Any, phase: Int)

    /**
     * Dispatch an [Event] to all [EventListener]s that listen to the [event] (ignore phases).
     *
     * This dispatch also includes [generic type information][typeInfo], normally EventSys infer the type
     * from generated event class, but if inference fails, or the class does not have generic information,
     * you need to use this method to dispatch events.
     *
     * All listeners will be called (no matter the phase it listen).
     *
     * @param event [Event] to dispatch do listeners.
     * @param typeInfo Information of generic event type.
     * @param owner Instance of the [event].
     */
    fun <T : Event> dispatch(event: T, typeInfo: TypeInfo<T>, owner: Any) {
        this.dispatch(event, typeInfo, owner, -1)
    }

    //////////// Async

    /**
     * Dispatch an [Event] to all [EventListener]s that listen to the [event] and specified [listening phase][phase].
     *
     * Asynchronous dispatch.
     *
     * @param event [Event] to dispatch do listeners.
     * @param owner Owner of the [event].
     * @param phase Phase of the dispatch, only listeners that listen to this phase will be called.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Event> dispatchAsync(event: T, owner: Any, phase: Int)

    /**
     * Dispatch an [Event] to all [EventListener]s that listen to the [event].
     *
     * Asynchronous dispatch
     *
     * All listeners will be called (no matter the phase it listen).
     *
     * @param event [Event] to dispatch do listeners.
     * @param owner Owner of the [event].
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Event> dispatchAsync(event: T, owner: Any) {
        this.dispatchAsync(event, owner, -1)
    }

    /**
     * Dispatch an [Event] to all [EventListener]s that listen to the [event] and specified [listening phase][phase].
     *
     * This dispatch also includes [generic type information][typeInfo], normally EventSys infer the type
     * from generated event class, but if inference fails, or the class does not have generic information,
     * you need to use this method to dispatch events.
     *
     * Asynchronous dispatch.
     *
     * @param event [Event] to dispatch do listeners.
     * @param typeInfo Information of generic event type.
     * @param owner Owner of the [event].
     * @param phase Phase of the dispatch, only listeners that listen to this phase will be called.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Event> dispatchAsync(event: T, typeInfo: TypeInfo<T>, owner: Any, phase: Int)

    /**
     * Dispatch an [Event] to all [EventListener]s that listen to the [event].
     *
     * This dispatch also includes [generic type information][typeInfo], normally EventSys infer the type
     * from generated event class, but if inference fails, or the class does not have generic information,
     * you need to use this method to dispatch events.
     *
     * Asynchronous dispatch
     *
     * All listeners will be called (no matter the phase it listen).
     *
     * @param event [Event] to dispatch do listeners.
     * @param typeInfo Information of generic event type.
     * @param owner Owner of the [event].
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Event> dispatchAsync(event: T, typeInfo: TypeInfo<T>, owner: Any) {
        this.dispatchAsync(event, typeInfo, owner, -1)
    }

    //////////// /Async

    /**
     * Gets listeners of a specific event.
     *
     * @param eventType Type of event.
     * @return Listeners of event ([eventType])
     */
    fun <T : Event> getListeners(eventType: TypeInfo<T>): Set<Pair<TypeInfo<T>, EventListener<T>>>

    /**
     * Gets all listeners of events
     */
    fun getListeners(): Set<Pair<TypeInfo<*>, EventListener<*>>>
}

/**
 * Register the listener to [Event] [T].
 *
 * @param T Event type
 * @param plugin Plugin instance
 * @param eventListener Event Listener instance.
 */
inline fun <reified T : Event> EventManager.registerListener(plugin: Any, eventListener: EventListener<T>) {
    val typeInfo: TypeInfo<T> = object : AbstractTypeInfo<T>() {}

    this.registerListener(plugin, typeInfo, eventListener)
}