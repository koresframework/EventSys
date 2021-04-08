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
package com.github.koresframework.eventsys.gen.event

import com.github.jonathanxd.iutils.`object`.Tristate
import com.github.jonathanxd.iutils.map.ConcurrentListMap
import com.github.jonathanxd.iutils.option.Options
import com.github.jonathanxd.kores.base.ClassDeclaration
import com.github.jonathanxd.kores.base.MethodDeclaration
import com.github.jonathanxd.kores.type.`is`
import com.github.jonathanxd.kores.type.bindedDefaultResolver
import com.github.jonathanxd.kores.type.canonicalName
import com.github.koresframework.eventsys.context.EnvironmentContext
import com.github.koresframework.eventsys.event.Event
import com.github.koresframework.eventsys.event.EventListener
import com.github.koresframework.eventsys.event.ListenerSpec
import com.github.koresframework.eventsys.extension.ExtensionSpecification
import com.github.koresframework.eventsys.gen.CommonGenerationEnvironment
import com.github.koresframework.eventsys.gen.GenerationEnvironment
import com.github.koresframework.eventsys.gen.ResolvableDeclaration
import com.github.koresframework.eventsys.gen.check.CheckHandler
import com.github.koresframework.eventsys.gen.check.DefaultCheckHandler
import com.github.koresframework.eventsys.logging.LoggerInterface
import com.github.koresframework.eventsys.reflect.isEqual
import com.github.koresframework.eventsys.util.ESysExecutor
import com.github.koresframework.eventsys.util.EventImplementationGenerationFailure
import com.github.koresframework.eventsys.util.isPublic
import java.lang.IllegalArgumentException
import java.lang.reflect.Method
import java.lang.reflect.Type
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.function.Supplier

class CommonEventGenerator @JvmOverloads constructor(
        override val logger: LoggerInterface,
        override val generationEnvironment: GenerationEnvironment = CommonGenerationEnvironment()
) : EventGenerator {

    private val factoryImplCache = ConcurrentHashMap<Type, ResolvableDeclaration<*>>()
    private val eventImplCache = ConcurrentHashMap<EventClass, ResolvableDeclaration<Class<*>>>()
    private val listenerImplCache =
            ConcurrentHashMap<MethodDeclaration, ResolvableDeclaration<Class<out EventListener<Event>>>>()

    private val extensionMap =
            ConcurrentListMap<Type, ExtensionSpecification>(ConcurrentHashMap())

    // Not synchronized
    override val options: Options = Options(ConcurrentHashMap())
    override var checkHandler: CheckHandler = DefaultCheckHandler()

    // Executor
    private val executor = ESysExecutor(this.options, Executors.newCachedThreadPool())

    override fun <T : Any> createFactoryAsync(factoryType: Type,
                                              ctx: EnvironmentContext): CompletableFuture<ResolvableDeclaration<T>> =
            CompletableFuture.supplyAsync(
                    Supplier<ResolvableDeclaration<T>> { this.createFactory(factoryType, ctx) },
                    this.executor
            )


    override fun <T : Event> createEventClassAsync(
            type: Type,
            additionalProperties: List<PropertyInfo>,
            extensions: List<ExtensionSpecification>,
            ctx: EnvironmentContext
    ): CompletableFuture<ResolvableDeclaration<Class<out T>>> =
            CompletableFuture.supplyAsync(Supplier<ResolvableDeclaration<Class<out T>>> {
                this.createEventClass(type, additionalProperties, extensions, ctx)
            }, this.executor)

    override fun createMethodListenerAsync(
            listenerClass: Type,
            method: Method,
            instance: Any?,
            listenerSpec: ListenerSpec,
            ctx: EnvironmentContext
    ): CompletableFuture<ResolvableDeclaration<EventListener<Event>>> =
            CompletableFuture.supplyAsync(Supplier<ResolvableDeclaration<EventListener<Event>>> {
                this.createMethodListener(listenerClass, method, instance, listenerSpec, ctx)
            }, this.executor)

    override fun createMethodListenerAsync(
            listenerClass: Type,
            method: MethodDeclaration,
            instance: Any?,
            listenerSpec: ListenerSpec,
            ctx: EnvironmentContext
    ): CompletableFuture<ResolvableDeclaration<EventListener<Event>>> =
            CompletableFuture.supplyAsync(Supplier<ResolvableDeclaration<EventListener<Event>>> {
                this.createMethodListener(listenerClass, method, instance, listenerSpec, ctx)
            }, this.executor)

    override fun createMethodListenerAsync(
            listenerClass: Type,
            method: MethodDeclaration,
            listenerSpec: ListenerSpec,
            ctx: EnvironmentContext
    ): CompletableFuture<ResolvableDeclaration<Class<out EventListener<Event>>>> =
            CompletableFuture.supplyAsync(Supplier<ResolvableDeclaration<Class<out EventListener<Event>>>> {
                this.createMethodListener(listenerClass, method, listenerSpec, ctx)
            }, this.executor)

    override fun registerExtension(
            base: Type,
            extensionSpecification: ExtensionSpecification
    ) {
        if (this.extensionMap[base]?.contains(extensionSpecification) == true)
            return

        this.extensionMap.putToList(base, extensionSpecification)
    }

    override fun <T : Event> registerEventImplementation(
            eventClassSpecification: EventClassSpecification,
            implementation: Class<out T>
    ) {
        val evtClass = EventClass(
                eventClassSpecification.type,
                eventClassSpecification.additionalProperties,
                emptyList(),
                eventClassSpecification.extensions
        )

        this.eventImplCache[evtClass] = ResolvableDeclaration(
                classDeclaration = this.generationEnvironment.declarationCache[implementation] as ClassDeclaration,
                resolver = { implementation })
    }

    override fun <T : Any> createFactory(factoryType: Type,
                                         ctx: EnvironmentContext): ResolvableDeclaration<T> {
        @Suppress("UNCHECKED_CAST")
        return this.factoryImplCache.computeIfAbsent(factoryType) {
            EventFactoryClassGenerator.create<T>(
                    this,
                    factoryType,
                    this.logger,
                    this.generationEnvironment,
                    ctx
            )
        } as ResolvableDeclaration<T>

    }

    override fun <T : Event> createEventClass(
            type: Type,
            additionalProperties: List<PropertyInfo>,
            extensions: List<ExtensionSpecification>,
            ctx: EnvironmentContext
    ): ResolvableDeclaration<Class<out T>> {

        if (type.isPublic() == Tristate.FALSE) {
            throw EventImplementationGenerationFailure("The provided '${type.canonicalName}' for event implementation generation must be public.")
        }

        val currExts =
                (this.extensionMap[type]
                        ?: this.extensionMap.entries
                                .firstOrNull { (k, _) -> k.`is`(type) }?.value).orEmpty()

        val eventClass = EventClass(
                type,
                additionalProperties,
                currExts,
                extensions
        )

        cleanup(eventClass)

        @Suppress("UNCHECKED_CAST")
        return this.eventImplCache.computeIfAbsent(eventClass) {
            EventClassGenerator.genImplementation<T>(
                    eventClass.spec,
                    this,
                    this.generationEnvironment,
                    ctx
            )
        } as ResolvableDeclaration<Class<out T>>
    }

    /**
     * Removes all old event implementation classes that does not have same extensions as current extensions.
     */
    private fun cleanup(eventClass: EventClass) {
        synchronized(this.eventImplCache) {
            this.eventImplCache.keys.toSet()
                    .filter { it.currExts != eventClass.currExts && it.userExts == eventClass.userExts }
                    .forEach { this.eventImplCache.remove(it) }
        }
    }

    override fun createMethodListener(
            listenerClass: Type,
            method: MethodDeclaration,
            listenerSpec: ListenerSpec,
            ctx: EnvironmentContext
    ): ResolvableDeclaration<Class<out EventListener<Event>>> =
            this.listenerImplCache.computeIfAbsent(method) {
                MethodListenerGenerator.createClass(listenerClass, it, listenerSpec)
            }

    override fun createMethodListener(
            listenerClass: Type,
            method: MethodDeclaration,
            instance: Any?,
            listenerSpec: ListenerSpec,
            ctx: EnvironmentContext
    ): ResolvableDeclaration<EventListener<Event>> =
            createMethodListener(listenerClass, method, listenerSpec, ctx).let {
                MethodListenerGenerator.create(it, method, instance)
            }

    override fun createMethodListener(
            listenerClass: Type,
            method: Method,
            instance: Any?,
            listenerSpec: ListenerSpec,
            ctx: EnvironmentContext
    ): ResolvableDeclaration<EventListener<Event>> =
            this.generationEnvironment.declarationCache[listenerClass].methods.first { it.isEqual(method) }.let {
                createMethodListener(listenerClass, it, instance, listenerSpec, ctx)
            }

    override fun createListenerSpecFromMethod(method: Method): ListenerSpec =
            this.createListenerSpecFromMethod(this.generationEnvironment
                    .declarationCache[method.declaringClass]
                    .methods
                    .first {
                        it.isEqual(
                                method
                        )
                    })

    override fun createListenerSpecFromMethod(method: MethodDeclaration): ListenerSpec =
            ListenerSpec.fromMethodDeclaration(method)

    private data class EventClass(
            val typeInfo: Type,
            val additionalProperties: List<PropertyInfo>,
            val currExts: List<ExtensionSpecification>,
            val userExts: List<ExtensionSpecification>
    ) {
        val spec: EventClassSpecification =
                EventClassSpecification(typeInfo, additionalProperties, currExts + userExts)
    }

}