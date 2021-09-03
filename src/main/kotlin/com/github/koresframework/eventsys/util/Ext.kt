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
package com.github.koresframework.eventsys.util

import com.koresframework.kores.type.genericTypeOf
import com.github.koresframework.eventsys.context.EnvironmentContext
import com.github.koresframework.eventsys.event.Event
import com.github.koresframework.eventsys.event.EventListener
import com.github.koresframework.eventsys.event.EventListenerRegistry
import com.github.koresframework.eventsys.event.EventManager
import com.github.koresframework.eventsys.extension.ExtensionSpecification
import com.github.koresframework.eventsys.gen.event.EventGenerator
import com.github.koresframework.eventsys.gen.event.PropertyInfo
import com.github.koresframework.eventsys.result.ListenResult

/**
 * Creates implementation class of event [T].
 *
 * @param additionalProperties Additional properties of implementation.
 * @param extensions Additional extensions of implementation.
 */
inline fun <reified T : Event> EventGenerator.createEventClass(additionalProperties: List<PropertyInfo> = emptyList(),
                                                               extensions: List<ExtensionSpecification> = emptyList(),
                                                               ctx: EnvironmentContext = EnvironmentContext()) =
        this.createEventClass<T>(genericTypeOf<T>(), additionalProperties, extensions, ctx)

/**
 * Creates implementation class of event [T].
 *
 * Non blocking asynchronous creation.
 *
 * @param additionalProperties Additional properties of implementation.
 * @param extensions Additional extensions of implementation.
 */
inline fun <reified T : Event> EventGenerator.createEventClassAsync(additionalProperties: List<PropertyInfo> = emptyList(),
                                                                    extensions: List<ExtensionSpecification> = emptyList(),
                                                                    ctx: EnvironmentContext = EnvironmentContext()) =
        this.createEventClassAsync<T>(genericTypeOf<T>(), additionalProperties, extensions, ctx)

/**
 * Creates implementation of factory [T].
 */
inline fun <reified T : Any> EventGenerator.createFactory(ctx: EnvironmentContext = EnvironmentContext()) =
        this.createFactory<T>(genericTypeOf<T>(), ctx)

/**
 * Creates implementation of factory [T].
 *
 * Non blocking asynchronous creation.
 */
inline fun <reified T : Any> EventGenerator.createFactoryAsync(ctx: EnvironmentContext = EnvironmentContext()) =
        this.createFactoryAsync<T>(genericTypeOf<T>(), ctx)

/**
 * Register the listener to [Event] [T].
 *
 * @param T Event type
 * @param plugin Plugin instance
 * @param eventListener Event Listener instance.
 */
inline fun <reified T : Event> EventListenerRegistry.registerListener(plugin: Any, eventListener: EventListener<T>) {
    this.registerListener(plugin, genericTypeOf<T>(), eventListener)
}

inline fun <T : Event> EventListener(crossinline f: suspend (event: T, dispatcher: Any) -> ListenResult): EventListener<T> =
        object : EventListener<T> {
            override suspend fun onEvent(event: T, dispatcher: Any) = f(event, dispatcher)
        }