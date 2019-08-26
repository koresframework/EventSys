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
package com.github.koresframework.eventsys.reflect

import com.github.jonathanxd.kores.base.KoresAnnotation
import com.github.jonathanxd.kores.base.KoresParameter
import com.github.jonathanxd.kores.base.MethodDeclaration
import com.github.jonathanxd.kores.base.TypeDeclaration
import com.github.jonathanxd.kores.type.KoresType
import com.github.jonathanxd.kores.type.`is`
import com.github.jonathanxd.kores.util.conversion.koresParameter
import com.github.koresframework.eventsys.util.DeclarationCache
import com.github.koresframework.eventsys.util.DeclaredMethod
import com.github.koresframework.eventsys.util.NameCaching
import com.github.koresframework.eventsys.util.forAllTypes
import java.lang.reflect.Method
import java.lang.reflect.Type

fun findImplementation(jClass: KoresType, method: DeclaredMethod, cache: DeclarationCache): Pair<Type, MethodDeclaration>? {
    val paramTypes = listOf(method.type) + method.methodDeclaration.parameters.map { it.type }
    val retType = method.methodDeclaration.returnType

    val dec = cache[jClass]

    cache.getInnerClasses(jClass).filter { it.simpleName == "DefaultImpls" }.forEach { type ->
        val found = type.methods.find {
            it.name == method.methodDeclaration.name
                    && it.parameters.map { it.type }.`is`(paramTypes)
                    && it.returnType.`is`(retType)
        }

        found?.let {
            return Pair(type, it)
        }
    }

    /*val superclasses = mutableListOf<Class<*>>()

    if (jClass.superclass != null && jClass.superclass != Any::class.java)
        superclasses += jClass.superclass

    superclasses += jClass.interfaces

    superclasses.forEach {
        val find = findImplementation(it, method)

        if (find != null)
            return find
    }*/

    return null
}

fun Method.isEqual(other: Method): Boolean =
    this.name == other.name
            && this.returnType == other.returnType
            && this.parameterTypes.contentEquals(other.parameterTypes)

fun MethodDeclaration.isEqual(other: MethodDeclaration): Boolean =
    this.name == other.name
            && this.returnType.`is`(other.returnType)
            && this.parameters.eq(other.parameters)

fun MethodDeclaration.isEqual(other: Method): Boolean =
    this.name == other.name
            && this.returnType.`is`(other.returnType)
            && this.parameters.eqType(other.genericParameterTypes.toList())

fun List<KoresParameter>.eq(other: List<KoresParameter>): Boolean =
    if (this.size != other.size) false
    else this.withIndex().all { (index, parameter) -> other[index].type.`is`(parameter.type) }

fun List<KoresParameter>.eqType(other: List<Type>): Boolean =
    if (this.size != other.size) false
    else this.withIndex().all { (index, parameter) -> other[index].`is`(parameter.type) }


internal fun getName(base: String, nameCaching: NameCaching): String {

    var base_ = base
    var count = 0

    fun findClass(base: String): Boolean =
        try {
            Class.forName(base)
            true
        } catch (t: Throwable) {
            !nameCaching.cache(base)
        }

    while (findClass(base_)) {
        base_ = "$base\$$count"
        count++
    }

    return base_
}

fun <T : Annotation> Class<*>.getAllAnnotationsOfType(type: Class<out T>): List<T> {
    val list = mutableListOf<T>()
    this.addAllAnnotationsOfTypeTo(list, type)
    return list
}

private fun <T : Annotation> Class<*>.addAllAnnotationsOfTypeTo(
    destination: MutableList<T>,
    type: Class<out T>
) {
    destination += this.getDeclaredAnnotationsByType(type)

    if (this.superclass != null && this.superclass != Any::class.java)
        this.superclass.addAllAnnotationsOfTypeTo(destination, type)

    this.interfaces.forEach {
        it.addAllAnnotationsOfTypeTo(destination, type)
    }
}

fun TypeDeclaration.getAllKoresAnnotationsOfType(type: Type): List<KoresAnnotation> {
    val list = mutableListOf<KoresAnnotation>()
    this.addAllKoresAnnotationsOfTypeTo(list, type)
    return list
}

private fun TypeDeclaration.addAllKoresAnnotationsOfTypeTo(
    destination: MutableList<KoresAnnotation>,
    type: Type
) {

    this.forAllTypes {
        destination += this.annotations.filter { it.type.`is`(type) }
    }
    /*destination += this.annotations.filter { it.type.`is`(type) }

    if (this.superclass != null && this.superclass != Any::class.java)
        this.superclass.addAllAnnotationsOfTypeTo(destination, type)

    this.interfaces.forEach {
        it.addAllAnnotationsOfTypeTo(destination, type)
    }*/
}