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
package com.github.projectsandstone.eventsys.event

import com.github.jonathanxd.iutils.type.TypeParameterProvider
import com.github.jonathanxd.kores.base.KoresAnnotation
import com.github.jonathanxd.kores.base.MethodDeclaration
import com.github.jonathanxd.kores.type.GenericType
import com.github.jonathanxd.kores.type.asGeneric
import com.github.jonathanxd.kores.type.bindedDefaultResolver
import com.github.jonathanxd.kores.util.conversion.koresAnnotation
import com.github.jonathanxd.kores.util.conversion.kotlinParameters
import com.github.jonathanxd.kores.util.isKotlin
import com.github.projectsandstone.eventsys.event.annotation.*
import com.github.projectsandstone.eventsys.util.*
import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

/**
 * Data Class version of [Listener] annotation.
 *
 * @property eventType Event type information.
 * @property firstIsEvent Whether first parameter is event type or not.
 * @property ignoreCancelled Ignore this listener if event is cancelled.
 * @property priority Priority of this listener.
 * @property parameters Method parameters.
 * @property channel Channel where this method listen to. Less than zero means all groups.
 * Channel value may vary depending on the event dispatcher. This same event instance can be dispatched in different channels.
 */
data class ListenerSpec(
    val eventType: Type,
    val firstIsEvent: Boolean,
    val ignoreCancelled: Boolean = false,
    val priority: EventPriority = EventPriority.NORMAL,
    val parameters: List<LParameter>,
    val channel: Int
) {

    data class LParameter internal constructor(
        val name: String,
        val annotations: List<KoresAnnotation>,
        val type: Type,
        val isOptional: Boolean,
        val optType: Type?,
        val shouldLookup: Boolean
    )

    companion object {

        /**
         * Create listener specification from [method] annotated with [Listener].
         */
        @Deprecated(message = "Since EventSys 1.6 you should use fromMethodDeclaration function")
        fun fromMethod(method: Method): ListenerSpec {

            val firstIsEvent =
                method.getDeclaredAnnotation(Filter::class.java).hasEventFirstArg()

            val evType =
                if (!firstIsEvent)
                    method.getDeclaredAnnotation(Filter::class.java)?.value?.singleOrNull()?.java?.let {
                        if (it.superclass == TypeParameterProvider::class.java)
                            (it.genericSuperclass as? ParameterizedType)?.actualTypeArguments
                                ?.firstOrNull()?.asGeneric ?: it
                        else it
                    } ?: Event::class.java
                else method.genericParameterTypes[0].asGeneric

            val listenerAnnotation = method.getDeclaredAnnotation(Listener::class.java)

            val isKotlin = method.declaringClass.isKotlin

            val ktParameters = if (isKotlin) method.kotlinParameters else null

            val namedParameters = method.parameters.mapIndexed { i, it ->

                val typeIsOptional = it.type.isOptType()
                val isOptional =
                    typeIsOptional || it.isAnnotationPresent(OptionalProperty::class.java)

                val generic = it.parameterizedType.asGeneric
                val parameterType =
                    if (typeIsOptional) generic.bounds[0].type
                    else generic

                val name: String? = it.getDeclaredAnnotation(Name::class.java)?.value
                        ?: (if (it.isNamePresent) it.name else null)
                        ?: ktParameters?.get(i)?.name

                return@mapIndexed LParameter(
                    name ?: it.name,
                    it.annotations.koresAnnotation,
                    parameterType,
                    isOptional,
                    if (typeIsOptional) generic else null,
                    evType is GenericType && evType.bounds.isNotEmpty()
                )

            }.toList()

            return ListenerSpec(
                eventType = evType,
                firstIsEvent = firstIsEvent,
                ignoreCancelled = listenerAnnotation.ignoreCancelled,
                priority = listenerAnnotation.priority,
                parameters = namedParameters,
                channel = listenerAnnotation.channel
            )
        }

        /**
         * Create listener specification from [method] annotated with [Listener].
         */
        fun fromMethodDeclaration(method: MethodDeclaration): ListenerSpec {

            val firstIsEvent =
                method.getDeclaredAnnotation(Filter::class.java).hasEventFirstArg()

            val evType: Type =
                if (!firstIsEvent)
                    (method.getDeclaredAnnotation(Filter::class.java)?.filterValue()?.singleOrNull()?.let {
                        val superClass = it.bindedDefaultResolver.getSuperclass()
                        if (superClass.rightOrNull() == TypeParameterProvider::class.java)
                            superClass.right?.asGeneric?.bounds?.get(0)?.type ?: it
                        else it
                    } ?: Event::class.java)
                else method.parameters[0].type.asGeneric

            val listenerAnnotation = method.getDeclaredAnnotation(Listener::class.java)

            val namedParameters = method.parameters.map { it ->

                val typeIsOptional = it.type.isOptType()
                val isOptional =
                    typeIsOptional || it.isAnnotationPresent(OptionalProperty::class.java)

                val generic = it.type.asGeneric
                val parameterType =
                    if (typeIsOptional) generic.bounds[0].type
                    else generic

                val name: String? =
                    it.getDeclaredAnnotation(Name::class.java)?.values?.get("value") as? String
                            ?: it.name

                return@map LParameter(
                    name ?: it.name,
                    it.annotations,
                    parameterType,
                    isOptional,
                    if (typeIsOptional) generic else null,
                    evType is GenericType && evType.bounds.isNotEmpty()
                )

            }.toList()

            return ListenerSpec(
                eventType = evType,
                firstIsEvent = firstIsEvent,
                ignoreCancelled = listenerAnnotation.listenerIgnoreCancelled,
                priority = listenerAnnotation.listenerPriority,
                parameters = namedParameters,
                channel = listenerAnnotation.listenerChannel
            )
        }
    }
}

