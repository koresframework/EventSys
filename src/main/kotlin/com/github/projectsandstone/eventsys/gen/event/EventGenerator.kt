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
package com.github.projectsandstone.eventsys.gen.event

import com.github.jonathanxd.iutils.option.Options
import com.github.jonathanxd.iutils.type.TypeInfo
import com.github.projectsandstone.eventsys.event.Event
import com.github.projectsandstone.eventsys.event.EventListener
import com.github.projectsandstone.eventsys.event.ListenerSpec
import com.github.projectsandstone.eventsys.gen.check.CheckHandler
import com.github.projectsandstone.eventsys.logging.LoggerInterface
import java.lang.reflect.Method
import java.util.concurrent.Future

/**
 * Event generator manager.
 */
interface EventGenerator {

    /**
     * Options of generator.
     *
     * @see EventGeneratorOptions
     */
    val options: Options

    /**
     * Logger of this generator
     */
    val logger: LoggerInterface

    /**
     * Check handler
     */
    var checkHandler: CheckHandler

    /**
     * Registers a [extension][extensionSpecification] for [event base class][base].
     */
    fun registerExtension(base: Class<*>, extensionSpecification: ExtensionSpecification)

    /**
     * Registers an [implementation] of a [eventClassSpecification].
     *
     * Note that the [implementation] should follow some rules:
     *
     * - Provide a constructor which receive properties values and is annotated with `Name`, which will
     * provide the name of properties.
     * - Properly implement `getProperties` and `getExtension` (if applicable). `getProperties` should return an
     * immutable map.
     * - Properly register properties in the map, no one property should have null getter or setter, always
     * use specialized properties.
     *
     * Note that registered event implementation will only be used if the [eventClassSpecification]
     * is the same as expected specification, if a class requires an extension, the class need to be
     * generated (unless you specify your own implementation with the extension).
     */
    fun <T : Event> registerEventImplementation(eventClassSpecification: EventClassSpecification<T>,
                                                implementation: Class<T>)

    /**
     * Creates event factory class.
     *
     * @see EventFactoryClassGenerator
     */
    fun <T : Any> createFactory(factoryClass: Class<T>): T

    /**
     * Asynchronously create [factoryClass] instance, only use this method if you do not need
     * the factory immediately.
     *
     * This also generated required event classes.
     *
     * @see EventFactoryClassGenerator
     */
    fun <T : Any> createFactoryAsync(factoryClass: Class<T>): Future<T>

    /**
     * Creates event class
     *
     * @see EventClassGenerator
     */
    fun <T : Event> createEventClass(type: TypeInfo<T>, additionalProperties: List<PropertyInfo>): Class<T> =
            this.createEventClass(type, additionalProperties, emptyList())

    /**
     * Asynchronously create event class, only use this method if you do not need
     * the event class immediately.
     *
     * @see EventClassGenerator
     */
    fun <T : Event> createEventClassAsync(type: TypeInfo<T>, additionalProperties: List<PropertyInfo>): Future<Class<T>> =
            this.createEventClassAsync(type, additionalProperties, emptyList())

    /**
     * Creates event class
     *
     * @see EventClassGenerator
     */
    fun <T : Event> createEventClass(type: TypeInfo<T>, additionalProperties: List<PropertyInfo>, extensions: List<ExtensionSpecification>): Class<T>

    /**
     * Asynchronously create event class, only use this method if you do not need
     * the event class immediately.
     *
     * @see EventClassGenerator
     */
    fun <T : Event> createEventClassAsync(type: TypeInfo<T>, additionalProperties: List<PropertyInfo>, extensions: List<ExtensionSpecification>): Future<Class<T>>

    /**
     * Creates method listener class
     *
     * @see MethodListenerGenerator
     */
    fun createMethodListener(owner: Any, method: Method, instance: Any?, listenerSpec: ListenerSpec): EventListener<Event>

    /**
     * Asynchronously create method listener class instance, only use this method if you do not need
     * the event listener method class immediately.
     *
     * @see MethodListenerGenerator
     */
    fun createMethodListenerAsync(owner: Any, method: Method, instance: Any?, listenerSpec: ListenerSpec): Future<EventListener<Event>>

}