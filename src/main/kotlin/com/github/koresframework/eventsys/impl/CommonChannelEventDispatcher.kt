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

import com.github.koresframework.eventsys.channel.ChannelSet
import com.github.koresframework.eventsys.event.ChannelEventDispatcher
import com.github.koresframework.eventsys.event.ChannelEventListenerRegistry
import com.github.koresframework.eventsys.event.Event
import com.github.koresframework.eventsys.event.EventDispatcher
import com.github.koresframework.eventsys.gen.event.EventGenerator
import com.github.koresframework.eventsys.logging.LoggerInterface
import com.github.koresframework.eventsys.result.DispatchResult
import java.lang.reflect.Type
import java.util.concurrent.Executor

/**
 * Common implementation of [ChannelEventDispatcher] backed to a [normal dispatcher][backendDispatcher].
 */
class CommonChannelEventDispatcher(override val eventGenerator: EventGenerator,
                                   override val executor: Executor,
                                   override val logger: LoggerInterface,
                                   private val channelEventListenerRegistry: ChannelEventListenerRegistry) : AbstractEventDispatcher(), ChannelEventDispatcher {

    override val channels: ChannelSet
        get() = this.channelEventListenerRegistry.channels

    override fun <T : Event> getListeners(event: T, eventType: Type, channel: String): Iterable<EventListenerContainer<*>> {
        return if (ChannelSet.Expression.isAll(channel) || channel in this.channelEventListenerRegistry.channels) {
            this.channelEventListenerRegistry.getListenersContainers(event, eventType, channel)
        } else {
            emptyList()
        }
    }

}

class ChannelDispatcherDistributor(dispatchers: List<ChannelEventDispatcher>,
                                   private val globalDispatcher: EventDispatcher) : EventDispatcher, ChannelEventDispatcher {

    private val registeredChannelDispatchers = dispatchers.toMutableList()
    private val registeredChannelDispatcherMap =
            dispatchers.flatMap { dispatcher ->
                dispatcher.channels.toSet().map { it to dispatcher }
            }.groupBy({ it.first }, { it.second })

    override val channels: ChannelSet
        get() = ChannelSet.Include(this.registeredChannelDispatchers.map { it.channels.toSet() }.flatten().toSet())


    override fun <T : Event> dispatch(event: T, eventType: Type, dispatcher: Any, channel: String, isAsync: Boolean): DispatchResult<T> =
            if (ChannelSet.Expression.isAll(channel)) {
                this.registeredChannelDispatchers.map {
                    it.dispatch(event, eventType, dispatcher, channel, isAsync)
                }
            } else {
                this.registeredChannelDispatcherMap[channel]?.map {
                    it.dispatch(event, eventType, dispatcher, channel, isAsync)
                }
            }?.reduce { acc, dispatchResult ->
                DispatchResult(acc.listenExecutionResults + dispatchResult.listenExecutionResults)
            } ?: DispatchResult(emptyList())

}