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

import com.github.jonathanxd.iutils.collection.wrapper.WrapperCollections
import com.github.koresframework.eventsys.channel.ChannelSet
import com.github.koresframework.eventsys.channel.parseChannelSet
import com.github.koresframework.eventsys.context.EnvironmentContext
import com.github.koresframework.eventsys.event.*
import com.github.koresframework.eventsys.event.EventListener
import com.github.koresframework.eventsys.event.annotation.Filter
import com.github.koresframework.eventsys.event.annotation.Listener
import com.github.koresframework.eventsys.gen.event.EventGenerator
import com.github.koresframework.eventsys.gen.event.EventGeneratorOptions
import com.github.koresframework.eventsys.logging.LoggerInterface
import com.github.koresframework.eventsys.logging.MessageType
import com.github.koresframework.eventsys.util.hasEventFirstArg
import com.github.koresframework.eventsys.util.mh.MethodDispatcher
import java.lang.reflect.Method
import java.lang.reflect.Type
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentSkipListSet

/**
 * Stores channel listeners in a pair of [Channel name][String] to [EventListener] sorted set,
 * each set of listeners has its own channel node, so listeners get a real separated dispatch instead of
 * a filter and dispatch approach like [SharedSetChannelEventListenerRegistry] does.
 */
class PerChannelEventListenerRegistry(
    private val sorter: Comparator<EventListener<*>>,
    override val logger: LoggerInterface,
    override val eventGenerator: EventGenerator
) : AbstractEventListenerRegistry() {
    private val channelListenerMap = ConcurrentHashMap<String, SortedSet<EventListenerContainer<*>>>()

    override fun <T : Event> registerListener(
        owner: Any,
        eventType: Type,
        eventListener: EventListener<T>
    ): ListenerRegistryResults {
        val validate = this.validateListener(eventListener)
        if (validate != null) {
            return validate
        }

        val channels = eventListener.channel.parseChannelSet()

        val registered = channels.toSet().map { channel ->

            this.channelListenerMap.computeIfAbsent(channel) {
                ConcurrentSkipListSet(EventListenerContainerComparator(sorter))
            }.add(EventListenerContainer(owner, eventType, eventListener))

            registered(eventListener, channel)
        }

        return ListenerRegistryResults(registered)
    }

    override fun <T : Event> getListeners(
        event: T,
        eventType: Type,
        channel: String
    ): Iterable<EventListenerContainer<*>> =
        if (ChannelSet.Expression.isAll(channel)) {
            this.channelListenerMap[channel] ?: emptySet()
        } else {
            val chan = ChannelSet.Expression.parseExpression(channel)
            (chan.filterChannels(ChannelSet.include(channelListenerMap.keys)) + setOf("@all"))
                .flatMapTo(TreeSet(EventListenerContainerComparator(sorter))) {
                    this.channelListenerMap[it] ?: emptySet()
                }
        }.filter {
            it.isAssignableFrom(eventType)
        }

    override fun getListenersContainers(): Set<EventListenerContainer<*>> =
        this.channelListenerMap.values.flatten().toSet()

}

/**
 * Filter-dispatch approach. All listeners shares the same registry and are filtered in the
 * time of dispatch.
 */
class SharedSetChannelEventListenerRegistry(
    private val sorter: Comparator<EventListener<*>>,
    override val logger: LoggerInterface,
    override val eventGenerator: EventGenerator
) : AbstractEventListenerRegistry() {
    private val listeners: SortedSet<EventListenerContainer<*>> = ConcurrentSkipListSet(EventListenerContainerComparator(this.sorter))

    override fun <T : Event> registerListener(
        owner: Any,
        eventType: Type,
        eventListener: EventListener<T>
    ): ListenerRegistryResults {
        this.listeners.add(EventListenerContainer(owner, eventType, eventListener))
        return registered(eventListener, eventListener.channel.parseChannelSet().toSet())
    }

    override fun <T : Event> getListeners(
        event: T,
        eventType: Type,
        channel: String
    ): Iterable<EventListenerContainer<*>> =
        if (ChannelSet.Expression.isAll(channel)) {
            this.listeners
        } else {
            val expr = channel.parseChannelSet()
            this.listeners
                .filter { expr.contains(it.eventListener.channel) }
        }.filter { it.isAssignableFrom(eventType) }

    override fun getListenersContainers(): Set<EventListenerContainer<*>> =
        this.listeners

}

/**
 * A channel filtered implementation of listener registry, useful for distributed systems based on
 * ignore-on-receive (instead of ignore-on-send) approach.
 *
 * This registry only allows listeners of [channels] to be registered. If the listener does not meet
 * the criteria, it will be ignored.
 *
 * If a Listener does have a `Include` expression, it will only be registered if there are at least
 * one channel that is included.
 *
 * Read more about `ChannelSet` [here][ChannelSet].
 */
class CommonChannelEventListenerRegistry(
    override val channels: ChannelSet,
    private val sorter: Comparator<EventListener<*>>,
    override val logger: LoggerInterface,
    override val eventGenerator: EventGenerator
) : AbstractEventListenerRegistry(), ChannelEventListenerRegistry {

    private val channelToListenerMap = ConcurrentHashMap<String, SortedSet<EventListenerContainer<*>>>()

    override fun <T : Event> registerListener(
        owner: Any,
        eventType: Type,
        eventListener: EventListener<T>
    ): ListenerRegistryResults {
        val validate = this.validateListener(eventListener)
        if (validate != null) {
            return validate
        }

        // Always Include or @all
        val listenerSet = eventListener.channel.parseChannelSet()
        val filter = listenerSet.filterChannels(this.channels)

        return if (listenerSet.listenTo(channels)) {
            this.channelToListenerMap.computeIfAbsent(eventListener.channel) {
                ConcurrentSkipListSet(EventListenerContainerComparator(sorter))
            }.add(EventListenerContainer(owner, eventType, eventListener))
            registered(eventListener, filter)
        } else {
            notRegistered(eventListener, listenerSet.toSet())
        }
    }

    override fun <T : Event> getListeners(
        event: T,
        eventType: Type,
        channel: String
    ): Iterable<EventListenerContainer<*>> {
        return WrapperCollections.immutableSet(
            if (ChannelSet.Expression.isAll(channel)) {
                this.channelToListenerMap.values.flatten().toSet()
            } else {
                val f = ChannelSet.Expression.parseExpression(channel)
                val chan = f.filterChannels(ChannelSet.include(this.channelToListenerMap.keys))
                if (chan.isEmpty())
                    emptySet()
                else
                    chan.flatMapTo(TreeSet(EventListenerContainerComparator(sorter))) {
                        this.channelToListenerMap.getOrDefault(it, emptySet())
                    }
            }
        )
    }

    override fun getListenersContainers(): Set<EventListenerContainer<*>> =
        this.channelToListenerMap.values.flatten().toSet()
}

/**
 * Abstract implementation of [EventListenerRegistry].
 *
 * This includes:
 *
 * - The logic for reflective registration of methods, backing to normal [registerListener].
 * - Some common implementation of [getListeners] methods.
 *
 * Each implementation may choose your own listener retrieval logic. They could be [channel based][ChannelSet],
 * [event type based][Event.eventType], shared and mixed. EventSys provides only shared implementation and
 * channel based ones:
 *
 * - [Shared implementation][SharedSetChannelEventListenerRegistry]: Shares the same set for all listeners.
 * - [Channel Based implementation][PerChannelEventListenerRegistry]: Every channel has its own set and the set
 *   is stored in a `HashMap`, providing `O(1)` avg.
 * - [Channel filtered implementation][CommonChannelEventListenerRegistry]: Every channel has its own set and the set
 *   is stored in a `HashMap`, providing `O(1)` avg.
 *   This implementation only register listeners that listens to the specified
 *   [*channel set*][CommonChannelEventListenerRegistry.channels].
 *
 * A mixed implementation may be a bit hard to implement and may not provide the enough
 * performance improvement to consider doing, but if you are curious how to do that, you firstly
 * need a [channel based][ChannelSet] retrieval and then an [event type based][Event.eventType]
 * retrieval. Then, stores the listener on each node with sub-types mapped to listener.
 */
abstract class AbstractEventListenerRegistry : EventListenerRegistry {

    protected abstract val logger: LoggerInterface
    protected abstract val eventGenerator: EventGenerator

    protected abstract fun <T : Event> getListeners(
        event: T,
        eventType: Type,
        channel: String
    ): Iterable<EventListenerContainer<*>>

    // Register

    @Suppress("UNCHECKED_CAST")
    private fun <T : Event> registerGenericListener(
        owner: Any,
        eventType: Type,
        eventListener: EventListener<*>
    ): ListenerRegistryResults =
        this.registerListener(owner, eventType, eventListener as EventListener<T>)

    override fun registerListeners(
        owner: Any, listener: Any,
        ctx: EnvironmentContext
    ): ListenerRegistryResults =
        ListenerRegistryResults(
            this.createMethodListeners(owner, listener, ctx).map {
                this.registerGenericListener<Event>(owner, it.eventType, it.eventListener)
            }.flatMap { it.results }
        )

    override fun registerMethodListener(
        owner: Any,
        listenerClass: Type,
        instance: Any?,
        method: Method,
        ctx: EnvironmentContext
    ): ListenerRegistryResults =
        this.createMethodListener(
            listenerClass = listenerClass,
            owner = owner,
            instance = instance,
            method = method,
            ctx = ctx
        ).let {
            this.registerGenericListener<Event>(owner, it.eventType, it.eventListener)
        }

    @Suppress("UNCHECKED_CAST")
    private fun createMethodListener(
        owner: Any,
        listenerClass: Type,
        instance: Any?,
        method: Method,
        ctx: EnvironmentContext
    ): EventListenerContainer<*> {
        return this.eventGenerator.createListenerSpecFromMethod(method).let { spec ->
            EventListenerContainer(
                owner,
                spec.eventType,
                this.eventGenerator.createMethodListener(
                    listenerClass,
                    method,
                    instance,
                    spec,
                    ctx
                ).resolve()
            )
        }
    }

    private fun createMethodListeners(
        owner: Any,
        instance: Any,
        ctx: EnvironmentContext
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
                    instance = instance,
                    ctx = ctx
                )
            }
        }
    }

    protected fun validateListener(eventListener: EventListener<*>): ListenerRegistryResults? {
        val set = eventListener.channel.parseChannelSet()

        if (set.isExclude) {
            logger.log(
                "Listeners can not be registered with exclude channels expression! Listener: $eventListener",
                MessageType.INVALID_LISTENER_DECLARATION,
                EnvironmentContext()
            )
            return notRegistered(eventListener, eventListener.channel).coerce()
        }

        return null
    }

    // /Register
    // Retrieval

    override fun getListenersAsPair(): Set<Pair<Type, EventListener<*>>> {
        return this.getListenersContainers()
            .map { Pair(it.eventType, it.eventListener) }
            .toSet()
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : Event> getListeners(eventType: Type): Set<Pair<Type, EventListener<T>>> {
        return this.getListenersContainers()
            .filter { it.isAssignableFrom(eventType) }
            .map { Pair(it.eventType, it.eventListener) }
            .toSet() as Set<Pair<Type, EventListener<T>>>
    }

    override fun <T : Event> getListenersContainers(eventType: Type): Set<EventListenerContainer<*>> {
        return this.getListenersContainers()
            .filter { it.isAssignableFrom(eventType) }
            .toSet()
    }

    override fun <T : Event> getListenersContainers(
        event: T,
        eventType: Type,
        channel: String
    ): Iterable<EventListenerContainer<*>> =
        this.getListeners(event, eventType, channel)

    // /Retrieval


}