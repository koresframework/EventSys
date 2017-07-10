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
package com.github.projectsandstone.eventsys.event.property.primitive

import com.github.projectsandstone.eventsys.event.property.GSProperty
import com.github.projectsandstone.eventsys.event.property.primitive.LongProperty
import com.github.projectsandstone.eventsys.event.property.primitive.LongSetterProperty
import com.github.projectsandstone.eventsys.event.property.primitive.LongGetterProperty
import java.util.function.LongConsumer
import java.util.function.LongSupplier

/**
 * Long getter and setter property.
 *
 * Avoid boxing and unboxing.
 */
interface LongGSProperty : LongProperty, LongGetterProperty, LongSetterProperty, GSProperty<Long> {

    class Impl(val getter: LongSupplier, val setter: LongConsumer) : LongGSProperty {
        override fun getAsLong(): Long = this.getter.asLong
        override fun setAsLong(value: Long) = this.setter.accept(value)
    }

}