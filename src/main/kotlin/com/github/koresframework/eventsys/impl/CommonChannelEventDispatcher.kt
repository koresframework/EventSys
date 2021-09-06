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

import com.github.koresframework.eventsys.channel.ChannelSet
import com.github.koresframework.eventsys.channel.parseChannelSet
import com.github.koresframework.eventsys.context.EnvironmentContext
import com.github.koresframework.eventsys.event.ChannelEventDispatcher
import com.github.koresframework.eventsys.event.ChannelEventListenerRegistry
import com.github.koresframework.eventsys.event.Event
import com.github.koresframework.eventsys.event.EventDispatcher
import com.github.koresframework.eventsys.gen.event.EventGenerator
import com.github.koresframework.eventsys.logging.LoggerInterface
import com.github.koresframework.eventsys.result.DispatchResult
import java.lang.reflect.Type
import java.util.concurrent.Executor
import kotlin.coroutines.CoroutineContext

/**
 * Common implementation of [ChannelEventDispatcher] that only dispatches to channel
 * that the [registry][ChannelEventListenerRegistry] supports.
 */
class CommonChannelEventDispatcher(override val eventGenerator: EventGenerator,
                                   override val executor: Executor,
                                   override val logger: LoggerInterface,
                                   override val context: CoroutineContext,
                                   private val channelEventListenerRegistry: ChannelEventListenerRegistry) : AbstractEventDispatcher(), ChannelEventDispatcher {

    override val channels: ChannelSet
        get() = this.channelEventListenerRegistry.channels

    override fun <T : Event> getListeners(event: T, eventType: Type, channel: String): Iterable<EventListenerContainer<*>> {
        val expr = channel.parseChannelSet()
        val filteredChannels = expr.filterChannels(channels)

        return if (filteredChannels.isNotEmpty()) {
            this.channelEventListenerRegistry.getListenersContainers(event, eventType, channel)
        } else {
            emptyList()
        }
    }

}

class ChannelDispatcherDistributor(dispatchers: List<ChannelEventDispatcher>,
                                   val context: CoroutineContext,
                                   private val globalDispatcher: EventDispatcher) : EventDispatcher, ChannelEventDispatcher {

    private val registeredChannelDispatchers = dispatchers.toMutableList()
    private val registeredChannelDispatcherMap =
            dispatchers.flatMap { dispatcher ->
                dispatcher.channels.toSet().map { it to dispatcher }
            }.groupBy({ it.first }, { it.second })

    override val channels: ChannelSet
        get() = ChannelSet.Include(this.registeredChannelDispatchers.map { it.channels.toSet() }.flatten().toSet())


    override suspend fun <T : Event> dispatch(event: T,
                                      eventType: Type,
                                      dispatcher: Any,
                                      channel: String,
                                      isAsync: Boolean,
                                      ctx: EnvironmentContext): DispatchResult<T> {

        val dispatchers = when (val expr = channel.parseChannelSet()) {
            ChannelSet.ALL -> {
                this.registeredChannelDispatchers
            }
            ChannelSet.NONE -> {
                emptyList()
            }
            is ChannelSet.Include, is ChannelSet.Exclude -> {
                val includeChannels =
                    if (expr is ChannelSet.Include) expr.toSet()
                    else this.channels.toSet().filter { expr.contains(it) }

                includeChannels
                    .flatMap { registeredChannelDispatcherMap[it].orEmpty() }
            }
            else -> {
                emptyList()
            }
        }

        return dispatchers
            .map { it.dispatch(event, eventType, dispatcher, channel, isAsync, ctx) }
            .fold(DispatchResult(this.context, emptyList())) { acc, dispatchResult ->
                acc.combine(dispatchResult)
            }
    }
}