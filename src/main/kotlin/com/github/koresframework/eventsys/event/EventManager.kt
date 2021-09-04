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
package com.github.koresframework.eventsys.event

import com.github.koresframework.eventsys.channel.ChannelSet
import com.github.koresframework.eventsys.context.EnvironmentContext
import com.github.koresframework.eventsys.gen.event.EventGenerator
import com.github.koresframework.eventsys.result.DispatchResult
import com.github.koresframework.eventsys.util.getEventType
import kotlinx.coroutines.runBlocking
import java.lang.reflect.Type

/**
 * Registers and dispatches events to listeners.
 *
 * # Dispatch strategy
 *
 * ## Sync
 *
 * This is the default dispatch strategy for single-thread applications,
 * listeners are called one-by-one sequentially, respecting the
 * [listener priority][EventListener.priority].
 *
 * ## Async
 *
 * This is the default strategy for **Atevist** and multi-thread applications,
 * listeners are called concurrently, and the [listener priority][EventListener.priority] is not totally ignored,
 * but can't be guaranteed to be respected.
 *
 * ## Sync/Async Blocking
 *
 * This is a variant which allows Java-code to interact with `suspend` dispatch, this is not recommended
 * for Kotlin code to use the `*Blocking` functions, instead, `suspend` variant must be used.
 *
 * The `*Blocking` strategy blocks current thread until all listeners have completed the handling logic.
 */
interface EventManager {

    /**
     * Event dispatcher
     */
    val eventDispatcher: EventDispatcher

    /**
     * Dispatch an [Event] to all [EventListener]s that listen to the [event] in [channel].
     *
     * Not recommended being used from kotlin code, use `suspend` variant instead.
     *
     * @param event [Event] to dispatch do listeners.
     * @param dispatcher Dispatcher of the [event].
     * @param channel Channel of listeners to receive event.
     */
    fun <T : Event> dispatchBlocking(event: T, dispatcher: Any, channel: String) =
        runBlocking {
            dispatch(event, getEventType(event), dispatcher, channel, EnvironmentContext())
        }

    /**
     * Dispatch an [Event] to all [EventListener]s that listen to the [event] in [channel].
     *
     * @param event [Event] to dispatch do listeners.
     * @param dispatcher Dispatcher of the [event].
     * @param channel Channel of listeners to receive event.
     */
    suspend fun <T : Event> dispatch(event: T, dispatcher: Any, channel: String) =
            this.dispatch(event, getEventType(event), dispatcher, channel, EnvironmentContext())

    /**
     * Dispatch an [Event] to all [EventListener]s that listen to the [event] in [channel].
     *
     * @param event [Event] to dispatch do listeners.
     * @param dispatcher Dispatcher of the [event].
     * @param channel Channel of listeners to receive event.
     */
    fun <T : Event> dispatchBlocking(event: T, dispatcher: Any, channel: String, ctx: EnvironmentContext) =
        runBlocking {
            dispatch(event, getEventType(event), dispatcher, channel, ctx)
        }

    /**
     * Dispatch an [Event] to all [EventListener]s that listen to the [event] in [channel].
     *
     * @param event [Event] to dispatch do listeners.
     * @param dispatcher Dispatcher of the [event].
     * @param channel Channel of listeners to receive event.
     */
    suspend fun <T : Event> dispatch(event: T, dispatcher: Any, channel: String, ctx: EnvironmentContext) =
            this.dispatch(event, getEventType(event), dispatcher, channel, ctx)

    /**
     * Dispatch an [Event] to all [EventListener]s that listen to the [event] (all channels).
     *
     * All listeners will be called (no matter the channel it listen).
     *
     * @param event [Event] to dispatch do listeners.
     * @param dispatcher Dispatcher of the [event].
     */
    fun <T : Event> dispatchBlocking(event: T, dispatcher: Any) =
        runBlocking {
            dispatch(event, dispatcher, ChannelSet.Expression.ALL, EnvironmentContext())
        }

    /**
     * Dispatch an [Event] to all [EventListener]s that listen to the [event] (all channels).
     *
     * All listeners will be called (no matter the channel it listen).
     *
     * @param event [Event] to dispatch do listeners.
     * @param dispatcher Dispatcher of the [event].
     */
    suspend fun <T : Event> dispatch(event: T, dispatcher: Any) =
            this.dispatch(event, dispatcher, ChannelSet.Expression.ALL, EnvironmentContext())

    /**
     * Dispatch an [Event] to all [EventListener]s that listen to the [event] (all channels).
     *
     * All listeners will be called (no matter the channel it listen).
     *
     * @param event [Event] to dispatch do listeners.
     * @param dispatcher Dispatcher of the [event].
     */
    fun <T : Event> dispatchBlocking(event: T, dispatcher: Any, ctx: EnvironmentContext) =
        runBlocking {
            dispatch(event, dispatcher, ChannelSet.Expression.ALL, ctx)
        }

    /**
     * Dispatch an [Event] to all [EventListener]s that listen to the [event] (all channels).
     *
     * All listeners will be called (no matter the channel it listen).
     *
     * @param event [Event] to dispatch do listeners.
     * @param dispatcher Dispatcher of the [event].
     */
    suspend fun <T : Event> dispatch(event: T, dispatcher: Any, ctx: EnvironmentContext) =
        this.dispatch(event, dispatcher, ChannelSet.Expression.ALL, ctx)

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
    fun <T : Event> dispatchBlocking(event: T, type: Type, dispatcher: Any, channel: String) =
        runBlocking {
            dispatch(event, type, dispatcher, channel, EnvironmentContext())
        }

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
    suspend fun <T : Event> dispatch(event: T, type: Type, dispatcher: Any, channel: String) =
            this.dispatch(event, type, dispatcher, channel, EnvironmentContext())

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
    fun <T : Event> dispatchBlocking(event: T, type: Type, dispatcher: Any, channel: String, ctx: EnvironmentContext) =
        runBlocking {
            eventDispatcher.dispatch(event, type, dispatcher, channel, false, ctx)
        }

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
    suspend fun <T : Event> dispatch(event: T, type: Type, dispatcher: Any, channel: String, ctx: EnvironmentContext) =
            this.eventDispatcher.dispatch(event, type, dispatcher, channel, false, ctx)

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
    fun <T : Event> dispatchBlocking(event: T, type: Type, dispatcher: Any) =
        runBlocking {
            dispatch(event, type, dispatcher, ChannelSet.Expression.ALL, EnvironmentContext())
        }

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
    suspend fun <T : Event> dispatch(event: T, type: Type, dispatcher: Any) =
            this.dispatch(event, type, dispatcher, ChannelSet.Expression.ALL, EnvironmentContext())

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
    fun <T : Event> dispatchBlocking(event: T, type: Type, dispatcher: Any, ctx: EnvironmentContext) =
        runBlocking {
            dispatch(event, type, dispatcher, ChannelSet.Expression.ALL, ctx)
        }

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
    suspend fun <T : Event> dispatch(event: T, type: Type, dispatcher: Any, ctx: EnvironmentContext) =
            this.dispatch(event, type, dispatcher, ChannelSet.Expression.ALL, ctx)


    //////////// Async

    /**
     * Dispatch an [Event] to all [EventListener]s that listen to the [event] in [channel].
     *
     * Non-blocking asynchronous dispatch.
     *
     * @param event [Event] to dispatch do listeners.
     * @param dispatcher Dispatcher of the [event].
     * @param channel Channel to dispatch event (`-1` = all).
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Event> dispatchAsyncBlocking(event: T, dispatcher: Any, channel: String) =
        runBlocking { dispatchAsync(event, getEventType(event), dispatcher, channel, EnvironmentContext()) }


    /**
     * Dispatch an [Event] to all [EventListener]s that listen to the [event] in [channel].
     *
     * Non-blocking asynchronous dispatch.
     *
     * @param event [Event] to dispatch do listeners.
     * @param dispatcher Dispatcher of the [event].
     * @param channel Channel to dispatch event (`-1` = all).
     */
    @Suppress("UNCHECKED_CAST")
    suspend fun <T : Event> dispatchAsync(event: T, dispatcher: Any, channel: String) =
            this.dispatchAsync(event, getEventType(event), dispatcher, channel, EnvironmentContext())

    /**
     * Dispatch an [Event] to all [EventListener]s that listen to the [event] in [channel].
     *
     * Non-blocking asynchronous dispatch.
     *
     * @param event [Event] to dispatch do listeners.
     * @param dispatcher Dispatcher of the [event].
     * @param channel Channel to dispatch event (`-1` = all).
     * @param ctx Context.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Event> dispatchAsyncBlocking(event: T, dispatcher: Any, channel: String, ctx: EnvironmentContext) =
            runBlocking { dispatchAsync(event, getEventType(event), dispatcher, channel, ctx) }

    /**
     * Dispatch an [Event] to all [EventListener]s that listen to the [event] in [channel].
     *
     * Non-blocking asynchronous dispatch.
     *
     * @param event [Event] to dispatch do listeners.
     * @param dispatcher Dispatcher of the [event].
     * @param channel Channel to dispatch event (`-1` = all).
     * @param ctx Context.
     */
    @Suppress("UNCHECKED_CAST")
    suspend fun <T : Event> dispatchAsync(event: T, dispatcher: Any, channel: String, ctx: EnvironmentContext) =
            this.dispatchAsync(event, getEventType(event), dispatcher, channel, ctx)

    /**
     * Dispatch an [Event] to all [EventListener]s that listen to the [event] (all channels).
     *
     * Non-blocking asynchronous dispatch
     *
     * All listeners will be called (no matter the channel it listen).
     *
     * @param event [Event] to dispatch do listeners.
     * @param dispatcher Dispatcher of the [event].
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Event> dispatchAsyncBlocking(event: T, dispatcher: Any) =
            runBlocking { dispatchAsync(event, dispatcher, ChannelSet.Expression.ALL, EnvironmentContext()) }

    /**
     * Dispatch an [Event] to all [EventListener]s that listen to the [event] (all channels).
     *
     * Non-blocking asynchronous dispatch
     *
     * All listeners will be called (no matter the channel it listen).
     *
     * @param event [Event] to dispatch do listeners.
     * @param dispatcher Dispatcher of the [event].
     */
    @Suppress("UNCHECKED_CAST")
    suspend fun <T : Event> dispatchAsync(event: T, dispatcher: Any) =
            this.dispatchAsync(event, dispatcher, ChannelSet.Expression.ALL, EnvironmentContext())

    /**
     * Dispatch an [Event] to all [EventListener]s that listen to the [event] (all channels).
     *
     * Non-blocking asynchronous dispatch
     *
     * All listeners will be called (no matter the channel it listen).
     *
     * @param event [Event] to dispatch do listeners.
     * @param dispatcher Dispatcher of the [event].
     * @param ctx Context.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Event> dispatchAsyncBlocking(event: T, dispatcher: Any, ctx: EnvironmentContext) =
            runBlocking { dispatchAsync(event, dispatcher, ChannelSet.Expression.ALL, ctx) }


    /**
     * Dispatch an [Event] to all [EventListener]s that listen to the [event] (all channels).
     *
     * Non-blocking asynchronous dispatch
     *
     * All listeners will be called (no matter the channel it listen).
     *
     * @param event [Event] to dispatch do listeners.
     * @param dispatcher Dispatcher of the [event].
     * @param ctx Context.
     */
    @Suppress("UNCHECKED_CAST")
    suspend fun <T : Event> dispatchAsync(event: T, dispatcher: Any, ctx: EnvironmentContext) =
            this.dispatchAsync(event, dispatcher, ChannelSet.Expression.ALL, ctx)


    /**
     * Dispatch an [Event] to all [EventListener]s that listen to the [event] in [channel].
     *
     * This dispatch also includes [generic type information][type], normally EventSys infer the type
     * from generated event class, but if inference fails, or the class does not have generic information,
     * you need to use this method to dispatch events.
     *
     * Non-blocking asynchronous dispatch.
     *
     * @param event [Event] to dispatch do listeners.
     * @param type Information of generic event type.
     * @param dispatcher Dispatcher of the [event].
     * @param channel Channel to dispatch event.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Event> dispatchAsyncBlocking(event: T, type: Type, dispatcher: Any, channel: String) =
            runBlocking { dispatchAsync(event, type, dispatcher, channel, EnvironmentContext()) }

    /**
     * Dispatch an [Event] to all [EventListener]s that listen to the [event] in [channel].
     *
     * This dispatch also includes [generic type information][type], normally EventSys infer the type
     * from generated event class, but if inference fails, or the class does not have generic information,
     * you need to use this method to dispatch events.
     *
     * Non-blocking asynchronous dispatch.
     *
     * @param event [Event] to dispatch do listeners.
     * @param type Information of generic event type.
     * @param dispatcher Dispatcher of the [event].
     * @param channel Channel to dispatch event.
     */
    @Suppress("UNCHECKED_CAST")
    suspend fun <T : Event> dispatchAsync(event: T, type: Type, dispatcher: Any, channel: String) =
            this.dispatchAsync(event, type, dispatcher, channel, EnvironmentContext())

    /**
     * Dispatch an [Event] to all [EventListener]s that listen to the [event] in [channel].
     *
     * This dispatch also includes [generic type information][type], normally EventSys infer the type
     * from generated event class, but if inference fails, or the class does not have generic information,
     * you need to use this method to dispatch events.
     *
     * Non-blocking asynchronous dispatch.
     *
     * @param event [Event] to dispatch do listeners.
     * @param type Information of generic event type.
     * @param dispatcher Dispatcher of the [event].
     * @param channel Channel to dispatch event.
     * @param ctx Context.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Event> dispatchAsyncBlocking(event: T, type: Type, dispatcher: Any, channel: String, ctx: EnvironmentContext) =
            runBlocking { eventDispatcher.dispatch(event, type, dispatcher, channel, true, ctx) }

    /**
     * Dispatch an [Event] to all [EventListener]s that listen to the [event] in [channel].
     *
     * This dispatch also includes [generic type information][type], normally EventSys infer the type
     * from generated event class, but if inference fails, or the class does not have generic information,
     * you need to use this method to dispatch events.
     *
     * Non-blocking asynchronous dispatch.
     *
     * @param event [Event] to dispatch do listeners.
     * @param type Information of generic event type.
     * @param dispatcher Dispatcher of the [event].
     * @param channel Channel to dispatch event.
     * @param ctx Context.
     */
    @Suppress("UNCHECKED_CAST")
    suspend fun <T : Event> dispatchAsync(event: T, type: Type, dispatcher: Any, channel: String, ctx: EnvironmentContext) =
            this.eventDispatcher.dispatch(event, type, dispatcher, channel, true, ctx)

    /**
     * Dispatch an [Event] to all [EventListener]s that listen to the [event].
     *
     * This dispatch also includes [generic type information][type], normally EventSys infer the type
     * from generated event class, but if inference fails, or the class does not have generic information,
     * you need to use this method to dispatch events.
     *
     * Non-blocking asynchronous dispatch.
     *
     * All listeners will be called (no matter the channel it listen).
     *
     * @param event [Event] to dispatch do listeners.
     * @param type Information of generic event type.
     * @param dispatcher Dispatcher of the [event].
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Event> dispatchAsyncBlocking(event: T, type: Type, dispatcher: Any) =
            runBlocking { dispatchAsync(event, type, dispatcher, ChannelSet.Expression.ALL, EnvironmentContext()) }

    /**
     * Dispatch an [Event] to all [EventListener]s that listen to the [event].
     *
     * This dispatch also includes [generic type information][type], normally EventSys infer the type
     * from generated event class, but if inference fails, or the class does not have generic information,
     * you need to use this method to dispatch events.
     *
     * Non-blocking asynchronous dispatch.
     *
     * All listeners will be called (no matter the channel it listen).
     *
     * @param event [Event] to dispatch do listeners.
     * @param type Information of generic event type.
     * @param dispatcher Dispatcher of the [event].
     */
    @Suppress("UNCHECKED_CAST")
    suspend fun <T : Event> dispatchAsync(event: T, type: Type, dispatcher: Any) =
            this.dispatchAsync(event, type, dispatcher, ChannelSet.Expression.ALL, EnvironmentContext())

    /**
     * Dispatch an [Event] to all [EventListener]s that listen to the [event].
     *
     * This dispatch also includes [generic type information][type], normally EventSys infer the type
     * from generated event class, but if inference fails, or the class does not have generic information,
     * you need to use this method to dispatch events.
     *
     * Non-blocking asynchronous dispatch.
     *
     * All listeners will be called (no matter the channel it listen).
     *
     * @param event [Event] to dispatch do listeners.
     * @param type Information of generic event type.
     * @param dispatcher Dispatcher of the [event].
     * @param ctx Context.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Event> dispatchAsyncBlocking(event: T, type: Type, dispatcher: Any, ctx: EnvironmentContext) =
            runBlocking { dispatchAsync(event, type, dispatcher, ChannelSet.Expression.ALL, ctx) }


    /**
     * Dispatch an [Event] to all [EventListener]s that listen to the [event].
     *
     * This dispatch also includes [generic type information][type], normally EventSys infer the type
     * from generated event class, but if inference fails, or the class does not have generic information,
     * you need to use this method to dispatch events.
     *
     * Non-blocking asynchronous dispatch.
     *
     * All listeners will be called (no matter the channel it listen).
     *
     * @param event [Event] to dispatch do listeners.
     * @param type Information of generic event type.
     * @param dispatcher Dispatcher of the [event].
     * @param ctx Context.
     */
    @Suppress("UNCHECKED_CAST")
    suspend fun <T : Event> dispatchAsync(event: T, type: Type, dispatcher: Any, ctx: EnvironmentContext) =
            this.dispatchAsync(event, type, dispatcher, ChannelSet.Expression.ALL, ctx)


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
    suspend fun <T : Event> dispatch(
        event: T,
        eventType: Type,
        dispatcher: Any,
        channel: String,
        isAsync: Boolean,
        ctx: EnvironmentContext
    ): DispatchResult<T>
}

interface BlockingEventDispatcher : EventDispatcher {
    override suspend fun <T : Event> dispatch(
        event: T,
        eventType: Type,
        dispatcher: Any,
        channel: String,
        isAsync: Boolean,
        ctx: EnvironmentContext
    ): DispatchResult<T> = this.dispatchBlocking(event, eventType, dispatcher, channel, isAsync, ctx)

    fun <T : Event> dispatchBlocking(
        event: T,
        eventType: Type,
        dispatcher: Any,
        channel: String,
        isAsync: Boolean,
        ctx: EnvironmentContext
    ): DispatchResult<T>
}

fun <T : Event> EventDispatcher.dispatchBlocking(
    event: T,
    eventType: Type,
    dispatcher: Any,
    channel: String,
    isAsync: Boolean,
    ctx: EnvironmentContext
): DispatchResult<T> =
    runBlocking { this@dispatchBlocking.dispatch(event, eventType, dispatcher, channel, isAsync, ctx) }