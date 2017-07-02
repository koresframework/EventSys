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

import com.github.jonathanxd.codeapi.extra.getUnificationInstance
import com.github.jonathanxd.codeapi.source.process.PlainSourceGenerator
import com.github.jonathanxd.codeapi.type.CodeType
import com.github.jonathanxd.codeapi.util.`is`
import com.github.jonathanxd.codeapi.util.concreteType
import com.github.jonathanxd.codeapi.util.getCodeType
import com.github.jonathanxd.iutils.`object`.Default
import com.github.projectsandstone.eventsys.event.annotation.Extension
import com.github.projectsandstone.eventsys.event.property.PropertyHolder
import java.io.IOException
import java.util.*
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.Filer
import javax.annotation.processing.ProcessingEnvironment
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.SourceVersion
import javax.lang.model.element.ElementKind
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.TypeElement
import javax.lang.model.type.TypeKind
import javax.lang.model.type.TypeMirror
import javax.tools.Diagnostic
import javax.tools.FileObject
import javax.tools.StandardLocation

class AnnotationProcessor : AbstractProcessor() {
    private lateinit var processingEnvironment: ProcessingEnvironment

    override fun init(processingEnv: ProcessingEnvironment) {
        super.init(processingEnv)
        this.processingEnvironment = processingEnv
    }

    override fun process(annotations: MutableSet<out TypeElement>, roundEnv: RoundEnvironment): Boolean {
        val elements = roundEnv.getElementsAnnotatedWith(Factory::class.java)
        val propertiesToGen = mutableListOf<FactoryInfo>()

        elements.forEach {
            if (it is TypeElement) {
                val first = it.annotationMirrors.firstOrNull {
                    it.annotationType.getCodeType(processingEnv.elementUtils).concreteType.`is`(Factory::class.java)
                }

                if (first != null) {
                    val factoryAnnotation = getUnificationInstance(
                            first,
                            FactoryUnification::class.java, {
                        if (it.`is`(Extension::class.java))
                            ExtensionUnification::class.java
                        else null
                    }, processingEnv.elementUtils)

                    val codeType = it.getCodeType(processingEnv.elementUtils).concreteType
                    if (!codeType.`is`(PropertyHolder::class.java)) {
                        checkExtension(factoryAnnotation, it)
                        val properties = getProperties(factoryAnnotation, it)
                        propertiesToGen += FactoryInfo(codeType, factoryAnnotation, properties, it)
                        processingEnv.messager.printMessage(Diagnostic.Kind.WARNING, "Properties: $properties")
                    }
                }
            }

        }

        if (!propertiesToGen.isEmpty()) {
            val sourceGen = PlainSourceGenerator()

            val grouped = propertiesToGen.groupBy { it.factoryUnification.factoryClass() }

            grouped.forEach { name, factInf ->
                val declaration = FactoryInterfaceGenerator.processNamed(name, factInf)

                val file = get(processingEnv.filer, declaration.packageName, declaration.simpleName)

                file.ifPresent { it.delete() }

                val classFile = processingEnvironment.filer.createSourceFile(declaration.qualifiedName,
                        *factInf.map { it.origin }.toTypedArray())

                val outputStream = classFile.openOutputStream()

                outputStream.write(sourceGen.process(declaration).toByteArray(Charsets.UTF_8))

                outputStream.flush()
                outputStream.close()

            }


        }

        return true

    }

    fun getTypes(element: TypeElement): Set<CodeType> {
        val types = mutableSetOf<CodeType>()
        this.getTypes(element, types)
        return types
    }

    fun getTypes(element: TypeElement, types: MutableSet<CodeType>) {
        types += element.getCodeType(processingEnv.elementUtils)
        val superC = element.superclass

        val tps = mutableListOf<TypeMirror>()

        if (superC.kind != TypeKind.NONE) {
            tps += superC
        }

        tps += element.interfaces

        tps.forEach {
            types += it.getCodeType(processingEnv.elementUtils).concreteType.also {
                (it.defaultResolver.resolve(it) as? TypeElement)?.let {
                    this.getTypes(it, types)
                }
            }
        }

    }

    fun checkExtension(factoryUnification: FactoryUnification, element: TypeElement) {
        val types = getTypes(element)

        factoryUnification.extension().forEach {
            var found = false

            val impl = it.extensionClass().concreteType
            val tp = impl.defaultResolver.resolve(impl)

            if (tp is TypeElement) {
                tp.enclosedElements.forEach {
                    if (it is ExecutableElement) {
                        if (it.kind == ElementKind.CONSTRUCTOR) {
                            if (it.parameters.size == 1
                                    && it.parameters.first().asType().getCodeType(processingEnv.elementUtils).let {
                                paramType ->
                                types.any { type -> type.`is`(paramType) }
                            })
                                found = true
                        }
                    }
                }

                if (!found) {
                    processingEnv.messager.printMessage(Diagnostic.Kind.ERROR, "Extension class requires at least a constructor with only one parameter of one of following types: ${types.joinToString { it.simpleName }}!")
                }
            }
        }
    }

    fun getProperties(factoryUnification: FactoryUnification, element: TypeElement): List<Pair<CodeType, String>> {
        val list = mutableListOf<Pair<CodeType, String>>()
        this.getProperties(factoryUnification, element, list)

        factoryUnification.extension().forEach {
            val impl = it.implement().concreteType
            val tp = impl.defaultResolver.resolve(impl)

            if (tp is TypeElement) {
                getProperties(factoryUnification, tp, list)
            }
        }

        return list
    }

    fun getProperties(factoryUnification: FactoryUnification, element: TypeElement, list: MutableList<Pair<CodeType, String>>) {
        // TODO: Test
        val codeType = element.getCodeType(processingEnv.elementUtils).concreteType

        if (codeType.concreteType.`is`(Default::class.java))
            return

        if (!codeType.`is`(PropertyHolder::class.java)) {
            element.enclosedElements.forEach {
                if (it is ExecutableElement) {
                    val name = it.simpleName.toString()
                    val isGetOrSet = name.startsWith("get") || name.startsWith("set")
                    val isIs = name.startsWith("is")

                    if (isGetOrSet || isIs) {
                        val propertyName =
                                (if (isGetOrSet) name.substring(3..name.length - 1) else name.substring(2..name.length - 1))
                                        .decapitalize()

                        if (!containsMethod(it, factoryUnification)) {
                            list += it.returnType.getCodeType(processingEnv.elementUtils) to propertyName
                        }

                    }

                }
            }
        }

    }

    fun containsMethod(typeElement: TypeElement, executableElement: ExecutableElement): Boolean {
        if (typeElement.getCodeType(processingEnv.elementUtils).concreteType.`is`(Default::class.java))
            return false

        typeElement.enclosedElements.forEach {
            if (it is ExecutableElement) {
                if (it.simpleName.toString() == executableElement.simpleName.toString()
                        && it.parameters.map { it.asType().getCodeType(processingEnv.elementUtils).concreteType }
                        .`is`(executableElement.parameters.map {
                            it.asType().getCodeType(processingEnv.elementUtils).concreteType
                        })) {
                    return true
                }
            }
        }

        val types = mutableListOf<TypeMirror>()

        if (typeElement.superclass.kind != TypeKind.NONE) {
            types += typeElement.superclass
        }

        types += typeElement.interfaces

        val itfs = types.map { it.getCodeType(processingEnv.elementUtils).concreteType }.map {
            it.defaultResolver.resolve(it)
        }.toMutableList()

        itfs.forEach {
            if (it is TypeElement) {
                if (containsMethod(it, executableElement))
                    return true
            }

        }

        return false
    }


    fun containsMethod(executableElement: ExecutableElement, factoryUnification: FactoryUnification): Boolean {
        val extensions = factoryUnification.extension()

        extensions.forEach {
            val cl = it.extensionClass().concreteType
            val resolve = cl.defaultResolver.resolve(cl)

            if (resolve is TypeElement) {
                if (containsMethod(resolve, executableElement))
                    return true
            }
        }

        return false
    }

    override fun getSupportedAnnotationTypes(): MutableSet<String> {
        return mutableSetOf("com.github.projectsandstone.eventsys.ap.Factory")
    }

    override fun getSupportedSourceVersion(): SourceVersion {
        return SourceVersion.latest()
    }

    fun get(filer: Filer, pkg: String, name: String): Optional<FileObject> {

        try {
            return Optional.ofNullable(filer.getResource(StandardLocation.SOURCE_OUTPUT, pkg, name))
        } catch (e: IOException) {
            return Optional.empty()
        }

    }
}

