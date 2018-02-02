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

import com.github.jonathanxd.kores.util.conversion.kotlinParameters
import com.github.jonathanxd.kores.util.isKotlin
import com.github.jonathanxd.iutils.type.TypeInfo
import com.github.jonathanxd.iutils.type.TypeParameterProvider
import com.github.jonathanxd.iutils.type.TypeUtil
import com.github.projectsandstone.eventsys.event.annotation.Filter
import com.github.projectsandstone.eventsys.event.annotation.Listener
import com.github.projectsandstone.eventsys.event.annotation.Name
import com.github.projectsandstone.eventsys.event.annotation.OptionalProperty
import com.github.projectsandstone.eventsys.util.hasEventFirstArg
import com.github.projectsandstone.eventsys.util.isOptType
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
        val eventType: TypeInfo<*>,
        val firstIsEvent: Boolean,
        val ignoreCancelled: Boolean = false,
        val priority: EventPriority = EventPriority.NORMAL,
        val parameters: List<LParameter>,
        val channel: Int) {

    data class LParameter internal constructor(val name: String,
                                               val annotations: List<Annotation>,
                                               val type: TypeInfo<*>,
                                               val isOptional: Boolean,
                                               val optType: Type?,
                                               val shouldLookup: Boolean)

    companion object {

        /**
         * Create listener specification from [method] annotated with [Listener].
         */
        fun fromMethod(method: Method): ListenerSpec {

            val firstIsEvent =
                    method.getDeclaredAnnotation(Filter::class.java).hasEventFirstArg()

            val evType =
                    if (!firstIsEvent)
                        method.getDeclaredAnnotation(Filter::class.java)?.value?.singleOrNull()?.java?.let {
                            if (it.superclass == TypeParameterProvider::class.java)
                                (it.genericSuperclass as? ParameterizedType)?.actualTypeArguments
                                        ?.firstOrNull()?.let { TypeUtil.toTypeInfo(it) } ?: TypeInfo.of(it)
                            else TypeInfo.of(it)
                        } ?: TypeInfo.of(Event::class.java)
                    else TypeUtil.toTypeInfo(method.genericParameterTypes[0])

            val listenerAnnotation = method.getDeclaredAnnotation(Listener::class.java)

            val isKotlin = method.declaringClass.isKotlin

            val ktParameters = if (isKotlin) method.kotlinParameters else null

            val namedParameters = method.parameters.mapIndexed { i, it ->

                val typeIsOptional = it.type.isOptType()
                val isOptional = typeIsOptional || it.isAnnotationPresent(OptionalProperty::class.java)

                val typeInfo = TypeUtil.toTypeInfo(it.parameterizedType)
                val parameterType =
                        if (typeIsOptional) typeInfo.getTypeParameter(0)
                        else typeInfo

                val name: String? = it.getDeclaredAnnotation(Name::class.java)?.value
                        ?: (if (it.isNamePresent) it.name else null)
                        ?: ktParameters?.get(i)?.name

                return@mapIndexed LParameter(name ?: it.name,
                        it.annotations.toList(),
                        parameterType,
                        isOptional,
                        if (typeIsOptional) typeInfo.typeClass else null,
                        evType.typeParameters.isNotEmpty()
                )

            }.toList()

            return ListenerSpec(eventType = evType,
                    firstIsEvent = firstIsEvent,
                    ignoreCancelled = listenerAnnotation.ignoreCancelled,
                    priority = listenerAnnotation.priority,
                    parameters = namedParameters,
                    channel = listenerAnnotation.channel)
        }

    }
}

