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
package com.github.koresframework.eventsys.channel

import com.github.jonathanxd.iutils.collection.wrapper.WrapperCollections
import com.github.koresframework.eventsys.annotation.EventSysExperimental

/**
 * Abstract representation of channel inclusion rule.
 */
sealed class ChannelSet {
    /**
     * Returns whether this set has [channel] or not.
     */
    abstract operator fun contains(channel: String): Boolean

    /**
     * Returns whether this set has all [channels] or not.
     */
    abstract fun containsAll(channels: Collection<String>): Boolean

    /**
     * Returns whether this set has any channel of [channels] or not.
     */
    abstract fun containsAny(channels: Collection<String>): Boolean

    /**
     * Filter out channels of [channelSet] that are not included in this set.
     */
    abstract fun filterChannels(channelSet: Set<String>): Set<String>

    /**
     * Join to string representation.
     */
    abstract fun joinToString(): String

    /**
     * Creates a set from this [ChannelSet] object.
     */
    abstract fun toSet(): Set<String>

    object All : ChannelSet() {
        override fun contains(channel: String): Boolean = true
        override fun containsAll(channels: Collection<String>): Boolean = true
        override fun containsAny(channels: Collection<String>): Boolean = true
        override fun joinToString(): String = "@all"
        override fun filterChannels(channelSet: Set<String>): Set<String> = channelSet
        override fun toSet(): Set<String> = setOf("@all")
    }

    object None : ChannelSet() {
        override fun contains(channel: String): Boolean = false
        override fun containsAll(channels: Collection<String>): Boolean = false
        override fun containsAny(channels: Collection<String>): Boolean = false
        override fun joinToString(): String = "![@all]"
        override fun filterChannels(channelSet: Set<String>): Set<String> = emptySet()
        override fun toSet(): Set<String> = setOf("!@all")
    }

    class Include(channels: Set<String>) : ChannelSet() {
        private val channels: Set<String> = WrapperCollections.immutableSet(channels.toSet())

        override fun contains(channel: String): Boolean =
                this.channels.contains(channel)

        override fun containsAll(channels: Collection<String>): Boolean =
                this.channels.containsAll(channels)

        override fun containsAny(channels: Collection<String>): Boolean =
                channels.any { this.channels.contains(it) }

        override fun joinToString(): String = this.channels.joinToString()

        override fun filterChannels(channelSet: Set<String>): Set<String> =
            channelSet.filterTo(mutableSetOf()) { this.contains(it) }

        override fun toSet(): Set<String> = this.channels
    }

    /**
     * Not implemented yet.
     */
    class Exclude(channels: Set<String>) : ChannelSet() {
        private val channels: Set<String> = WrapperCollections.immutableSet(channels.toSet())

        override fun contains(channel: String): Boolean =
                !this.channels.contains(channel)

        override fun containsAll(channels: Collection<String>): Boolean =
                !this.channels.containsAll(channels)

        override fun containsAny(channels: Collection<String>): Boolean =
                channels.none { this.channels.contains(it) }

        override fun joinToString(): String = "!${this.channels.joinToString()}"

        override fun filterChannels(channelSet: Set<String>): Set<String> =
            channelSet.filterTo(mutableSetOf()) { this.contains(it) }

        override fun toSet(): Set<String> = emptySet() // TODO
    }

    object Expression {
        /**
         * Represents a [ChannelSet] which includes all channels
         */
        const val ALL = "@all"

        /**
         * Represents a [ChannelSet] which does not include any channels.
         */
        const val NONE = "!@all"

        /**
         * Returns whether [expr] is an [ALL] channel expression.
         */
        fun isAll(expr: String) = expr == ALL

        /**
         * Returns whether [expr] is an [NONE] channel expression.
         */
        fun isNone(expr: String) = expr == NONE

        /**
         * Creates [ChannelSet] from [expr].
         */
        fun fromExpr(expr: String) = when (expr) {
            ALL -> All
            NONE -> None
            else ->
                if (expr.startsWith("!")) Exclude(expr.split(",").toSet())
                else Include(expr.split(",").toSet())
        }
    }

    companion object {
        /**
         * [All] [ChannelSet]
         */
        @JvmField
        val ALL = All

        /**
         * [None] [ChannelSet]
         */
        @JvmField
        val NONE = None

        /**
         * Returns whether [channelSet] includes all channels.
         */
        @JvmStatic
        fun isAll(channelSet: ChannelSet) = channelSet.toSet().contains("@all")

        /**
         * Returns whether [channelSet] does not include any channel.
         */
        @JvmStatic
        fun isNone(channelSet: ChannelSet) = channelSet.toSet().contains("!@all")

        /**
         * Creates a [ChannelSet] which includes a single [channel].
         */
        @JvmStatic
        fun include(channel: String) = Include(setOf(channel))

        /**
         * Creates a [ChannelSet] which includes a multiple [channels].
         */
        @JvmStatic
        fun include(channels: Set<String>) = Include(channels)
    }
}

