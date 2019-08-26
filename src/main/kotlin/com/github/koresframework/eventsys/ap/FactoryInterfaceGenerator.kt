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

import com.github.jonathanxd.iutils.`object`.Default
import com.github.jonathanxd.iutils.type.TypeInfo
import com.github.jonathanxd.kores.base.*
import com.github.jonathanxd.kores.base.Annotation
import com.github.jonathanxd.kores.base.Retention
import com.github.jonathanxd.kores.factory.parameter
import com.github.jonathanxd.kores.generic.GenericSignature
import com.github.jonathanxd.kores.type.Generic
import com.github.jonathanxd.kores.type.KoresType
import com.github.jonathanxd.kores.type.koresType
import com.github.jonathanxd.kores.util.eraseType
import com.github.koresframework.eventsys.event.annotation.Extension
import com.github.koresframework.eventsys.event.annotation.LazyGeneration
import com.github.koresframework.eventsys.event.annotation.Name
import com.github.koresframework.eventsys.event.annotation.TypeParam
import com.github.koresframework.eventsys.gen.event.eventTypeFieldName
import javax.lang.model.element.Element
import javax.lang.model.element.TypeElement

/**
 * Generator of factory interface
 */
object FactoryInterfaceGenerator {

    private val DEFAULT = Default::class.java.koresType

    fun processNamed(name: String,
                     factoryInfoList: List<FactoryInfo>): TypeDeclaration {
        return InterfaceDeclaration.Builder.builder()
            .modifiers(KoresModifier.PUBLIC)
            .name(name)
            .methods(createMethods(factoryInfoList))
            .build()
    }

    private fun createMethods(
        factoryInfoList: List<FactoryInfo>,
        descs: MutableList<MethodDesc> = mutableListOf()
    ): List<MethodDeclaration> =
        factoryInfoList.map {
            val annotations: MutableList<Annotation> = it.factoryUnification.extensions()
                .filter {
                    !it.implement().`is`(DEFAULT)
                            || !it.extensionClass().`is`(DEFAULT)
                }
                .map {
                    val values = mutableMapOf<String, Any>()

                    if (!it.implement().`is`(DEFAULT)) {
                        values["implement"] = it.implement()
                    }

                    if (!it.extensionClass().`is`(DEFAULT)) {
                        values["extensionClass"] = it.extensionClass()
                    }

                    Annotation.Builder.builder()
                        .retention(Retention.RUNTIME)
                        .type(Extension::class.java)
                        .values(values)
                        .build()
                }
                .toMutableList()

            if (it.factoryUnification.lazy())
                annotations += Annotation.Builder.builder()
                    .retention(Retention.RUNTIME)
                    .type(LazyGeneration::class.java)
                    .build()

            val parameters = mutableListOf<KoresParameter>()

            val gT = Generic.type(TypeInfo::class.java).of(it.type)

            if (it.element.typeParameters.isNotEmpty() && !it.factoryUnification.omitTypeParam())
                parameters += parameter(
                    type = gT, name = eventTypeFieldName, annotations = listOf(
                        Annotation.Builder.builder()
                            .type(TypeParam::class.java)
                            .retention(Retention.RUNTIME)
                            .build()
                    )
                )

            parameters += it.properties.map {
                parameter(
                    type = it.propertyType, name = it.propertyName, annotations = listOf(
                        Annotation.Builder.builder()
                            .type(Name::class.java)
                            .retention(Retention.RUNTIME)
                            .values(mapOf("value" to it.propertyName))
                            .build()
                    )
                )
            }

            val desc = MethodDesc(it.name, parameters.size)

            val name = getUniqueName(desc, descs)
            descs += desc.copy(name = name)

            val signature = it.signature

            MethodDeclaration.Builder.builder()
                .annotations(annotations)
                .modifiers(KoresModifier.PUBLIC)
                .genericSignature(it.signature)
                .name(name)
                .returnType(eraseType(it.type, signature))
                .parameters(parameters.map {
                    it.builder().type(eraseType(it.type, signature)).build()
                })
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

class FactoryInfo(
    val type: KoresType,
    val element: TypeElement,
    val signature: GenericSignature,
    val factoryUnification: FactoryUnification,
    val properties: List<EventSysProperty>,
    val name: String,
    val origin: Element
)

data class EventSysProperty(
    val annotatedElement: TypeElement,
    val propertyType: KoresType,
    val propertyName: String
)