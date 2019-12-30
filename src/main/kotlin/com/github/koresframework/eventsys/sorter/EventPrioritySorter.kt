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
package com.github.koresframework.eventsys.sorter

import com.github.koresframework.eventsys.event.Event
import com.github.koresframework.eventsys.event.EventListener

class EventPrioritySorter<T : Event> : Comparator<EventListener<T>>,
        (EventListener<T>, EventListener<T>) -> Int {

    override fun compare(o1: EventListener<T>?, o2: EventListener<T>?): Int =
            compareValues(o1?.priority, o2?.priority)

    override fun invoke(p1: EventListener<T>, p2: EventListener<T>): Int = this.compare(p1, p2)

    companion object {
        private val sorter = EventPrioritySorter<Event>()

        @Suppress("UNCHECKED_CAST")
        @JvmStatic
        fun <T: Event> anySorter(): Comparator<EventListener<*>> =
                this.sorter as Comparator<EventListener<*>>
    }
}