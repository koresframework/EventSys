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

import com.github.koresframework.eventsys.channel.ChannelSet
import com.github.koresframework.eventsys.gen.event.EventGenerator
import com.github.koresframework.eventsys.result.DispatchResult
import com.github.koresframework.eventsys.util.getEventType
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
     * Event dispatcher
     */
    val eventDispatcher: EventDispatcher

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
            this.dispatch(event, dispatcher, ChannelSet.Expression.ALL)


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
    fun <T : Event> dispatch(event: T, type: Type, dispatcher: Any) =
            this.dispatch(event, type, dispatcher, ChannelSet.Expression.ALL)

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
    fun <T : Event> dispatchAsync(event: T, dispatcher: Any, channel: String) =
            this.dispatchAsync(event, getEventType(event), dispatcher, channel)

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
            this.dispatchAsync(event, dispatcher, ChannelSet.Expression.ALL)


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
            this.eventDispatcher.dispatch(event, type, dispatcher, channel, true)

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
            this.dispatchAsync(event, type, dispatcher, ChannelSet.Expression.ALL)


    //////////// /Async

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
    fun <T : Event> dispatch(event: T, eventType: Type, dispatcher: Any, channel: String, isAsync: Boolean): DispatchResult<T>
}
