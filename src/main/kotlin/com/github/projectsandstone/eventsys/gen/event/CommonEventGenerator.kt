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
package com.github.projectsandstone.eventsys.gen.event

import com.github.jonathanxd.iutils.map.ConcurrentListMap
import com.github.jonathanxd.iutils.option.Options
import com.github.jonathanxd.iutils.type.TypeInfo
import com.github.projectsandstone.eventsys.event.Event
import com.github.projectsandstone.eventsys.event.EventListener
import com.github.projectsandstone.eventsys.event.ListenerSpec
import com.github.projectsandstone.eventsys.extension.ExtensionSpecification
import com.github.projectsandstone.eventsys.gen.check.CheckHandler
import com.github.projectsandstone.eventsys.gen.check.DefaultCheckHandler
import com.github.projectsandstone.eventsys.logging.LoggerInterface
import com.github.projectsandstone.eventsys.util.ESysExecutor
import java.lang.reflect.Method
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.function.Supplier

class CommonEventGenerator(override val logger: LoggerInterface) : EventGenerator {

    private val factoryImplCache = ConcurrentHashMap<Class<*>, Any>()
    private val eventImplCache = ConcurrentHashMap<EventClass<*>, Class<*>>()
    private val listenerImplCache = ConcurrentHashMap<Method, EventListener<Event>>()

    private val extensionMap =
        ConcurrentListMap<Class<*>, ExtensionSpecification>(ConcurrentHashMap())

    // Not synchronized
    override val options: Options = Options(ConcurrentHashMap())
    override var checkHandler: CheckHandler = DefaultCheckHandler()

    // Executor
    private val executor = ESysExecutor(this.options, Executors.newCachedThreadPool())

    override fun <T : Any> createFactoryAsync(factoryClass: Class<T>): CompletableFuture<out T> =
        CompletableFuture.supplyAsync(
            Supplier<T> { this.createFactory(factoryClass) },
            this.executor
        )


    override fun <T : Event> createEventClassAsync(
        type: TypeInfo<T>,
        additionalProperties: List<PropertyInfo>,
        extensions: List<ExtensionSpecification>
    ): CompletableFuture<Class<out T>> =
        CompletableFuture.supplyAsync(Supplier<Class<out T>> {
            this.createEventClass(type, additionalProperties, extensions)
        }, this.executor)

    override fun createMethodListenerAsync(
        owner: Any,
        method: Method,
        instance: Any?,
        listenerSpec: ListenerSpec
    ): CompletableFuture<EventListener<Event>> =
        CompletableFuture.supplyAsync(Supplier<EventListener<Event>> {
            this.createMethodListener(owner, method, instance, listenerSpec)
        }, this.executor)

    override fun registerExtension(base: Class<*>, extensionSpecification: ExtensionSpecification) {

        if (this.extensionMap[base]?.contains(extensionSpecification) == true)
            return

        this.extensionMap.putToList(base, extensionSpecification)
    }

    override fun <T : Event> registerEventImplementation(
        eventClassSpecification: EventClassSpecification<T>,
        implementation: Class<out T>
    ) {
        val evtClass = EventClass(
            eventClassSpecification.typeInfo,
            eventClassSpecification.additionalProperties,
            emptyList(),
            eventClassSpecification.extensions
        )

        this.eventImplCache[evtClass] = implementation
    }

    override fun <T : Any> createFactory(factoryClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return this.factoryImplCache.computeIfAbsent(factoryClass) {
            EventFactoryClassGenerator.create(this, factoryClass, this.logger)
        } as T

    }

    override fun <T : Event> createEventClass(
        type: TypeInfo<T>,
        additionalProperties: List<PropertyInfo>,
        extensions: List<ExtensionSpecification>
    ): Class<out T> {

        val eventClass = EventClass(
            type,
            additionalProperties,
            this.extensionMap[type.typeClass] ?: emptyList(),
            extensions
        )

        cleanup(eventClass)

        @Suppress("UNCHECKED_CAST")
        return this.eventImplCache.computeIfAbsent(eventClass) {
            EventClassGenerator.genImplementation(eventClass.spec, this)
        } as Class<T>
    }

    /**
     * Removes all old event implementation classes that does not have same extensions as current extensions.
     */
    private fun <T> cleanup(eventClass: EventClass<T>) {
        synchronized(this.eventImplCache) {
            this.eventImplCache.keys.toSet()
                .filter { it.currExts != eventClass.currExts && it.userExts == eventClass.userExts }
                .forEach { this.eventImplCache.remove(it) }
        }
    }

    override fun createMethodListener(
        owner: Any,
        method: Method,
        instance: Any?,
        listenerSpec: ListenerSpec
    ): EventListener<Event> {
        return this.listenerImplCache.computeIfAbsent(method) {
            MethodListenerGenerator.create(owner, method, instance, listenerSpec)
        }
    }

    private data class EventClass<T>(
        val typeInfo: TypeInfo<T>,
        val additionalProperties: List<PropertyInfo>,
        val currExts: List<ExtensionSpecification>,
        val userExts: List<ExtensionSpecification>
    ) {
        val spec: EventClassSpecification<T> =
            EventClassSpecification(typeInfo, additionalProperties, currExts + userExts)
    }

}