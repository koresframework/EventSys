/*
 *      EventSys - Event implementation generator written on top of CodeAPI
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
package com.github.projectsandstone.eventsys.event

import com.github.jonathanxd.codeapi.util.conversion.kotlinParameters
import com.github.jonathanxd.iutils.annotation.Named
import com.github.jonathanxd.iutils.type.TypeInfo
import com.github.jonathanxd.iutils.type.TypeUtil
import com.github.projectsandstone.eventsys.event.annotation.Erased
import com.github.projectsandstone.eventsys.event.annotation.Listener
import com.github.projectsandstone.eventsys.event.annotation.Name
import com.github.projectsandstone.eventsys.event.annotation.NullableProperty
import com.github.projectsandstone.eventsys.reflect.isKotlin
import org.jetbrains.annotations.Nullable
import java.lang.reflect.Method
import kotlin.reflect.jvm.kotlinFunction

/**
 * Data Class version of [Listener] annotation.
 */
data class ListenerSpec(
        /**
         * Event type.
         */
        val eventType: TypeInfo<*>,

        /**
         * Ignore this listener if event is cancelled
         */
        val ignoreCancelled: Boolean = false,

        /**
         * Priority of this listener
         */
        val priority: EventPriority = EventPriority.NORMAL,

        /**
         * Method parameters
         */
        val parameters: List<LParameter>,

        /**
         * Phase where this method listen to. Less than zero means all phases.
         *
         * Phases value may vary depending on the event dispatcher.
         *
         * The EventSys supports phase listening,
         * some event dispatchers may dispatch events in different phases, this means that the same
         * event instance can be dispatched multiple times depending on the location where event occurs.
         */
        val phase: Int) {

    data class LParameter internal constructor(val name: String,
                                               val annotations: List<Annotation>,
                                               val type: TypeInfo<*>,
                                               val isNullable: Boolean,
                                               val isErased: Boolean)

    companion object {

        /**
         * Create listener specification from [method] annotated with [Listener].
         */
        fun fromMethod(method: Method): ListenerSpec {

            val listenerAnnotation = method.getDeclaredAnnotation(Listener::class.java)

            val isKotlin = method.declaringClass.isKotlin

            val ktParameters = if(isKotlin) method.kotlinParameters else null

            val namedParameters = method.parameters.mapIndexed { i, it ->

                val isNullable = if (ktParameters != null) ktParameters[i].type.isMarkedNullable else
                    it.isAnnotationPresent(Nullable::class.java)

                val typeInfo = TypeUtil.toTypeInfo(it.parameterizedType)

                val name: String? = it.getDeclaredAnnotation(Name::class.java)?.value ?: ktParameters?.get(i)?.name

                return@mapIndexed LParameter(name ?: it.name,
                        it.annotations.toList(),
                        typeInfo,
                        it.isAnnotationPresent(NullableProperty::class.java) || isNullable,
                        it.isAnnotationPresent(Erased::class.java))

            }.toList()



            return ListenerSpec(eventType = TypeUtil.toTypeInfo(method.genericParameterTypes[0]),
                    ignoreCancelled = listenerAnnotation.ignoreCancelled,
                    priority = listenerAnnotation.priority,
                    parameters = namedParameters,
                    phase = listenerAnnotation.phase)
        }

    }
}

