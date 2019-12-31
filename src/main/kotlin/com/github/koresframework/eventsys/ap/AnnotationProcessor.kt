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
import com.github.jonathanxd.iutils.kt.rightOrFail
import com.github.jonathanxd.iutils.kt.typedKeyOf
import com.github.jonathanxd.kores.Types
import com.github.jonathanxd.kores.base.TypeDeclaration
import com.github.jonathanxd.kores.extra.UnifiedAnnotation
import com.github.jonathanxd.kores.extra.getUnificationInstance
import com.github.jonathanxd.kores.generic.GenericSignature
import com.github.jonathanxd.kores.source.process.PlainSourceGenerator
import com.github.jonathanxd.kores.type.*
import com.github.jonathanxd.kores.util.KoresTypeResolverFunc
import com.github.jonathanxd.kores.util.fromSourceString
import com.github.jonathanxd.kores.util.inferType
import com.github.koresframework.eventsys.context.EnvironmentContext
import com.github.koresframework.eventsys.event.Cancellable
import com.github.koresframework.eventsys.event.Event
import com.github.koresframework.eventsys.event.annotation.Extension
import com.github.koresframework.eventsys.event.property.PropertyHolder
import com.github.koresframework.eventsys.extension.ExtensionSpecification
import com.github.koresframework.eventsys.gen.GenerationEnvironment
import com.github.koresframework.eventsys.gen.event.*
import com.github.koresframework.eventsys.logging.MessageType
import java.io.IOException
import java.util.*
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.Filer
import javax.annotation.processing.Messager
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.SourceVersion
import javax.lang.model.element.*
import javax.lang.model.type.TypeKind
import javax.lang.model.type.TypeMirror
import javax.tools.Diagnostic
import javax.tools.FileObject
import javax.tools.StandardLocation

val ROUND_ENV_KEY = typedKeyOf<RoundEnvironment>("ROUND_ENVIRONMENT")

/**
 * Annotation processor of compile-time factory interface generation.
 */
class AnnotationProcessor : AbstractProcessor() {
    private val sourceGen = PlainSourceGenerator()

    private val logger by lazy { CTLogger(this.processingEnv.messager) }

    private val env: GenerationEnvironment by lazy {
        APTEnvironment(this.processingEnv.elementUtils)
    }

    private val defaultGenerator: EventGenerator by lazy {
        createGenerator()
    }

    private val resolver: KoresTypeResolverFunc by lazy {
        APTResolverFunc(this.processingEnv.elementUtils, this.env.declarationCache)
    }

    override fun process(
        annotations: MutableSet<out TypeElement>,
        roundEnv: RoundEnvironment
    ): Boolean {
        val messager = this.processingEnv.messager
        val ctx = EnvironmentContext().also {
            ROUND_ENV_KEY.set(it.data, roundEnv)
        }

        try {
            val elements =
                roundEnv.getElementsAnnotatedWith(Factory::class.java) + roundEnv.getElementsAnnotatedWith(
                    Factories::class.java
                )

            val settingsElements = roundEnv.getElementsAnnotatedWith(FactorySettings::class.java)

            val propertiesToGen = mutableListOf<FactoryInfo>()
            val settings = mutableListOf<FactorySettingsAnnotatedElement>()

            elements.forEach {
                if (it is TypeElement) {
                    val all = getFactoryAnnotations(it).toMutableList()

                    if (all.isNotEmpty()) {
                        val annotation = all.first()

                        if (annotation.inheritProperties()) {
                            val props = getSubTypesProperties(it, annotation)

                            props.forEach { (_, lannotations) ->
                                all += lannotations
                            }
                        }
                    }

                    all.forEach { annotation ->

                        val cTypeWithParams =
                            it.getKoresTypeFromTypeParameters(processingEnv.elementUtils).asGeneric
                        val koresType = it.getKoresType(processingEnv.elementUtils).concreteType
                        if (!koresType.`is`(PropertyHolder::class.java)
                                && !koresType.`is`(Event::class.java)
                        ) {
                            checkExtension(annotation, it)
                            val genericKoresType =
                                it.asType().getKoresType(processingEnv.elementUtils)

                            val properties = getProperties(annotation, it).map { prop ->
                                val propWithParams =
                                    prop.annotatedElement.getKoresTypeFromTypeParameters(
                                        processingEnv.elementUtils
                                    )
                                        .asGeneric

                                prop.copy(
                                    propertyType = inferType(
                                        prop.propertyType,
                                        propWithParams,
                                        cTypeWithParams,
                                        koresType.defaultResolver,
                                        ModelResolver(processingEnv.elementUtils)
                                    )
                                )
                            }

                            val params =
                                it.getKoresTypeFromTypeParameters(processingEnv.elementUtils)

                            val signature: GenericSignature =
                                if (it.typeParameters.isEmpty()
                                        || params !is GenericType
                                        // \/ = Defensive check
                                        || !params.bounds.all { it.type is GenericType }
                                ) GenericSignature.empty()
                                else GenericSignature(params.bounds.map { it.type as GenericType }.toTypedArray())

                            propertiesToGen += FactoryInfo(
                                genericKoresType,
                                it,
                                signature,
                                annotation,
                                properties,
                                if (annotation.methodName().isNotEmpty()) annotation.methodName() else it.factoryName(),
                                it
                            )
                        }
                    }
                }

            }

            settingsElements.forEach {
                if (it is TypeElement) {
                    settings += FactorySettingsAnnotatedElement(it, getFactorySettingsAnnotation(it))
                }
            }

            if (!propertiesToGen.isEmpty()) {

                val grouped = propertiesToGen.groupBy { it.factoryUnification.value() }
                val groupedSetting = settings.groupBy { it.settings.value() }

                grouped.forEach { name, factInf ->
                    val setting = groupedSetting[name]
                    val declaration = FactoryInterfaceGenerator.processNamed(name, factInf)

                    this.save(declaration, *factInf.map { it.origin }.toTypedArray())

                    if (setting != null && setting.any { it.settings.compileTimeGenerator() }) {

                        this.validateSettings(messager, setting)

                        val extensions = settings.flatMap { it.settings.extensions() }

                        val generator = this.createGenerator(extensions)
                        this.generateFactory(declaration, generator, ctx)
                    }

                }


            }

            return true
        } catch (e: Throwable) {
            logger.log("Failed to process annotation.", MessageType.STANDARD_FATAL, e, ctx)
            throw e
        }
    }

    private fun createGenerator(extensions: List<EventExtensionUnification>) =
        if (extensions.isEmpty()) this.defaultGenerator
        else createGenerator().also { gen ->
            extensions.forEach { eext ->
                val extensionsSpecs = eext.extensions().map {
                    ExtensionSpecification(Unit,
                        it.implement().let {
                            if (it.`is`(Default::class.java)) null
                            else it
                        },
                        it.extensionClass().let {
                            if (it.`is`(Default::class.java)) null
                            else it
                        }
                    )
                }

                extensionsSpecs.forEach { spec ->
                    eext.events().forEach { evt ->
                        gen.registerExtension(fromSourceString(evt, this.resolver), spec)
                    }
                }
            }
        }

    private fun validateSettings(messager: Messager,
                                 setting: List<FactorySettingsAnnotatedElement>) {
        val extensions = setting.map { it.typeElement to it.settings.extensions() }

        extensions.forEach { (type, ext) ->
            ext.forEach {
                if (it.events().isEmpty()) {
                    messager.printError("'events' property should not be empty.", type, it)
                }
                if (it.extensions().isEmpty()) {
                    messager.printError("'extensions' should not be empty.", type, it)
                }
            }
        }
    }

    fun Messager.printError(msg: String,
                            type: Element,
                            annotation: UnifiedAnnotation) {
        val origin = annotation.getUnifiedAnnotationOrigin()

        if (origin is AnnotationMirror) {
            this.printMessage(
                Diagnostic.Kind.WARNING,
                msg,
                type, origin
            )
        } else {
            this.printMessage(
                Diagnostic.Kind.WARNING,
                msg,
                type
            )
        }
    }

    private fun createGenerator() =
        CommonEventGenerator(this.logger, this.env).also {
            it.options[EventGeneratorOptions.LAZY_EVENT_GENERATION_MODE] = LazyGenerationMode.REFLECTION
        }

    private fun generateFactory(declaration: TypeDeclaration, eventGenerator: EventGenerator,
                                ctx: EnvironmentContext) {
        val (factory, events) = EventFactoryClassGenerator.createDeclaration(
            eventGenerator,
            declaration,
            eventGenerator.logger,
            eventGenerator.generationEnvironment,
                ctx
        )

        events.forEach {
            this.save(it.classDeclaration)
        }

        this.save(factory)
    }

    private fun save(declaration: TypeDeclaration, vararg origin: Element) {
        val file = get(processingEnv.filer, declaration.packageName, declaration.simpleName)

        file.ifPresent { it.delete() }

        val classFile = processingEnv.filer.createSourceFile(declaration.qualifiedName, *origin)

        val outputStream = classFile.openOutputStream()

        outputStream.write(sourceGen.process(declaration).toByteArray(Charsets.UTF_8))

        outputStream.flush()
        outputStream.close()
    }

    private fun getFactorySettingsAnnotation(element: Element): FactorySettingsUnification {
        val single = element.annotationMirrors.filter {
            it.annotationType.getKoresType(processingEnv.elementUtils)
                .concreteType.`is`(FactorySettings::class.java)
        }.toMutableList().single()

        return getUnificationInstance(
            annotation = single,
            unificationInterface = FactorySettingsUnification::class.java,
            elements = processingEnv.elementUtils,
            additionalUnificationGetter = {
                when {
                    it.`is`(typeOf<EventExtension>()) -> EventExtensionUnification::class.java
                    it.`is`(Extension::class.java) -> ExtensionUnification::class.java
                    else -> null
                }
            }
        )
    }

    private fun getFactoryAnnotations(element: Element): List<FactoryUnification> {
        val all = element.annotationMirrors.filter {
            it.annotationType.getKoresType(processingEnv.elementUtils)
                .concreteType.`is`(Factory::class.java)
        }.toMutableList()

        element.annotationMirrors.filter {
            it.annotationType.getKoresType(processingEnv.elementUtils)
                .concreteType.`is`(Factories::class.java)
        }.forEach {
            it.elementValues.forEach { executableElement, annotationValue ->
                if (executableElement.simpleName.contentEquals("value")) {
                    val value = annotationValue.value
                    if (value is List<*>) {
                        value.forEach {
                            if (it is AnnotationMirror)
                                all += it
                        }
                    }
                }
            }
        }

        return all.map {
            getUnificationInstance(
                it,
                FactoryUnification::class.java, {
                    if (it.`is`(Extension::class.java))
                        ExtensionUnification::class.java
                    else null
                }, processingEnv.elementUtils
            )
        }
    }

    private fun getSubTypesProperties(
        element: TypeElement,
        current: FactoryUnification
    ): List<FactoryAnnotatedElement> {

        val all = mutableListOf<FactoryAnnotatedElement>()
        val impls = current.extensions().map { it.implement() }
        val exts = current.extensions().map { it.extensionClass() }

        this.getSubTypesProperties0(element, all, false)

        return all.map {
            FactoryAnnotatedElement(it.typeElement, it.factoryAnnotations.filter {
                it.extensions().isNotEmpty()
                        && it.extensions().none { out ->
                    impls.any { out.implement().`is`(it) }
                            || exts.any { out.extensionClass().`is`(it) }
                }
            })
        }.filter { it.factoryAnnotations.isNotEmpty() }
    }

    private fun getSubTypesProperties0(
        element: TypeElement,
        unificationList: MutableList<FactoryAnnotatedElement>,
        instaInspect: Boolean = true
    ) {

        if (instaInspect)
            unificationList += FactoryAnnotatedElement(element, getFactoryAnnotations(element))

        if (element.superclass.kind != TypeKind.NONE) {
            val type = element.superclass.getKoresType(processingEnv.elementUtils).concreteType
            val resolve = type.defaultResolver.resolve(type).rightOrFail

            if (resolve is TypeElement) {
                getSubTypesProperties0(resolve, unificationList)
            }
        }

        element.interfaces.forEach {
            val type = it.getKoresType(processingEnv.elementUtils).concreteType
            val resolve = type.defaultResolver.resolve(type).rightOrFail

            if (resolve is TypeElement) {
                getSubTypesProperties0(resolve, unificationList)
            }
        }
    }

    fun getTypes(element: TypeElement): Set<KoresType> {
        val types = mutableSetOf<KoresType>()
        this.getTypes(element, types)
        return types
    }

    fun getTypes(element: TypeElement, types: MutableSet<KoresType>) {
        types += element.getKoresType(processingEnv.elementUtils)
        val superC = element.superclass

        val tps = mutableListOf<TypeMirror>()

        if (superC.kind != TypeKind.NONE) {
            tps += superC
        }

        tps += element.interfaces

        tps.forEach {
            types += it.getKoresType(processingEnv.elementUtils).concreteType.also {
                (it.defaultResolver.resolve(it).rightOrFail as? TypeElement)?.let {
                    this.getTypes(it, types)
                }
            }
        }

    }

    fun checkExtension(factoryUnification: FactoryUnification, element: TypeElement) {
        val types = getTypes(element)

        factoryUnification.extensions().forEach {
            var found = false

            val impl = it.extensionClass().concreteType
            val tp = impl.defaultResolver.resolve(impl).rightOrFail

            if (!impl.`is`(Default::class.java)) {

                if (tp is TypeElement) {
                    tp.enclosedElements.forEach {
                        if (it is ExecutableElement) {
                            if (it.kind == ElementKind.CONSTRUCTOR) {
                                if (it.parameters.size == 1
                                        && it.parameters.first().asType().getKoresType(processingEnv.elementUtils).let { paramType ->
                                            types.any { type -> type.`is`(paramType) }
                                        }
                                )
                                    found = true
                            }
                        }
                    }

                    if (!found) {
                        processingEnv.messager.printMessage(
                            Diagnostic.Kind.ERROR,
                            "Extension class '${impl.simpleName}' requires at least a constructor with only one parameter of one of following types: ${types.joinToString { it.simpleName }}!"
                        )
                    }
                }
            }
        }
    }

    private fun getProperties(
        factoryUnification: FactoryUnification,
        element: TypeElement,
        extensionOnly: Boolean = false,
        list: MutableList<EventSysProperty> = mutableListOf()
    ): List<EventSysProperty> {

        if (!extensionOnly) {
            this.getProperties(factoryUnification, element, list)

            val types = mutableListOf<TypeMirror>()

            if (element.superclass.kind != TypeKind.NONE) {
                types += element.superclass
            }

            types += element.interfaces

            val itfs = types.map { it.getKoresType(processingEnv.elementUtils).concreteType }.map {
                it.defaultResolver.resolve(it)
            }.filter { it.isRight }.map { it.rightOrFail }.toMutableList()

            itfs.forEach {
                if (it is TypeElement) {
                    getProperties(factoryUnification, it, list)
                }

            }
        }

        factoryUnification.extensions().forEach {
            val impl = it.implement().concreteType
            val tp = impl.defaultResolver.resolve(impl).rightOrFail

            if (tp is TypeElement) {
                this.getProperties(factoryUnification, tp, list)
            }
        }

        return list
    }

    private fun getProperties(
        factoryUnification: FactoryUnification,
        element: TypeElement,
        list: MutableList<EventSysProperty>
    ) {

        val koresType = element.getKoresType(processingEnv.elementUtils).concreteType

        if (koresType.`is`(Default::class.java))
            return

        if (!koresType.`is`(PropertyHolder::class.java)
                && !koresType.`is`(Event::class.java)
                && !koresType.`is`(Cancellable::class.java)
        ) {
            element.enclosedElements.forEach {
                if (it is ExecutableElement) {
                    val name = it.simpleName.toString()
                    val isSet = (name.startsWith("set") && it.parameters.size == 1)
                    val isGetOrSet = (name.startsWith("get") && it.parameters.isEmpty()) || isSet
                    val isIs = name.startsWith("is") && it.parameters.isEmpty()

                    if (isGetOrSet || isIs) {
                        val propertyName =
                            (if (isGetOrSet) name.substring(3 until name.length) else name.substring(
                                2 until name.length
                            ))
                                .decapitalize()

                        val type =
                            if (isSet) it.parameters.first().asType().getKoresType(processingEnv.elementUtils)
                            else it.returnType.getKoresType(processingEnv.elementUtils)

                        if (!type.`is`(Types.VOID)) {

                            if (list.none { it.propertyName == propertyName }
                                    && !containsMethod(it, factoryUnification)) {
                                list += EventSysProperty(element, type, propertyName)
                            }
                        }

                    }

                }
            }
        }

        val types = mutableListOf<TypeMirror>()

        if (element.superclass.kind != TypeKind.NONE) {
            types += element.superclass
        }

        types += element.interfaces

        val itfs = types.map { it.getKoresType(processingEnv.elementUtils).concreteType }.map {
            it.defaultResolver.resolve(it)
        }.filter { it.isRight }.map { it.right!! }.toMutableList()

        itfs.forEach {
            if (it is TypeElement) {
                getProperties(factoryUnification, it, list)
            }

        }

    }

    fun containsMethod(typeElement: TypeElement, executableElement: ExecutableElement): Boolean {
        if (typeElement.getKoresType(processingEnv.elementUtils).concreteType.`is`(Default::class.java))
            return false

        typeElement.enclosedElements.forEach {
            if (it is ExecutableElement) {
                if (it.simpleName.toString() == executableElement.simpleName.toString()
                        && it.parameters.map {
                            it.asType().getKoresType(processingEnv.elementUtils).concreteType
                        }
                            .`is`(executableElement.parameters.map {
                                it.asType().getKoresType(processingEnv.elementUtils).concreteType
                            })
                ) {
                    return true
                }
            }
        }

        val types = mutableListOf<TypeMirror>()

        if (typeElement.superclass.kind != TypeKind.NONE) {
            types += typeElement.superclass
        }

        types += typeElement.interfaces

        val itfs = types.map { it.getKoresType(processingEnv.elementUtils).concreteType }.map {
            it.defaultResolver.resolve(it)
        }.filter { it.isRight }.map { it.right!! }.toMutableList()

        itfs.forEach {
            if (it is TypeElement) {
                if (containsMethod(it, executableElement))
                    return true
            }

        }

        return false
    }


    fun containsMethod(
        executableElement: ExecutableElement,
        factoryUnification: FactoryUnification
    ): Boolean {
        val extensions = factoryUnification.extensions()

        extensions.forEach {
            val cl = it.extensionClass().concreteType
            val resolve = cl.defaultResolver.resolve(cl).rightOrFail

            if (resolve is TypeElement) {
                if (containsMethod(resolve, executableElement))
                    return true
            }
        }

        return false
    }

    override fun getSupportedAnnotationTypes(): MutableSet<String> {
        return mutableSetOf(
            "com.github.koresframework.eventsys.ap.Factory",
            "com.github.koresframework.eventsys.ap.Factories"
        )
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

data class FactoryAnnotatedElement(
    val typeElement: TypeElement,
    val factoryAnnotations: List<FactoryUnification>
)

data class FactorySettingsAnnotatedElement (
    val typeElement: TypeElement,
    val settings: FactorySettingsUnification
)