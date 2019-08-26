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
package com.github.koresframework.eventsys.gen.event

import com.github.jonathanxd.kores.generic.GenericSignature
import com.github.jonathanxd.kores.type.GenericType
import java.lang.reflect.Type

/**
 * Information about property to generate.
 */
data class PropertyInfo @JvmOverloads constructor(
    val declaringType: Type,
    val propertyName: String,
    val getterName: String? = null,
    val setterName: String? = null,
    val type: Type,
    val isNotNull: Boolean,
    val validator: Type? = null,
    val propertyType: PropertyType,
    val inferredType: Type = type
) {
    fun hasGetter() = this.getterName != null
    fun hasSetter() = this.setterName != null
    fun isMutable() = this.setterName != null
}

data class PropertyType(
    val type: GenericType,
    val definedParams: GenericSignature
)