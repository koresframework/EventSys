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
package com.github.projectsandstone.eventsys.ap

import com.github.jonathanxd.codeapi.base.*
import com.github.jonathanxd.codeapi.base.Annotation
import com.github.jonathanxd.codeapi.factory.parameter
import com.github.jonathanxd.codeapi.type.CodeType
import com.github.jonathanxd.codeapi.util.codeType
import com.github.jonathanxd.iutils.`object`.Default
import com.github.projectsandstone.eventsys.event.annotation.Extension
import com.github.projectsandstone.eventsys.event.annotation.Name
import javax.lang.model.element.Element
import javax.lang.model.element.ElementKind
import javax.lang.model.element.TypeElement

object FactoryInterfaceGenerator {

    private val DEFAULT = Default::class.java.codeType

    fun processNamed(name: String, factoryInfoList: List<FactoryInfo>): TypeDeclaration {
        return InterfaceDeclaration.Builder.builder()
                .modifiers(CodeModifier.PUBLIC)
                .name(name)
                .methods(createMethods(factoryInfoList))
                .build()
    }

    private fun createMethods(factoryInfoList: List<FactoryInfo>,
                              names: MutableList<String> = mutableListOf()): List<MethodDeclaration> =
            factoryInfoList.map {
                val annotations: List<Annotation> = it.factoryUnification.extensions()
                        .filter {
                            !it.implement().`is`(DEFAULT)
                                    || !it.extensionClass().`is`(DEFAULT)
                        }
                        .map {
                            val values = mutableMapOf<String, Any>()

                            if (!it.implement().`is`(DEFAULT)) {
                                values.put("implement", it.implement())
                            }

                            if (!it.extensionClass().`is`(DEFAULT)) {
                                values.put("extensionClass", it.extensionClass())
                            }

                            Annotation.Builder.builder()
                                    .visible(true)
                                    .type(Extension::class.java)
                                    .values(values)
                                    .build()
                        }

                val name = getUniqueName(it.name, names)
                names += name

                MethodDeclaration.Builder.builder()
                        .modifiers(CodeModifier.PUBLIC)
                        .annotations(annotations)
                        .name(name)
                        .returnType(it.type)
                        .parameters(it.properties.map {
                            parameter(type = it.first, name = it.second, annotations = listOf(
                                    Annotation.Builder.builder()
                                            .type(Name::class.java)
                                            .visible(true)
                                            .values(mapOf("value" to it.second))
                                            .build()
                            ))
                        })
                        .build()
            }

}

fun getUniqueName(name: String, names: List<String>): String {
    if(!names.contains(name))
        return name

    var i = 0

    while (names.contains("$name$i")) {
        ++i
    }

    return "$name$i"
}

fun TypeElement.factoryName(): String = "create${this.name()}"

fun TypeElement.name(): String {
    val builder = StringBuilder()
    var type: Element = this.enclosingElement

    builder.append(this.simpleName.toString().capitalize())

    if (type == this)
        return builder.toString()

    while (type is TypeElement) {

        builder.insert(0, type.simpleName.toString().capitalize())

        if (type == this.enclosingElement)
            break

        type = this.enclosingElement
    }

    return builder.toString()
}

class FactoryInfo(val type: CodeType,
                  val element: TypeElement,
                  val factoryUnification: FactoryUnification,
                  val properties: List<Pair<CodeType, String>>,
                  val name: String,
                  val origin: Element)