/*
 *      EventSys - Event implementation generator written on top of CodeAPI
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
package com.github.projectsandstone.eventsys.util.mh

import com.github.jonathanxd.iutils.type.TypeInfo
import com.github.projectsandstone.eventsys.event.Event
import com.github.projectsandstone.eventsys.event.EventListener
import com.github.projectsandstone.eventsys.event.EventPriority
import com.github.projectsandstone.eventsys.event.ListenerSpec
import com.github.projectsandstone.eventsys.event.property.GetterProperty
import com.github.projectsandstone.eventsys.event.property.Property
import com.github.projectsandstone.eventsys.util.createNoneRuntime
import com.github.projectsandstone.eventsys.util.createSomeRuntime
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

    init {
        if (method.genericParameterTypes.isEmpty()) {
            throw IllegalArgumentException("Invalid Method: '$method'. (No Parameters)")
        }
    }

    override fun onEvent(event: Event, dispatcher: Any) {

        // Process [parameters]
        if (listenerSpec.firstIsEvent && listenerSpec.parameters.size == 1) {
            method.invokeWithArguments(event)
        } else if (this.listenerSpec.parameters.isNotEmpty()) {
            val args: MutableList<Any?> = mutableListOf()

            if (listenerSpec.firstIsEvent)
                args += event

            this.listenerSpec.parameters.forEachIndexed { i, spec ->
                if (!this.listenerSpec.firstIsEvent || i > 0) {
                    val name = spec.name
                    val typeInfo = spec.type

                    val type =
                            if (typeInfo.typeClass == Property::class.java && typeInfo.typeParameters.size == 1)
                                typeInfo.getTypeParameter(0).typeClass
                            else
                                typeInfo.typeClass

                    val found =
                            if (spec.shouldLookup) event.lookup(type, name) as? GetterProperty<*>
                            else event.getGetterProperty(type, name)

                    if (found == null && spec.isOptional) {
                        if (spec.optType == null) args.add(null)
                        else args.add(spec.optType.createNoneRuntime())
                    } else if (found == null) {
                        return
                    } else {
                        if (spec.optType == null) args.add(found.getValue())
                        else args.add(spec.optType.createSomeRuntime(found.getValue()))
                    }
                }
            }

            method.invokeWithArguments(args)
        }
    }

    override val priority: EventPriority
        get() = this.listenerSpec.priority

    override val channel: Int
        get() = this.listenerSpec.channel

    override val ignoreCancelled: Boolean
        get() = this.listenerSpec.ignoreCancelled


    companion object {
        val lookup = MethodHandles.publicLookup()
    }
}