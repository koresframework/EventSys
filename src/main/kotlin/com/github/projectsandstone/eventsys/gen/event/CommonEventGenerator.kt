/**
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

import com.github.jonathanxd.iutils.map.ListHashMap
import com.github.jonathanxd.iutils.type.TypeInfo
import com.github.projectsandstone.eventsys.event.Event
import com.github.projectsandstone.eventsys.event.EventListener
import com.github.projectsandstone.eventsys.event.ListenerSpec
import java.lang.reflect.Method
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.Future

class CommonEventGenerator : EventGenerator {

    private val extensionMap = ListHashMap<Class<*>, ExtensionSpecification>()
    private val threadPool = Executors.newCachedThreadPool()

    override fun <T : Any> createFactoryAsync(factoryClass: Class<T>): Future<T> =
            threadPool.submit(Callable<T> {
                return@Callable this.createFactory(factoryClass)
            })

    override fun <T : Event> createEventClassAsync(type: TypeInfo<T>, additionalProperties: List<PropertyInfo>, extensions: List<ExtensionSpecification>): Future<Class<T>> =
            threadPool.submit(Callable<Class<T>> {
                return@Callable this.createEventClass(type, additionalProperties)
            })

    override fun createMethodListenerAsync(owner: Any, method: Method, instance: Any?, listenerSpec: ListenerSpec): Future<EventListener<Event>> =
            threadPool.submit(Callable<EventListener<Event>> {
                return@Callable this.createMethodListener(owner, method, instance, listenerSpec)
            })

    override fun registerExtension(base: Class<*>, extensionSpecification: ExtensionSpecification) {

        if(this.extensionMap[base]?.contains(extensionSpecification) ?: false)
            return

        this.extensionMap.putToList(base, extensionSpecification)
    }

    override fun <T : Any> createFactory(factoryClass: Class<T>): T {
        return EventFactoryClassGenerator.create(this, factoryClass)
    }

    override fun <T : Event> createEventClass(type: TypeInfo<T>, additionalProperties: List<PropertyInfo>, extensions: List<ExtensionSpecification>): Class<T> {

        val exts = (this.extensionMap[type.aClass] ?: emptyList()) + extensions

        return EventClassGenerator.genImplementation(EventClassSpecification(
                typeInfo = type,
                additionalProperties = additionalProperties,
                extensions = exts)
        )
    }

    override fun createMethodListener(owner: Any, method: Method, instance: Any?, listenerSpec: ListenerSpec): EventListener<Event> {
        return MethodListenerGenerator.create(owner, method, instance, listenerSpec)
    }

}