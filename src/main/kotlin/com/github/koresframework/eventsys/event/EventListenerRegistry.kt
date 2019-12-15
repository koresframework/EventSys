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
import com.github.koresframework.eventsys.impl.EventListenerContainer
import java.lang.reflect.Method
import java.lang.reflect.Type

interface EventListenerRegistry {
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
     * @return [ListenerRegistryResult] containing data about listener registration.
     */
    fun <T : Event> registerListener(owner: Any, eventType: Type, eventListener: EventListener<T>): ListenerRegistryResults

    /**
     * Register all method event listeners inside the [listener] instance.
     *
     * Listener methods must be annotated with [Listener] annotation.
     *
     * @param owner Owner of the [listener].
     * @param listener Listener instance to be used to create a [MethodEventListener].
     * @return [ListenerRegistryResults] containing data about registration of all listeners.
     */
    fun registerListeners(owner: Any, listener: Any): ListenerRegistryResults

    /**
     * Register [method] as [EventListener]. This method must be annotated with [Listener] annotation.
     *
     * @param owner Owner of the [method]
     * @param instance Instance used to invoke method.
     * @param method Method to register
     * @return [ListenerRegistryResults] containing data about method listener registration.
     */
    fun registerMethodListener(owner: Any,
                               eventClass: Type,
                               instance: Any?,
                               method: Method): ListenerRegistryResults

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
    fun getListenersAsPair(): Set<Pair<Type, EventListener<*>>>

    /**
     * Gets listeners of a specific event.
     *
     * @param eventType Type of event.
     * @return Listeners of event ([eventType])
     */
    @Deprecated(message = "This method is deprecated since 1.8.", replaceWith = ReplaceWith("getListenersContainers(event, eventType, channel)"))
    fun <T : Event> getListenersContainers(eventType: Type): Set<EventListenerContainer<*>>

    /**
     * Gets listeners of a specific event for specific [channel].
     *
     * @param event Event.
     * @param eventType Type of event.
     * @param channel Event channel.
     * @return Listeners of event ([eventType] and [channel]).
     */
    fun <T : Event> getListenersContainers(event: T, eventType: Type, channel: String): Iterable<EventListenerContainer<*>>

    /**
     * Gets all containers of listeners (immutable).
     */
    fun getListenersContainers(): Set<EventListenerContainer<*>>
}