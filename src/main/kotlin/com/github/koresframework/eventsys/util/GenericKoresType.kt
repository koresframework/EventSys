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

import com.github.jonathanxd.iutils.type.TypeInfo
import com.github.jonathanxd.kores.Instruction
import com.github.jonathanxd.kores.Types
import com.github.jonathanxd.kores.base.TypeSpec
import com.github.jonathanxd.kores.factory.*
import com.github.jonathanxd.kores.literal.Literals
import com.github.jonathanxd.kores.type.*
import java.lang.reflect.Type

fun GenericType.toTypeInfo(): TypeInfo<*> =
    TypeInfo.builderOf<Any?>(this.type)
        .also { bd ->
            this.bounds.forEach {
                if (it.type is GenericType) {
                    bd.of((it.type as GenericType).toTypeInfo())
                } else {
                    bd.of(it.type.type)
                }
            }
        }
        .build()

fun Type.toStructure(): Instruction =
    this.koresType.let {
        when (it) {
            is GenericType -> it.toStructure()
            else -> Literals.CLASS(it)
        }
    }

fun GenericType.toStructure(): Instruction =
    when {
        this.isWildcard -> typeOf<Generic>().invokeStatic(
            "wildcard",
            typeSpec(typeOf<Generic>()),
            emptyList()
        )
        this.isType -> typeOf<Generic>().invokeStatic(
            "type",
            typeSpec(typeOf<Generic>(), typeOf<Type>()),
            listOf(this.resolvedType.toStructure())
        )
        else -> typeOf<Generic>().invokeStatic(
            "type",
            typeSpec(typeOf<Generic>(), Types.STRING),
            listOf(Literals.STRING(this.name))
        )
    }.let {
        if (this.bounds.isEmpty()) it
        else this.bounds.toStructure(it)
    }

fun Array<GenericType.Bound>.toStructure(recv: Instruction): Instruction =
    recv.invokeVirtual(
        localization = typeOf<Generic>(),
        name = "of",
        spec = typeSpec(typeOf<Generic>(), genericTypeOf<Array<GenericType.Bound>>()),
        arguments = listOf(
            createArray(
                genericTypeOf<Array<GenericType.Bound>>(),
                listOf(Literals.INT(this.size)),
                this.map { it.toStructure() })
        )
    )

fun GenericType.Bound.toStructure(): Instruction =
    this::class.java.invokeConstructor(
        constructorTypeSpec(typeOf<KoresType>()),
        listOf(this.type.toStructure())
    )

fun GenericType.isGenericAssignableFrom(other: Type): Boolean {
    if (this.`is`(other))
        return true

    val otherSubTypeInfos = other.asGeneric.bounds

    if (otherSubTypeInfos.size == 1)
        return this.isAssignableFrom(otherSubTypeInfos[0].type)

    for (otherSubTypeInfo in otherSubTypeInfos) {
        if (this.isAssignableFrom(otherSubTypeInfo.type.asGeneric)) {
            return true
        }
    }

    return false
}


fun GenericType.toArg(): Instruction {
    val insn = when {
        this.isType -> Generic::class.java.invokeStatic(
            "type",
            TypeSpec(Generic::class.java, listOf(Type::class.java)),
            listOf(this.resolvedType.toArg())
        )
        this.isWildcard -> Generic::class.java.invokeStatic(
            "wildcard",
            TypeSpec(Generic::class.java),
            emptyList()
        )
        else -> Generic::class.java.invokeStatic(
            "type",
            TypeSpec(Generic::class.java, listOf(Types.STRING)),
            listOf(Literals.STRING(this.name))
        )
    }

    return if (this.bounds.isEmpty()) {
        insn
    } else {
        val args = this.bounds.toArgs()
        val arrType = GenericType.Bound::class.java.koresType.toArray(1)

        invokeVirtual(
            Generic::class.java,
            insn,
            "of",
            TypeSpec(Generic::class.java, listOf(arrType)),
            listOf(createArray(arrType, listOf(Literals.INT(args.size)), args))
        )
    }
}

fun Array<out GenericType.Bound>.toArgs(): List<Instruction> =
    this.map { it.toArg() }

fun GenericType.Bound.toArg(): Instruction =
    this::class.java.invokeConstructor(
        constructorTypeSpec(KoresType::class.java),
        listOf(this.type.toArg())
    )


fun Instruction.callGetKoresType(): Instruction =
    JavaCodePartUtil.callGetKoresType(this)

fun KoresType.toArg(): Instruction =
    when {
        this is LoadedKoresType<*> -> Literals.CLASS(this.loadedType.koresType).callGetKoresType()
        this is GenericType -> this.toArg()
        else -> Literals.CLASS(this).callGetKoresType()
    }
