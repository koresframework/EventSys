/*
 *      EventSys - Event implementation generator written on top of Kores
 *
 *         The MIT License (MIT)
 *
 *      Copyright (c) 2021 ProjectSandstone <https://github.com/ProjectSandstone/EventSys>
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
package com.github.koresframework.eventsys.util

import com.github.jonathanxd.iutils.`object`.Tristate
import com.koresframework.kores.base.Annotable
import com.koresframework.kores.type.`is`
import com.koresframework.kores.type.bindedDefaultResolver
import java.lang.reflect.Modifier
import java.lang.reflect.Type
import javax.lang.model.element.TypeElement

fun Annotable.isAnnotationPresent(type: Type) =
        this.annotations.any { it.type.`is`(type) }

fun Annotable.getDeclaredAnnotation(type: Type) =
        this.annotations.firstOrNull { it.type.`is`(type) }

fun Type.isPublic(): Tristate =
        ((this.bindedDefaultResolver.resolve().rightOr(null) as? Class<*>)?.modifiers?.let { Modifier.isPublic(it) }
                ?: (this.bindedDefaultResolver.resolve().rightOr(null) as? TypeElement)?.modifiers?.any { it == javax.lang.model.element.Modifier.PUBLIC }
                ?: (this.bindedDefaultResolver.resolveTypeDeclaration().rightOr(null)?.isPublic)).let {
            when (it) {
                true -> Tristate.TRUE
                false -> Tristate.FALSE
                else -> Tristate.UNKNOWN
            }
        }
