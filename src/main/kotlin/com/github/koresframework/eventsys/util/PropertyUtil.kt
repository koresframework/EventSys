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

import com.github.jonathanxd.iutils.function.consumer.BooleanConsumer
import com.github.jonathanxd.iutils.reflection.ClassUtil
import com.github.jonathanxd.iutils.type.Primitive
import com.github.koresframework.eventsys.event.property.GSProperty
import com.github.koresframework.eventsys.event.property.GetterProperty
import com.github.koresframework.eventsys.event.property.Property
import com.github.koresframework.eventsys.event.property.SetterProperty
import com.github.koresframework.eventsys.event.property.primitive.*
import java.lang.reflect.Type
import java.util.function.*

fun Type.getInvokeName() = when (this) {
    java.lang.Byte.TYPE,
    java.lang.Short.TYPE,
    java.lang.Character.TYPE,
    java.lang.Integer.TYPE -> "getAsInt"
    java.lang.Boolean.TYPE -> "getAsBoolean"
    java.lang.Double.TYPE,
    java.lang.Float.TYPE -> "getAsDouble"
    java.lang.Long.TYPE -> "getAsLong"
    else -> "getValue"
}

fun Type.getReifiedType() = when (this) {
    java.lang.Byte.TYPE,
    java.lang.Short.TYPE,
    java.lang.Character.TYPE,
    java.lang.Integer.TYPE -> java.lang.Integer.TYPE
    java.lang.Boolean.TYPE -> java.lang.Boolean.TYPE
    java.lang.Double.TYPE,
    java.lang.Float.TYPE -> java.lang.Double.TYPE
    java.lang.Long.TYPE -> java.lang.Long.TYPE
    else -> Any::class.java
}

fun Type.getGetterType() = when (this) {
    java.lang.Byte.TYPE,
    java.lang.Short.TYPE,
    java.lang.Character.TYPE,
    java.lang.Integer.TYPE -> IntGetterProperty::class.java
    java.lang.Boolean.TYPE -> BooleanGetterProperty::class.java
    java.lang.Double.TYPE,
    java.lang.Float.TYPE -> DoubleGetterProperty::class.java
    java.lang.Long.TYPE -> LongGetterProperty::class.java
    else -> GetterProperty::class.java
}

fun Type.getSetterType() = when (this) {
    java.lang.Byte.TYPE,
    java.lang.Short.TYPE,
    java.lang.Character.TYPE,
    java.lang.Integer.TYPE -> IntSetterProperty::class.java
    java.lang.Boolean.TYPE -> BooleanSetterProperty::class.java
    java.lang.Double.TYPE,
    java.lang.Float.TYPE -> DoubleSetterProperty::class.java
    java.lang.Long.TYPE -> LongSetterProperty::class.java
    else -> GetterProperty::class.java
}

fun Type.getGSetterType() = when (this) {
    java.lang.Byte.TYPE,
    java.lang.Short.TYPE,
    java.lang.Character.TYPE,
    java.lang.Integer.TYPE -> IntGSProperty::class.java
    java.lang.Boolean.TYPE -> BooleanGSProperty::class.java
    java.lang.Double.TYPE,
    java.lang.Float.TYPE -> DoubleGSProperty::class.java
    java.lang.Long.TYPE -> LongGSProperty::class.java
    else -> GSProperty::class.java
}


@Suppress("UNCHECKED_CAST")
fun <R> Property<R>.cast(type: Class<*>, from: Class<*>): Property<R> {
    if (!Primitive.typeEquals(type, from))
        throw IllegalArgumentException("Property '$this' is not compatible with type ${type.canonicalName}")

    if (from.isPrimitive) {
        @Suppress("UNCHECKED_CAST")
        val boxed = Primitive.box(from) as Class<R>
        return when (this) {
            is GSProperty -> GSProperty.Impl(boxed,
                    Supplier { this.getValue() },
                    Consumer { this.setValue(it) })
            is SetterProperty -> SetterProperty.Impl(boxed,
                    Consumer { this.setValue(it) })
            is GetterProperty -> GetterProperty.Impl(boxed,
                    Supplier { this.getValue() })
            else -> this
        }
    } else {
        @Suppress("UNCHECKED_CAST")
        val unBoxed = Primitive.unbox(from) as Class<R>

        return when (this) {
            is GSProperty -> when (unBoxed) {
                java.lang.Byte.TYPE,
                java.lang.Short.TYPE,
                java.lang.Character.TYPE,
                java.lang.Integer.TYPE ->
                    IntGSProperty.Impl(IntSupplier { this.getValue() as Int }, IntConsumer { this.setValue(it as R) })
                java.lang.Boolean.TYPE ->
                    BooleanGSProperty.Impl(BooleanSupplier { this.getValue() as Boolean }, BooleanConsumer { this.setValue(it as R) })
                java.lang.Double.TYPE,
                java.lang.Float.TYPE ->
                    DoubleGSProperty.Impl(DoubleSupplier { this.getValue() as Double }, DoubleConsumer { this.setValue(it as R) })
                java.lang.Long.TYPE ->
                    LongGSProperty.Impl(LongSupplier { this.getValue() as Long }, LongConsumer { this.setValue(it as R) })
                else ->
                    GSProperty.Impl(unBoxed, Supplier { this.getValue() }, Consumer { this.setValue(it) })
            }
            is SetterProperty -> when (unBoxed) {
                java.lang.Byte.TYPE,
                java.lang.Short.TYPE,
                java.lang.Character.TYPE,
                java.lang.Integer.TYPE ->
                    IntSetterProperty.Impl(IntConsumer { this.setValue(it as R) })
                java.lang.Boolean.TYPE ->
                    BooleanSetterProperty.Impl(BooleanConsumer { this.setValue(it as R) })
                java.lang.Double.TYPE,
                java.lang.Float.TYPE ->
                    DoubleSetterProperty.Impl(DoubleConsumer { this.setValue(it as R) })
                java.lang.Long.TYPE ->
                    LongSetterProperty.Impl(LongConsumer { this.setValue(it as R) })
                else ->
                    SetterProperty.Impl(unBoxed, Consumer { this.setValue(it) })
            }
            is GetterProperty -> when (unBoxed) {
                java.lang.Byte.TYPE,
                java.lang.Short.TYPE,
                java.lang.Character.TYPE,
                java.lang.Integer.TYPE ->
                    IntGetterProperty.Impl(IntSupplier { this.getValue() as Int })
                java.lang.Boolean.TYPE ->
                    BooleanGetterProperty.Impl(BooleanSupplier { this.getValue() as Boolean })
                java.lang.Double.TYPE,
                java.lang.Float.TYPE ->
                    DoubleGetterProperty.Impl(DoubleSupplier { this.getValue() as Double })
                java.lang.Long.TYPE ->
                    LongGetterProperty.Impl(LongSupplier { this.getValue() as Long })
                else ->
                    GetterProperty.Impl(unBoxed, Supplier { this.getValue() })
            }
            else -> this
        } as Property<R>
    }
}