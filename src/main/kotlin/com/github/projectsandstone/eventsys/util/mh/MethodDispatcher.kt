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
package com.github.projectsandstone.eventsys.util.mh

import com.github.jonathanxd.iutils.annotation.Named
import com.github.jonathanxd.iutils.type.TypeInfo
import com.github.jonathanxd.iutils.type.TypeUtil
import com.github.projectsandstone.eventsys.event.Event
import com.github.projectsandstone.eventsys.event.EventListener
import com.github.projectsandstone.eventsys.event.EventPriority
import com.github.projectsandstone.eventsys.event.ListenerSpec
import com.github.projectsandstone.eventsys.event.annotation.Name
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.reflect.Method

/**
 * Class used to dispatch method listener events.
 *
 * This dispatcher uses Java 7 [MethodHandle] to dispatch the event, the [MethodHandle] is faster
 * than Reflection but is slower than direct invocation.
 *
 * This dispatcher is a bit optimized, but we recommend to use direct invocation.
 */
open class MethodDispatcher(
        /**
         * Listener specification.
         */
        val listenerSpec: ListenerSpec,
        /**
         * Method to invoke
         */
        method: Method,

        /**
         * Instance to call method.
         */
        instance: Any?) : EventListener<Event> {

    @Suppress("UNCHECKED_CAST")
    val eventType: TypeInfo<Event> = this.listenerSpec.eventType as TypeInfo<Event>

    val backingMethod_: MethodHandle by lazy {
        lookup.unreflect(method).bindTo(instance)
    }

    val method: MethodHandle
        get() = backingMethod_

    val parameters: Array<TypeInfo<*>> = method.genericParameterTypes.map { TypeUtil.toTypeInfo(it) }.toTypedArray()

    internal val namedParameters: Array<com.github.jonathanxd.iutils.`object`.Named<TypeInfo<*>>> =
            method.parameters.map {
                val typeInfo = TypeUtil.toTypeInfo(it.parameterizedType)

                val name: String? = it.getDeclaredAnnotation(Named::class.java)?.value
                        ?: it.getDeclaredAnnotation(Name::class.java)?.value

                return@map com.github.jonathanxd.iutils.`object`.Named(name, typeInfo)

            }.toTypedArray()

    init {
        if (parameters.isEmpty()) {
            throw IllegalArgumentException("Invalid Method: '$method'. (No Parameters)")
        }
    }

    override fun onEvent(event: Event, owner: Any) {

        // Process [parameters]
        if (parameters.size == 1) {
            method.invokeWithArguments(event)
        } else if (parameters.size > 1) {
            val args: MutableList<Any?> = mutableListOf(event)

            this.namedParameters.forEachIndexed { i, named ->
                if (i > 0) {
                    val name = named.name
                    val typeInfo = named.value

                    args += event.getProperty(typeInfo.typeClass, name)
                }
            }

            method.invokeWithArguments(args)
        }
    }

    override val priority: EventPriority
        get() = this.listenerSpec.priority

    override val phase: Int
        get() = this.listenerSpec.phase

    override val ignoreCancelled: Boolean
        get() = this.listenerSpec.ignoreCancelled


    companion object {
        val lookup = MethodHandles.publicLookup()
    }
}