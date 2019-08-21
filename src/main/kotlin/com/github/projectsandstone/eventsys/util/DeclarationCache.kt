/*
 *      EventSys - Event implementation generator written on top of Kores
 *
 *         The MIT License (MIT)
 *
 *      Copyright (c) 2018 ProjectSandstone <https://github.com/ProjectSandstone/EventSys>
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

import com.github.jonathanxd.iutils.kt.ifRightSide
import com.github.jonathanxd.iutils.kt.rightOrFail
import com.github.jonathanxd.iutils.recursion.Element
import com.github.jonathanxd.iutils.recursion.ElementUtil
import com.github.jonathanxd.iutils.recursion.Elements
import com.github.jonathanxd.kores.base.ImplementationHolder
import com.github.jonathanxd.kores.base.MethodDeclaration
import com.github.jonathanxd.kores.base.SuperClassHolder
import com.github.jonathanxd.kores.base.TypeDeclaration
import com.github.jonathanxd.kores.type.*
import com.github.projectsandstone.eventsys.reflect.isEqual
import java.lang.reflect.Type

class DeclarationCache {

    private val cache = mutableMapOf<KoresType, TypeDeclaration>()
    private val mcache = mutableMapOf<TypeDeclaration, List<DeclaredMethod>>()
    private val scache = mutableMapOf<TypeDeclaration, List<TypeDeclaration>>()

    fun has(type: Type) = this.cache.contains(type.koresType)

    operator fun get(type: Type): TypeDeclaration =
        cache.computeIfAbsent(type.koresType) {
            it.concreteType.bindedDefaultResolver.resolveTypeDeclaration().rightOrFail
        }

    fun getMethods(typeDeclaration: TypeDeclaration): List<DeclaredMethod> =
        this.mcache.computeIfAbsent(typeDeclaration) {
            it.allMethods
        }

    fun getInnerClasses(typeDeclaration: TypeDeclaration): List<TypeDeclaration> =
        this.scache.computeIfAbsent(typeDeclaration) {
            it.allInner
        }

    fun getMethods(type: Type): List<DeclaredMethod> = this[type].run(this::getMethods)
    fun getInnerClasses(type: Type): List<TypeDeclaration> = this[type].run(this::getInnerClasses)
}

private val TypeDeclaration.allMethods: List<DeclaredMethod>
    get() {
        val metds = mutableListOf<DeclaredMethod>()

        this.forAllTypes {
            this.methods.map {
                val declared = DeclaredMethod(this, it)

                if (metds.none { (_, it) -> it.isEqual(declared.methodDeclaration) })
                    metds += declared
            }
        }

        return metds
    }

private val TypeDeclaration.allInner: List<TypeDeclaration>
    get() {
        val inners = mutableListOf<TypeDeclaration>()
        this.forAllTypes {
            inners += this.innerTypes
        }

        return inners
    }

inline fun TypeDeclaration.forAllTypes(consumer: TypeDeclaration.() -> Unit) {
    val elements = Elements<TypeDeclaration>()
    elements.insert(Element(this))

    while (true) {
        val next = elements.nextElement() ?: break
        val type = next.value

        consumer(type)

        val types = mutableListOf<TypeDeclaration>()

        if (type is SuperClassHolder) {
            type.superType?.also {
                it.bindedDefaultResolver.resolveTypeDeclaration().ifRightSide {
                    if (!it.`is`(typeOf<Any>()))
                        types += it
                }
            }
        }

        if (type is ImplementationHolder) {
            type.implementations.forEach {
                it.bindedDefaultResolver.resolveTypeDeclaration().ifRightSide {
                    types += it
                }
            }
        }

        if (types.isNotEmpty()) {
            elements.insertFromPair(ElementUtil.fromIterable(types))
        }
    }

}

data class DeclaredMethod(val type: TypeDeclaration, val methodDeclaration: MethodDeclaration)