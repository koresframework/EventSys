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
package com.github.projectsandstone.eventsys.util

import com.github.jonathanxd.codeapi.CodeInstruction
import com.github.jonathanxd.codeapi.factory.invokeStatic
import com.github.jonathanxd.codeapi.factory.typeSpec
import com.github.jonathanxd.iutils.opt.Opt
import com.github.jonathanxd.iutils.opt.specialized.*
import java.lang.reflect.Type

fun Type.createNoneRuntime(): Any =
        when (this) { // class.simpleName.capitalize()
            OptBoolean::class.java -> Opt.noneBoolean()
            OptChar::class.java -> Opt.noneChar()
            OptByte::class.java -> Opt.noneByte()
            OptShort::class.java -> Opt.noneShort()
            OptInt::class.java -> Opt.noneInt()
            OptFloat::class.java -> Opt.noneFloat()
            OptLong::class.java -> Opt.noneLong()
            OptDouble::class.java -> Opt.noneDouble()
            OptObject::class.java -> Opt.none<Any?>()
            else -> throw IllegalArgumentException("Cannot get primitive type of opt '$this'.")
        }

fun Type.createSomeRuntime(value: Any?): Any =
        when (this) { // class.simpleName.capitalize()
            OptBoolean::class.java -> Opt.someBoolean(value as Boolean)
            OptChar::class.java -> Opt.someChar(value as Char)
            OptByte::class.java -> Opt.someByte(value as Byte)
            OptShort::class.java -> Opt.someShort(value as Short)
            OptInt::class.java -> Opt.someInt(value as Int)
            OptFloat::class.java -> Opt.someFloat(value as Float)
            OptLong::class.java -> Opt.someLong(value as Long)
            OptDouble::class.java -> Opt.someDouble(value as Double)
            OptObject::class.java -> Opt.some<Any?>(value)
            else -> throw IllegalArgumentException("Cannot get primitive type of opt '$this'.")
        }

fun Type.isOptType() = when (this) {
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

fun Type.primitiveType() = when (this) {
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

fun Type.createNone(): CodeInstruction =
        Opt::class.java.invokeStatic(this.noneName(), typeSpec(this), emptyList())

fun Type.createSome(receiver: CodeInstruction): CodeInstruction =
        Opt::class.java.invokeStatic(this.someName(), typeSpec(this, this.primitiveType()), listOf(receiver))

fun Type.someName() = when (this) { // class.simpleName.capitalize()
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

fun Type.noneName() = when (this) { // class.simpleName.capitalize()
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
