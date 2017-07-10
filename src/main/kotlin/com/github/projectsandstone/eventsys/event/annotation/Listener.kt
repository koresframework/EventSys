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
package com.github.projectsandstone.eventsys.event.annotation

import com.github.projectsandstone.eventsys.event.EventPriority
import com.github.projectsandstone.eventsys.event.Event
import com.github.projectsandstone.eventsys.event.ListenerSpec

/**
 * Mark function to handle an event.
 *
 * The method MUST specify the [Event] in the first parameter, other parameters will be filled with
 * properties of [Event], if has no object that matches the parameter type, the method will
 * not be invoked.
 *
 * You **MUST** to use [Name] annotation to provide name of property.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class Listener(
        /**
         * Ignore this listener if event is cancelled
         */
        val ignoreCancelled: Boolean = false,

        /**
         * Priority of this listener
         */
        val priority: EventPriority = EventPriority.NORMAL,

        /**
         * Phase where this method listen to.
         *
         * @see ListenerSpec.phase
         */
        val phase: Int = -1
)