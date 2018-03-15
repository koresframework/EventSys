/*
 *      EventSys - Event implementation generator written on top of Kores
 *
 *         The MIT License (MIT)
 *
 *      Copyright (c) 2018 ProjectSandstone <https://github.com/ProjectSandstone/EventSys>
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
package com.github.projectsandstone.eventsys.util

import com.github.jonathanxd.iutils.type.TypeInfo
import com.github.jonathanxd.iutils.kt.typeInfo
import com.github.jonathanxd.kores.type.genericTypeOf
import com.github.projectsandstone.eventsys.event.Event
import com.github.projectsandstone.eventsys.event.EventListener
import com.github.projectsandstone.eventsys.event.EventManager
import com.github.projectsandstone.eventsys.extension.ExtensionSpecification
import com.github.projectsandstone.eventsys.gen.event.EventGenerator
import com.github.projectsandstone.eventsys.gen.event.PropertyInfo

/**
 * Creates implementation class of event [T].
 *
 * @param additionalProperties Additional properties of implementation.
 * @param extensions Additional extensions of implementation.
 */
inline fun <reified T : Event> EventGenerator.createEventClass(additionalProperties: List<PropertyInfo> = emptyList(),
                                                               extensions: List<ExtensionSpecification> = emptyList()) =
        this.createEventClass<T>(genericTypeOf<T>(), additionalProperties, extensions)

/**
 * Creates implementation class of event [T].
 *
 * Non blocking asynchronous creation.
 *
 * @param additionalProperties Additional properties of implementation.
 * @param extensions Additional extensions of implementation.
 */
inline fun <reified T : Event> EventGenerator.createEventClassAsync(additionalProperties: List<PropertyInfo> = emptyList(),
                                                                    extensions: List<ExtensionSpecification> = emptyList()) =
        this.createEventClassAsync<T>(genericTypeOf<T>(), additionalProperties, extensions)

/**
 * Creates implementation of factory [T].
 */
inline fun <reified T : Any> EventGenerator.createFactory() =
        this.createFactory<T>(genericTypeOf<T>())

/**
 * Creates implementation of factory [T].
 *
 * Non blocking asynchronous creation.
 */
inline fun <reified T : Any> EventGenerator.createFactoryAsync() =
        this.createFactoryAsync<T>(genericTypeOf<T>())

/**
 * Register the listener to [Event] [T].
 *
 * @param T Event type
 * @param plugin Plugin instance
 * @param eventListener Event Listener instance.
 */
inline fun <reified T : Event> EventManager.registerListener(plugin: Any, eventListener: EventListener<T>) {
    this.registerListener(plugin, genericTypeOf<T>(), eventListener)
}

inline fun <T : Event> EventListener(crossinline f: (event: T, dispatcher: Any) -> Unit): EventListener<T> =
        object : EventListener<T> {
            override fun onEvent(event: T, dispatcher: Any) = f(event, dispatcher)
        }