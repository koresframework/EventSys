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
import com.github.jonathanxd.codeapi.generic.GenericSignature
import com.github.jonathanxd.codeapi.type.CodeType
import com.github.jonathanxd.codeapi.type.Generic
import com.github.jonathanxd.codeapi.type.GenericType
import com.github.jonathanxd.codeapi.util.codeType
import com.github.jonathanxd.codeapi.util.eraseType
import com.github.jonathanxd.codeapi.util.getCodeTypeFromTypeParameters
import com.github.jonathanxd.codeapi.util.inferType
import com.github.jonathanxd.iutils.`object`.Default
import com.github.projectsandstone.eventsys.event.annotation.Extension
import com.github.projectsandstone.eventsys.event.annotation.Name
import java.lang.reflect.Type
import javax.lang.model.element.Element
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
                              descs: MutableList<MethodDesc> = mutableListOf()): List<MethodDeclaration> =
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

                val parameters = it.properties.map {
                    parameter(type = it.propertyType, name = it.propertyName, annotations = listOf(
                            Annotation.Builder.builder()
                                    .type(Name::class.java)
                                    .visible(true)
                                    .values(mapOf("value" to it.propertyName))
                                    .build()
                    ))
                }

                val desc = MethodDesc(it.name, parameters.size)

                val name = getUniqueName(desc, descs)
                descs += desc

                val signature = it.signature

                MethodDeclaration.Builder.builder()
                        .annotations(annotations)
                        .modifiers(CodeModifier.PUBLIC)
                        .genericSignature(it.signature)
                        .name(name)
                        .returnType(eraseType(it.type, signature))
                        .parameters(parameters.map { it.builder().type(eraseType(it.type, signature)).build() })
                        .build()
            }

}


fun getUniqueName(method: MethodDesc, descs: List<MethodDesc>): String {
    if (!descs.contains(method))
        return method.name

    var i = 0

    while (descs.any { it.parameters == method.parameters && it.name == "${method.name}$i" }) {
        ++i
    }

    return "${method.name}$i"
}

data class MethodDesc(val name: String, val parameters: Int)

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
                  val signature: GenericSignature,
                  val factoryUnification: FactoryUnification,
                  val properties: List<EventSysProperty>,
                  val name: String,
                  val origin: Element)

data class EventSysProperty(val annotatedElement: TypeElement, val propertyType: CodeType, val propertyName: String)