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
package com.github.koresframework.eventsys.event.annotation

import com.github.jonathanxd.kores.base.EnumValue
import com.github.jonathanxd.kores.base.KoresAnnotation
import com.github.koresframework.eventsys.channel.ChannelSet
import com.github.koresframework.eventsys.event.Event
import com.github.koresframework.eventsys.event.EventPriority
import com.github.koresframework.eventsys.event.ListenerSpec

/**
 * Marks the function as a event listener function.
 *
 * The function must specify the [Event] in the first parameter (unless annotated with [Filter]),
 * other parameters will be filled with matching properties of [Event]. If [Event] does not have
 * the property, and the parameter is not annotated with [OptionalProperty], the event will be
 * ignored by this listener. The property parameters must have name retention (compiled by Javac with
 * `-parameters`, compiled by Kotlinc) or have [Name] annotation.
 *
 * @property ignoreCancelled Ignore this listener if event is cancelled.
 * @property priority Priority of this listener.
 * @property channel Channel where this method listen to, see [ListenerSpec.channel]
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class Listener(
    val ignoreCancelled: Boolean = false,
    val priority: EventPriority = EventPriority.NORMAL,
    val channel: String = ChannelSet.Expression.ALL
)


val KoresAnnotation?.listenerIgnoreCancelled get () = this?.values?.get("ignoreCancelled") == true

val KoresAnnotation?.listenerPriority
    get () = (this?.values?.get("priority") as? EnumValue)?.enumEntry?.let { enumValueOf<EventPriority>(it) }
            ?: EventPriority.NORMAL

val KoresAnnotation?.listenerChannel
    get() = (this?.values?.get("channel") as? String) ?: ChannelSet.Expression.ALL