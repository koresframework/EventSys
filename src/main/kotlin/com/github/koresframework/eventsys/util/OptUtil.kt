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
package com.github.koresframework.eventsys.util

import com.github.jonathanxd.kores.Instruction
import com.github.jonathanxd.kores.factory.invokeStatic
import com.github.jonathanxd.kores.factory.typeSpec
import com.github.jonathanxd.iutils.opt.Opt
import com.github.jonathanxd.iutils.opt.OptObject
import com.github.jonathanxd.iutils.opt.specialized.*
import com.github.jonathanxd.kores.type.concreteType
import com.github.jonathanxd.kores.type.koresType
import java.lang.reflect.Type

fun Type.createNoneRuntime(): Any =
        when (this) { // class.simpleName.capitalize()
            OptBoolean::class.java -> OptBoolean.none()
            OptChar::class.java -> OptChar.none()
            OptByte::class.java -> OptByte.none()
            OptShort::class.java -> OptShort.none()
            OptInt::class.java -> OptInt.none()
            OptFloat::class.java -> OptFloat.none()
            OptLong::class.java -> OptLong.none()
            OptDouble::class.java -> OptDouble.none()
            OptObject::class.java -> Opt.none<Any?>()
            else -> throw IllegalArgumentException("Cannot get primitive type of opt '$this'.")
        }

fun Type.createSomeRuntime(value: Any?): Any =
        when (this) { // class.simpleName.capitalize()
            OptBoolean::class.java -> OptBoolean.some(value as Boolean)
            OptChar::class.java -> OptChar.some(value as Char)
            OptByte::class.java -> OptByte.some(value as Byte)
            OptShort::class.java -> OptShort.some(value as Short)
            OptInt::class.java -> OptInt.some(value as Int)
            OptFloat::class.java -> OptFloat.some(value as Float)
            OptLong::class.java -> OptLong.some(value as Long)
            OptDouble::class.java -> OptDouble.some(value as Double)
            OptObject::class.java -> Opt.someNullable(value)
            else -> throw IllegalArgumentException("Cannot get primitive type of opt '$this'.")
        }

fun Type.isOptType() = when (this.koresType.concreteType) {
    OptBoolean::class.java,
    OptChar::class.java,
    OptByte::class.java,
    OptShort::class.java,
    OptInt::class.java,
    OptFloat::class.java,
    OptLong::class.java,
    OptDouble::class.java,
    OptObject::class.java -> true
    else -> false
}

fun Type.primitiveType() = when (this.koresType.concreteType) {
    OptBoolean::class.java -> java.lang.Boolean.TYPE
    OptChar::class.java -> java.lang.Character.TYPE
    OptByte::class.java -> java.lang.Byte.TYPE
    OptShort::class.java -> java.lang.Short.TYPE
    OptInt::class.java -> java.lang.Integer.TYPE
    OptFloat::class.java -> java.lang.Float.TYPE
    OptLong::class.java -> java.lang.Long.TYPE
    OptDouble::class.java -> java.lang.Double.TYPE
    OptObject::class.java -> Any::class.java
    else -> throw IllegalArgumentException("Cannot get primitive type of opt '$this'.")
}

fun Type.createNone(): Instruction =
        Opt::class.java.invokeStatic(this.noneName(), typeSpec(this), emptyList())

fun Type.createSome(receiver: Instruction): Instruction =
        Opt::class.java.invokeStatic(this.someName(), typeSpec(this, this.primitiveType()), listOf(receiver))

fun Type.someName() = when (this.koresType.concreteType) { // class.simpleName.capitalize()
    OptBoolean::class.java -> "someBoolean"
    OptChar::class.java -> "someChar"
    OptByte::class.java -> "someByte"
    OptShort::class.java -> "someShort"
    OptInt::class.java -> "someInt"
    OptFloat::class.java -> "someFloat"
    OptLong::class.java -> "someLong"
    OptDouble::class.java -> "someDouble"
    OptObject::class.java -> "some"
    else -> throw IllegalArgumentException("Cannot get primitive type of opt '$this'.")
}

fun Type.noneName() = when (this.koresType.concreteType) { // class.simpleName.capitalize()
    OptBoolean::class.java -> "noneBoolean"
    OptChar::class.java -> "noneChar"
    OptByte::class.java -> "noneByte"
    OptShort::class.java -> "noneShort"
    OptInt::class.java -> "noneInt"
    OptFloat::class.java -> "noneFloat"
    OptLong::class.java -> "noneLong"
    OptDouble::class.java -> "noneDouble"
    OptObject::class.java -> "none"
    else -> throw IllegalArgumentException("Cannot get primitive type of opt '$this'.")
}
