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
package com.github.projectsandstone.eventsys.util

import com.github.jonathanxd.iutils.type.TypeInfo
import com.github.jonathanxd.iutils.type.TypeUtil
import com.github.projectsandstone.eventsys.event.Event
import java.lang.reflect.Type

fun <T : Event> getEventTypes(event: T): List<TypeInfo<*>> {
    val jClass = event.javaClass
    val superClass: Pair<Class<*>?, Type> = jClass.superclass to jClass.genericSuperclass
    val interfaces: Array<Pair<Class<*>, Type>> = pairFromArrays(jClass.interfaces, jClass.genericInterfaces)

    val types = mutableListOf<TypeInfo<*>>()

    if (superClass.first != null && Event::class.java.isAssignableFrom(superClass.first)) {
        types += TypeUtil.toTypeInfo(superClass.second)!!
    }

    for ((itf, type) in interfaces) {
        if (Event::class.java.isAssignableFrom(itf)) {
            types += TypeUtil.toTypeInfo(type)!!
        }
    }

    return types
}

/*fun <T : Event> getEventType(event: T): TypeInfo<*> =
        TypeUtil.toTypeInfo(event.javaClass)*/

fun <T : Event> getEventType(event: T): TypeInfo<*> {
    val jClass = event::class.java

    val info = TypeUtil.toTypeInfo(jClass.genericSuperclass)

    if (info.related.isNotEmpty())
        return info

    jClass.genericInterfaces.forEach {
        val info2 = TypeUtil.toTypeInfo(it)

        if (info2.related.isNotEmpty())
            return info2
    }

    return TypeUtil.toTypeInfo(jClass)
}