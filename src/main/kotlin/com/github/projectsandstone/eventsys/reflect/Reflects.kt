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
package com.github.projectsandstone.eventsys.reflect

import com.github.jonathanxd.iutils.description.Description
import com.github.jonathanxd.iutils.description.DescriptionUtil
import com.github.projectsandstone.eventsys.event.annotation.Name
import com.github.projectsandstone.eventsys.event.property.Property
import java.lang.reflect.Method
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.superclasses
import kotlin.reflect.full.valueParameters
import kotlin.reflect.jvm.javaType
import kotlin.reflect.jvm.jvmErasure

val propertyHolderSignatures: List<Description> =
        Property::class.java.methods.map {
            DescriptionUtil.from(it)
        }

val KFunction<*>.parameterNames: List<String>
    get() = this.valueParameters.map {
        it.findAnnotation<Name>()?.value ?: it.name ?: throw IllegalStateException("Cannot determine name of parameter $it")
    }

fun findImplementation(jClass: Class<*>, method: Method): Pair<Class<*>, Method>? {
    val paramTypes = arrayOf(method.declaringClass) + method.parameterTypes
    val retType = method.returnType

    jClass.classes.filter { it.simpleName == "DefaultImpls" }.forEach { type ->
        val found = type.methods.find {
            it.name == method.name
                    && it.parameterTypes.contentEquals(paramTypes)
                    && it.returnType == retType
        }

        found?.let {
            return Pair(type, it)
        }
    }

    val superclasses = mutableListOf<Class<*>>()

    if(jClass.superclass != null && jClass.superclass != Any::class.java)
        superclasses += jClass.superclass

    superclasses += jClass.interfaces

    superclasses.forEach {
        val find = findImplementation(it, method)

        if(find != null)
            return find
    }

    return null
}

fun Method.isEqual(other: Method): Boolean =
        this.name == other.name
                && this.returnType == other.returnType
                && this.parameterTypes.contentEquals(other.parameterTypes)

internal fun getName(base: String): String {

    var base_ = base
    var count = 0

    fun findClass(base: String): Boolean =
            try {
                Class.forName(base)
                true
            } catch (t: Throwable) {
                false
            }

    while (findClass(base_)) {
        if (count == 0)
            base_ += "\$"

        base_ += "$base$count"
        count++
    }

    return base_
}