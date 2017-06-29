/*
 *      EventImpl - Event implementation generator written on top of CodeAPI
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
@file:Suppress("DEPRECATED_JAVA_ANNOTATION")

package com.github.projectsandstone.eventsys.event.annotation

import com.github.jonathanxd.iutils.`object`.Default
import kotlin.reflect.KClass

/**
 * Defines the extension of a event class that will be created by the factory class.
 *
 * Since 1.1, event interfaces (and sub-classes) can be annotated with this.
 * If annotated in event interface or sub-classes, the event generated will always implement
 * extensions regardless the factory specification.
 *
 * Since 1.1.2, extension classes can override properties getter and setter. And EventSys will not generate
 * fields for properties where both getter and setter is override (or only the getter if property is immutable).
 *
 * @property implement Interface which generated event class should implement.
 * @property extensionClass An extension class which implements functions of
 * [implement] interface and provide additional methods. All methods of [extensionClass]
 * is added to target event and is delegated to [extensionClass], which should have a single-arg
 * constructor which accepts target event type (or a sub-type, it includes the [implement]).
 * Only non-static functions are added to target event.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Repeatable
annotation class Extension(
        val implement: KClass<*> = Default::class,
        val extensionClass: KClass<*> = Default::class
)