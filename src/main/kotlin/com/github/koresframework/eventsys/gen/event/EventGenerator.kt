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
package com.github.koresframework.eventsys.gen.event

import com.github.jonathanxd.iutils.option.Options
import com.github.jonathanxd.kores.base.ClassDeclaration
import com.github.jonathanxd.kores.base.MethodDeclaration
import com.github.jonathanxd.kores.type.Generic
import com.github.jonathanxd.kores.type.asGeneric
import com.github.jonathanxd.kores.type.koresType
import com.github.jonathanxd.kores.type.toGeneric
import com.github.jonathanxd.kores.util.createKoresTypeDescriptor
import com.github.jonathanxd.kores.util.genericTypeToDescriptor
import com.github.koresframework.eventsys.event.Event
import com.github.koresframework.eventsys.event.EventListener
import com.github.koresframework.eventsys.event.ListenerSpec
import com.github.koresframework.eventsys.extension.ExtensionSpecification
import com.github.koresframework.eventsys.gen.GenerationEnvironment
import com.github.koresframework.eventsys.gen.ResolvableDeclaration
import com.github.koresframework.eventsys.gen.Runtime
import com.github.koresframework.eventsys.gen.check.CheckHandler
import com.github.koresframework.eventsys.logging.LoggerInterface
import com.github.koresframework.eventsys.util.DeclarationCache
import java.lang.reflect.Method
import java.lang.reflect.Type
import java.util.concurrent.CompletableFuture

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
     * Generation environment
     */
    val generationEnvironment: GenerationEnvironment

    /**
     * Check handler
     */
    var checkHandler: CheckHandler

    /**
     * Plugs an [extension][extensionSpecification] to events of type [base] and all
     * other events that derive from [base].
     */
    fun registerExtension(base: Type, extensionSpecification: ExtensionSpecification)

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
    fun <T : Event> registerEventImplementation(
        eventClassSpecification: EventClassSpecification,
        implementation: Class<out T>
    )

    /**
     * Creates event factory class.
     *
     * @see EventFactoryClassGenerator
     */
    fun <T : Any> createFactory(factoryType: Type): ResolvableDeclaration<T>

    /**
     * Asynchronously create [factoryType] instance, only use this method if you do not need
     * the factory immediately.
     *
     * This also generated required event classes.
     *
     * @see EventFactoryClassGenerator
     */
    fun <T : Any> createFactoryAsync(factoryType: Type): CompletableFuture<out ResolvableDeclaration<T>>

    /**
     * Creates event class
     *
     * @see EventClassGenerator
     */
    fun <T : Event> createEventClass(type: Class<T>): ResolvableDeclaration<Class<out T>> =
            this.createEventClass(Generic.type(type))

    /**
     * Creates event class
     *
     * @see EventClassGenerator
     */
    fun <T : Event> createEventClass(type: Type): ResolvableDeclaration<Class<out T>> =
        this.createEventClass(type, emptyList(), emptyList())

    /**
     * Creates event class
     *
     * @see EventClassGenerator
     */
    fun <T : Event> createEventClass(
        type: Type,
        additionalProperties: List<PropertyInfo>
    ): ResolvableDeclaration<Class<out T>> =
        this.createEventClass(type, additionalProperties, emptyList())

    /**
     * Asynchronously create event class, only use this method if you do not need
     * the event class immediately.
     *
     * @see EventClassGenerator
     */
    fun <T : Event> createEventClassAsync(
        type: Type,
        additionalProperties: List<PropertyInfo>
    ): CompletableFuture<ResolvableDeclaration<Class<out T>>> =
        this.createEventClassAsync(type, additionalProperties, emptyList())

    /**
     * Creates event class
     *
     * @see EventClassGenerator
     */
    fun <T : Event> createEventClass(
        type: Type,
        additionalProperties: List<PropertyInfo>,
        extensions: List<ExtensionSpecification>
    ): ResolvableDeclaration<Class<out T>>

    /**
     * Asynchronously create event class, only use this method if you do not need
     * the event class immediately.
     *
     * @see EventClassGenerator
     */
    fun <T : Event> createEventClassAsync(
        type: Type,
        additionalProperties: List<PropertyInfo>,
        extensions: List<ExtensionSpecification>
    ): CompletableFuture<ResolvableDeclaration<Class<out T>>>

    /**
     * Creates method listener class
     *
     * @see MethodListenerGenerator
     */
    fun createMethodListener(
        listenerClass: Type,
        method: MethodDeclaration,
        listenerSpec: ListenerSpec
    ): ResolvableDeclaration<Class<out EventListener<Event>>>

    /**
     * Asynchronously create method listener class instance, only use this method if you do not need
     * the event listener method class immediately.
     *
     * @see MethodListenerGenerator
     */
    fun createMethodListenerAsync(
        listenerClass: Type,
        method: MethodDeclaration,
        listenerSpec: ListenerSpec
    ): CompletableFuture<ResolvableDeclaration<Class<out EventListener<Event>>>>


    /**
     * Creates method listener class
     *
     * @see MethodListenerGenerator
     */
    fun createMethodListener(
        listenerClass: Type,
        method: MethodDeclaration,
        instance: Any?,
        listenerSpec: ListenerSpec
    ): ResolvableDeclaration<EventListener<Event>>

    /**
     * Asynchronously create method listener class instance, only use this method if you do not need
     * the event listener method class immediately.
     *
     * @see MethodListenerGenerator
     */
    fun createMethodListenerAsync(
        listenerClass: Type,
        method: MethodDeclaration,
        instance: Any?,
        listenerSpec: ListenerSpec
    ): CompletableFuture<ResolvableDeclaration<EventListener<Event>>>

    /**
     * Creates method listener class
     *
     * @see MethodListenerGenerator
     */
    fun createMethodListener(
        listenerClass: Type,
        method: Method,
        instance: Any?,
        listenerSpec: ListenerSpec
    ): ResolvableDeclaration<EventListener<Event>>

    /**
     * Asynchronously create method listener class instance, only use this method if you do not need
     * the event listener method class immediately.
     *
     * @see MethodListenerGenerator
     */
    fun createMethodListenerAsync(
        listenerClass: Type,
        method: Method,
        instance: Any?,
        listenerSpec: ListenerSpec
    ): CompletableFuture<ResolvableDeclaration<EventListener<Event>>>

    /**
     * Creates a [ListenerSpec] from [Kores method declaration][method].
     */
    fun createListenerSpecFromMethod(method: MethodDeclaration): ListenerSpec

    /**
     * Creates a [ListenerSpec] from [Java reflection method][method].
     */
    fun createListenerSpecFromMethod(method: Method): ListenerSpec
}