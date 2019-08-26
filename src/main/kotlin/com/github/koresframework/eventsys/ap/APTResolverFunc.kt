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
package com.github.koresframework.eventsys.ap

import com.github.jonathanxd.iutils.type.TypeUtil
import com.github.jonathanxd.kores.type.KoresType
import com.github.jonathanxd.kores.type.PlainKoresType
import com.github.jonathanxd.kores.type.getKoresType
import com.github.jonathanxd.kores.type.koresType
import com.github.jonathanxd.kores.util.KoresTypeResolverFunc
import com.github.koresframework.eventsys.util.DeclarationCache
import javax.lang.model.util.Elements

class APTResolverFunc(
    val elements: Elements,
    val cache: DeclarationCache
) : KoresTypeResolverFunc() {

    override fun resolve(t: String): KoresType {
        try {
            return TypeUtil.resolveClass<Any>(t).koresType
        } catch (e: Exception) {
            val seq = if (t.startsWith("L") && t.endsWith(";")) {
                t.substring(1 until t.length-1).replace('/', '.').replace('$', '.')
            } else t

            elements.getTypeElement(seq)?.getKoresType(elements)?.let {
                return it
            }

            val plain = PlainKoresType(seq, false)

            return if (cache.has(plain)) {
                cache[plain]
            } else {
                plain
            }
        }
    }
}