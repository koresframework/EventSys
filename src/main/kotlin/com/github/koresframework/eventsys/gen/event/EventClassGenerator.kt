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
package com.github.koresframework.eventsys.gen.event

import com.github.jonathanxd.iutils.function.consumer.BooleanConsumer
import com.github.jonathanxd.iutils.kt.get
import com.github.jonathanxd.iutils.kt.rightOrFail
import com.github.jonathanxd.iutils.type.TypeInfo
import com.github.jonathanxd.kores.Instruction
import com.github.jonathanxd.kores.Instructions
import com.github.jonathanxd.kores.MutableInstructions
import com.github.jonathanxd.kores.Types
import com.github.jonathanxd.kores.base.*
import com.github.jonathanxd.kores.bytecode.GENERATE_BRIDGE_METHODS
import com.github.jonathanxd.kores.bytecode.VISIT_LINES
import com.github.jonathanxd.kores.bytecode.VisitLineType
import com.github.jonathanxd.kores.bytecode.processor.BytecodeGenerator
import com.github.jonathanxd.kores.bytecode.util.BridgeUtil
import com.github.jonathanxd.kores.common.FieldRef
import com.github.jonathanxd.kores.common.MethodInvokeSpec
import com.github.jonathanxd.kores.common.MethodTypeSpec
import com.github.jonathanxd.kores.factory.*
import com.github.jonathanxd.kores.generic.GenericSignature
import com.github.jonathanxd.kores.helper.ConcatHelper
import com.github.jonathanxd.kores.helper.invokeToString
import com.github.jonathanxd.kores.literal.Literals
import com.github.jonathanxd.kores.type.*
import com.github.jonathanxd.kores.util.MixedResolver
import com.github.jonathanxd.kores.util.conversion.access
import com.github.jonathanxd.kores.util.conversion.toVariableAccess
import com.github.jonathanxd.kores.util.inferType
import com.github.jonathanxd.kores.util.toSourceString
import com.github.koresframework.eventsys.Debug
import com.github.koresframework.eventsys.event.Cancellable
import com.github.koresframework.eventsys.event.Event
import com.github.koresframework.eventsys.event.annotation.Name
import com.github.koresframework.eventsys.event.annotation.NotNullValue
import com.github.koresframework.eventsys.event.annotation.TypeParam
import com.github.koresframework.eventsys.event.annotation.Validate
import com.github.koresframework.eventsys.event.property.*
import com.github.koresframework.eventsys.event.property.primitive.*
import com.github.koresframework.eventsys.extension.ExtensionHolder
import com.github.koresframework.eventsys.extension.ExtensionSpecification
import com.github.koresframework.eventsys.gen.GeneratedEventClass
import com.github.koresframework.eventsys.gen.GenerationEnvironment
import com.github.koresframework.eventsys.gen.ResolvableDeclaration
import com.github.koresframework.eventsys.gen.save.ClassSaver
import com.github.koresframework.eventsys.logging.MessageType
import com.github.koresframework.eventsys.reflect.findImplementation
import com.github.koresframework.eventsys.reflect.getName
import com.github.koresframework.eventsys.reflect.isEqual
import com.github.koresframework.eventsys.util.DeclarationCache
import com.github.koresframework.eventsys.util.NameCaching
import com.github.koresframework.eventsys.util.residenceToString
import com.github.koresframework.eventsys.util.toStructure
import com.github.koresframework.eventsys.validation.Validator
import java.lang.reflect.Type
import java.util.*
import java.util.function.*

/**
 * Generates [Event] class implementation.
 *
 * This class generate properties, constructor, and supports
 * [ExtensionSpecification], Extension method MUST be static and the first parameter must be of
 * event base class type. Extensions is used to implement methods of the event class, this generator
 * only generates properties method, like getters, setters and implementation of [PropertyHolder] method, other
 * methods present in the [Event] class must be manually implemented via [Extensions][ExtensionSpecification],
 * [Extensions][ExtensionSpecification] should be registered in [EventGenerator] (with [EventGenerator.registerExtension] method)
 * to work.
 *
 * If the event class have type parameters and [EventClassSpecification.typeInfo] provides types for these parameters,
 * then a reified event class will be generated, if not, a erased event class will be generated with a [TypeParam] parameter
 * in constructor.
 */
internal object EventClassGenerator {

    private val nameCaching = NameCaching()

    private fun TypeInfo<*>.toStr(): String {
        if (this.typeParameters.isEmpty()) {
            return this.toFullString()
        } else {
            val base = StringBuilder(this.typeClass.name)

            base.append("_of_")
            this.typeParameters.forEach {
                base.append(
                    it.toFullString()
                        .replace(".", "_")
                        .replace("<", "_of_")
                        .replace(">", "__")
                        .replace(", ", "and")
                )
            }

            base.append("__")

            return base.toString()
        }
    }

    private fun KoresType.xtoStr(): String =
        if (this is GenericType && this.bounds.isNotEmpty()) {
            this.resolvedType.canonicalName + this.toSourceString()
                .replace(".", "_")
                .replace("<", "_of_")
                .replace(">", "__")
                .replace(", ", "and")
        } else {
            this.canonicalName
        }

    @Suppress("UNCHECKED_CAST")
    fun <T : Event> genImplementation(
        eventClassSpecification: EventClassSpecification,
        eventGenerator: EventGenerator,
        generationEnvironment: GenerationEnvironment
    ): ResolvableDeclaration<Class<T>> {
        val classDeclaration =
            genImplementationDeclaration(eventClassSpecification, eventGenerator, generationEnvironment)

        return genImplementationFromDeclaration(classDeclaration)
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Event> genImplementationFromDeclaration(eventDeclaration: ClassDeclaration): ResolvableDeclaration<Class<T>> {
        val resolver = lazy(LazyThreadSafetyMode.NONE) {
            val generator = BytecodeGenerator()

            generator.options.set(VISIT_LINES, VisitLineType.GEN_LINE_INSTRUCTION)
            generator.options.set(GENERATE_BRIDGE_METHODS, true)

            val bytecodeClass = generator.process(eventDeclaration)[0]

            val bytes = bytecodeClass.bytecode
            val disassembled = lazy { bytecodeClass.disassembledCode }

            try {
                val generatedEventClass = EventGenClassLoader.defineClass(
                    eventDeclaration,
                    bytes,
                    disassembled
                ) as GeneratedEventClass<T>

                if (Debug.isSaveEnabled()) {
                    ClassSaver.save(Debug.EVENT_GEN_DEBUG, generatedEventClass)
                }

                return@lazy generatedEventClass.javaClass
            } catch (t: Throwable) {
                throw RuntimeException("Disassembled: \n${disassembled.value}", t)
            }
        }

        return ResolvableDeclaration(eventDeclaration, resolver)
    }

    @Suppress("UNCHECKED_CAST")
    fun genImplementationDeclaration(
        eventClassSpecification: EventClassSpecification,
        eventGenerator: EventGenerator,
        generationEnvironment: GenerationEnvironment
    ): ClassDeclaration {
        val cache = generationEnvironment.declarationCache
        val checker = eventGenerator.checkHandler
        val logger = eventGenerator.logger

        val eventType = eventClassSpecification.type
        val additionalProperties = eventClassSpecification.additionalProperties
        val extensions = eventClassSpecification.extensions
        //val type: KoresType = genericFromTypeInfo(eventType)
        val classType = eventType.concreteType
        val isItf = classType.isInterface
        val eventTypeDeclaration =
            classType.bindedDefaultResolver.resolveTypeDeclaration().rightOrFail

        val eventTypeLiter = eventType.koresType.xtoStr()
        val isSpecialized = eventType is GenericType && eventType.bounds.isNotEmpty()
        val requiresGenericType = eventType.concreteType.toGeneric.bounds.isNotEmpty()

        if (isSpecialized) {
            logger.log(
                "The construction of event of type '$eventType' is specialized, specialization is deprecated since 1.6",
                MessageType.STANDARD_WARNING
            )
        }

        val name = getName(
            "com.github.koresframework.eventsys.gen.event.${eventTypeLiter}Impl",
            nameCaching
        )

        var classDeclarationBuilder = ClassDeclaration.Builder.builder()
            .modifiers(KoresModifier.PUBLIC)
            .qualifiedName(name)

        val implementations = mutableListOf<Type>()

        val evtGenericType =
            if (isSpecialized) Generic.type(eventType.concreteType).of(eventType.asGeneric.bounds[0].type)
            else eventType.concreteType.toGeneric

        if (isItf) {
            implementations += evtGenericType
            classDeclarationBuilder = classDeclarationBuilder.superClass(Types.OBJECT)
        } else {
            classDeclarationBuilder = classDeclarationBuilder.superClass(evtGenericType)
        }

        if (!isSpecialized && requiresGenericType) {
            classDeclarationBuilder =
                    classDeclarationBuilder.genericSignature(cache[evtGenericType].genericSignature)
        }

        val extensionImplementations = mutableListOf<Type>()

        extensions.forEach {
            it.implement?.let {
                implementations += it
                extensionImplementations += it
            }

            if (it.extensionClass != null && !implementations.contains(ExtensionHolder::class.java))
                implementations += ExtensionHolder::class.java
        }


        classDeclarationBuilder = classDeclarationBuilder.implementations(implementations)

        val plain = classDeclarationBuilder.build()

        val properties =
            getProperties(eventTypeDeclaration, additionalProperties, extensions, cache).map {
                it.copy(inferredType = getPropInferredType(it, isSpecialized, plain, generationEnvironment))
            }

        classDeclarationBuilder = classDeclarationBuilder
            .fields(
                this.genFields(
                    properties,
                    extensions,
                    plain,
                    eventType,
                    requiresGenericType,
                    eventGenerator,
                    cache
                )
            )
            .constructors(
                this.genConstructor(
                    eventTypeDeclaration,
                    eventType,
                    requiresGenericType,
                    isSpecialized,
                    properties
                )
            )

        val methods = this.genMethods(eventType, requiresGenericType, properties).toMutableList()

        methods += this.genToStringMethod(properties, extensions)

        extensions.forEach { ext ->
            ext.extensionClass?.also {
                methods += this.genExtensionMethods(
                    plain,
                    it.bindedDefaultResolver.resolveTypeDeclaration().rightOrFail,
                    generationEnvironment
                )
            }
        }

        // gen getExtension method of ExtensionHolder
        methods += this.genExtensionGetter(extensions, plain)

        // Gen getProperties & getProperty & hasProperty
        methods += this.genPropertyHolderMethods()

        methods += this.genDefaultMethodsImpl(eventTypeDeclaration, methods, cache)

        val classDeclaration = classDeclarationBuilder.methods(methods).build()

        val bridgedClassDeclaration = classDeclarationBuilder.methods(methods).build().let {
            // Use generate bridges method instead of bytecode generator option to
            // Ensure correctness of checker
            it.builder().methods(it.methods + BridgeUtil.genBridgeMethods(it)).build()
        }

        checker.checkDuplicatedMethods(bridgedClassDeclaration.methods)
        this.validateExtensions(extensions, bridgedClassDeclaration, eventGenerator, cache)
        checker.checkImplementation(
            bridgedClassDeclaration.methods,
            eventTypeDeclaration,
            extensions,
            eventGenerator
        )

        return classDeclaration
    }

    private fun genExtensionGetter(extensions: List<ExtensionSpecification>,
                                   ctype: ClassDeclaration): MethodDeclaration {
        val extensionClasses =
            extensions.filter { it.extensionClass != null }.map { it.extensionClass!! }

        val type = Generic.type("T")
        val variableType = Generic.type(Class::class.java).of("T")

        return methodDec().modifiers(KoresModifier.PUBLIC)
            .name("getExtension")
            .genericSignature(GenericSignature.create(type))
            .parameters(parameter(name = "extensionClass", type = variableType))
            .returnType(type)
            .body(Instructions.fromPart(
                if (extensions.isNotEmpty()) switchStm()
                    .switchType(SwitchType.STRING)
                    .value(
                        accessVariable(variableType, "extensionClass").invokeVirtual(
                            Class::class.java,
                            "getCanonicalName",
                            typeSpec(String::class.java),
                            emptyList()
                        )
                    )
                    .cases(
                        extensionClasses.map {
                            val ref = getExtensionFieldRef(it, ctype)
                            caseStm()
                                .value(Literals.STRING(it.canonicalName))
                                .body(
                                    Instructions.fromPart(
                                        returnValue(
                                            type,
                                            cast(
                                                typeOf<Any>(),
                                                type,
                                                fieldAccess().base(ref).build()
                                            )
                                        )
                                    )
                                )
                                .build()
                        } + caseStm().defaultCase().body(
                            Instructions.fromVarArgs(
                                returnValue(
                                    type,
                                    Literals.NULL
                                )
                            )
                        )
                            .build()
                    )
                    .build()
                else returnValue(type, Literals.NULL)
            ))
            .build()
    }

    private fun genFields(
        properties: List<PropertyInfo>,
        extensions: List<ExtensionSpecification>,
        type: ClassDeclaration,
        genericType: Type,
        requiresType: Boolean,
        eventGenerator: EventGenerator,
        cache: DeclarationCache
    ): List<FieldDeclaration> {
        val fields = properties.map {
            val name = it.propertyName

            val modifiers = EnumSet.of(KoresModifier.PRIVATE)

            if (!it.isMutable()) {
                modifiers.add(KoresModifier.FINAL)
            }

            fieldDec().modifiers(modifiers).type(it.inferredType).name(name).build()
        }.toMutableList()

        fields += getPropertyFields()
        fields += genExtensionsFields(extensions, type, eventGenerator, cache)

        if (requiresType) {
            fields += fieldDec()
                .modifiers(KoresModifier.PRIVATE, KoresModifier.FINAL)
                .type(Type::class.java)
                .name(eventTypeFieldName)
                .build()
        }

        return fields

    }

    fun getPropInferredType(property: PropertyInfo,
                            isSpecialized: Boolean,
                            type: Type,
                            generationEnvironment: GenerationEnvironment): Type {
        if ((!property.propertyType.type.isType && !property.propertyType.type.isWildcard)
                || property.propertyType.type.bounds.isNotEmpty()
        ) {
            val infer = inferType(
                property.propertyType.type,
                property.declaringType.concreteType.toGeneric,
                type.asGeneric,
                type.defaultResolver,
                generationEnvironment.genericResolver
            ) { n ->
                property.propertyType.definedParams.types
                    .none { !it.isType && !it.isWildcard && it.name == n }
            }

            return if (infer.`is`(property.propertyType.type))
                property.type
            else infer
        } else {
            return property.type.let {
                if (it is GenericType) it.resolvedType
                else it
            }
        }

        /*if (!isSpecialized
                || property.declaringType.`is`(typeOf<Nothing>())
                || property.declaringType !is GenericType
                || property.declaringType.bounds.isEmpty()
        ) {
            return property.type
        } else {
            val infer = inferType(
                property.propertyType.type,
                property.declaringType.concreteType.toGeneric,
                type.asGeneric,
                type.defaultResolver,
                MixedResolver(null)
            ) { n ->
                property.propertyType.definedParams.types
                    .none { !it.isType && !it.isWildcard && it.name == n }
            }

            return if (infer.`is`(property.propertyType.type))
                property.type
            else infer
        }*/
    }

    private fun getExtensionFieldRef(extensionClass: Type,
                                     type: ClassDeclaration): FieldRef =
        extensionClass.let {
            FieldRef(
                localization = Alias.THIS,
                target = Access.THIS,
                name = "extension_${it.simpleName}",
                type = this.getExtensionFieldType(extensionClass, type)
            )
        }

    private fun getExtensionFieldType(extensionClass: Type,
                                      type: ClassDeclaration): Type {
        val genericExtClass = extensionClass.toGeneric
        val types = type.genericSignature.types

        return if (types.size == genericExtClass.bounds.size) {
            Generic.type(extensionClass).of(*types)
        } else {
            extensionClass
        }
    }

    private fun getExtensionFieldRef(extension: ExtensionSpecification,
                                     type: ClassDeclaration): FieldRef? =
        extension.extensionClass?.let { this.getExtensionFieldRef(it, type) }

    private fun genExtensionsFields(
        extensions: List<ExtensionSpecification>,
        type: ClassDeclaration,
        eventGenerator: EventGenerator,
        cache: DeclarationCache
    ): List<FieldDeclaration> =
        extensions
            .filter { it.extensionClass != null }
            .map {
                it.extensionClass!! // Safe: null filtered above
                val ref = this.getExtensionFieldRef(it, type)!!
                val ctr = eventGenerator.checkHandler
                    .validateExtension(it, cache[it.extensionClass], type, eventGenerator)

                fieldDec()
                    .modifiers(KoresModifier.PRIVATE, KoresModifier.FINAL)
                    .type(ref.type)
                    .name(ref.name)
                    .value(it.extensionClass.invokeConstructor(ctr.typeSpec, listOf(Access.THIS)))
                    .build()
            }

    private fun genConstructor(
        base: TypeDeclaration,
        genericType: Type,
        requiresType: Boolean,
        isSpecialized: Boolean,
        properties: List<PropertyInfo>
    ): ConstructorDeclaration {
        val parameters = mutableListOf<KoresParameter>()

        if (requiresType && !isSpecialized) {
            parameters += parameter(
                name = eventTypeFieldName,
                type = Type::class.java,
                annotations = listOf(
                    runtimeAnnotation(TypeParam::class.java, mapOf()),
                    runtimeAnnotation(
                        Name::class.java,
                        mapOf<String, Any>("value" to eventTypeFieldName)
                    )
                )
            )
        }

        val cancellable = Cancellable::class.java.isAssignableFrom(base)

        properties.forEach {
            val name = it.propertyName

            if (cancellable && name == "cancelled")
                return@forEach

            parameters += parameter(
                name = name,
                type = it.inferredType,
                annotations = listOf(
                    runtimeAnnotation(
                        Name::class.java,
                        mapOf<String, Any>("value" to name)
                    )
                )
            )
        }

        val constructor = ConstructorDeclaration.Builder.builder()
            .modifiers(KoresModifier.PUBLIC)
            .parameters(parameters)
            .body(MutableInstructions.create())
            .build()

        val constructorBody = constructor.body as MutableInstructions

        properties.filter { it.isNotNull }.forEach {
            constructorBody += Objects::class.java.invokeStatic(
                "requireNonNull",
                TypeSpec(Types.OBJECT, listOf(Types.OBJECT)),
                listOf(accessVariable(it.inferredType.koresType, it.propertyName))
            )
        }

        if (requiresType) {
            if (isSpecialized) {
                constructorBody += setFieldValue(
                    Alias.THIS, Access.THIS, Type::class.java, eventTypeFieldName,
                    genericType.toStructure()
                )
            } else {

                constructorBody += Objects::class.java.invokeStatic(
                    "requireNonNull",
                    TypeSpec(Types.OBJECT, listOf(Types.OBJECT)),
                    listOf(accessVariable(Type::class.java, eventTypeFieldName))
                )

                constructorBody += setFieldValue(
                    Alias.THIS, Access.THIS, Type::class.java, eventTypeFieldName,
                    accessVariable(Type::class.java, eventTypeFieldName)
                )
            }

        }

        properties.forEach {
            val valueType: KoresType = it.inferredType.koresType

            constructorBody += if (cancellable && it.propertyName == "cancelled") {
                setFieldValue(Alias.THIS, Access.THIS, valueType, it.propertyName, Literals.FALSE)
            } else {
                setFieldValue(
                    Alias.THIS, Access.THIS, valueType, it.propertyName,
                    accessVariable(valueType, it.propertyName)
                )
            }
        }

        genConstructorPropertiesMap(constructorBody, properties)

        return constructor

    }

    private fun genMethods(
        eventType: Type,
        requiresTypeInfo: Boolean,
        properties: List<PropertyInfo>
    ): List<MethodDeclaration> {

        val methods = mutableListOf<MethodDeclaration>()

        properties.map {
            if (it.hasGetter()) {
                methods += genGetter(it)
            }

            if (it.isMutable()) {
                methods += genSetter(it)
            }
        }

        val toReturn: Instruction = if (requiresTypeInfo)

            accessThisField(
                Type::class.java,
                eventTypeFieldName
            )
        else createGenericType(eventType)

        methods += MethodDeclaration.Builder.builder()
            .annotations(overrideAnnotation())
            .modifiers(KoresModifier.PUBLIC)
            .returnType(Type::class.java)
            .name("get${eventTypeFieldName.capitalize()}")
            .body(
                Instructions.fromPart(
                    returnValue(
                        Type::class.java,
                        toReturn
                    )
                )
            )
            .build()

        return methods
    }

    private fun genToStringMethod(
        properties: List<PropertyInfo>,
        extensions: List<ExtensionSpecification>
    ): MethodDeclaration =
        MethodDeclaration.Builder.builder()
            .annotations(overrideAnnotation())
            .modifiers(KoresModifier.PUBLIC)
            .returnType(Types.STRING)
            .name("toString")
            .body(
                Instructions.fromPart(
                    returnValue(
                        Types.STRING,
                        ConcatHelper.builder()
                            .concat("{")
                            .concat(Literals.STRING("class="))
                            .concat(
                                invokeVirtual(
                                    Class::class.java,
                                    invokeVirtual(
                                        Object::class.java, Access.THIS,
                                        "getClass",
                                        TypeSpec(Class::class.java),
                                        listOf()
                                    ),
                                    "getSimpleName",
                                    TypeSpec(String::class.java),
                                    listOf()
                                )
                            )
                            .concat(Literals.STRING(","))
                            .concat(Literals.STRING("type="))
                            .concat(
                                invokeInterface(
                                    Event::class.java,
                                    Access.THIS,
                                    "getEventType",
                                    TypeSpec(Type::class.java),
                                    listOf()
                                ).invokeToString()
                            )
                            .concat(Literals.STRING(","))
                            .concat(
                                Literals.STRING(
                                    "properties=${properties
                                        .joinToString(
                                            prefix = "[",
                                            postfix = "]"
                                        ) { it.propertyName }}"
                                )
                            )
                            .concat(Literals.STRING(","))
                            .concat(
                                Literals.STRING(
                                    "extensions=${extensions
                                        .joinToString(
                                            prefix = "[",
                                            postfix = "]"
                                        ) { "[impl=${it.implement?.simpleName},ext=${it.extensionClass?.simpleName},residence=${it.residence.residenceToString()}]" }}"
                                )
                            )
                            .concat("}")
                            .build()

                    )
                )
            )
            .build()

    private fun genExtensionMethods(
        type: ClassDeclaration,
        extensionClass: TypeDeclaration,
        generationEnvironment: GenerationEnvironment
    ): List<MethodDeclaration> =
        generationEnvironment.declarationCache.getMethods(extensionClass)
            .filter { (_, it) ->
                it.modifiers.contains(KoresModifier.PUBLIC) && !it.modifiers.contains(
                    KoresModifier.STATIC
                )
            }
            .map { (_, it) ->

                val rtype: Type = it.returnType
                val params: List<KoresParameter> = it.parameters

                val arguments = params.access

                val ref = getExtensionFieldRef(extensionClass, type)

                MethodDeclaration.Builder.builder()
                    .modifiers(KoresModifier.PUBLIC)
                    .name(it.name)
                    .returnType(rtype)
                    .parameters(params)
                    .body(
                        Instructions.fromPart(
                            returnValue(
                                it.returnType.koresType,
                                invokeVirtual(
                                    localization = extensionClass,
                                    target = ref.let {
                                        accessField(
                                            it.localization,
                                            it.target,
                                            it.type,
                                            it.name
                                        )
                                    },
                                    spec = it.typeSpec,
                                    name = it.name,
                                    arguments = arguments
                                )
                            )
                        )
                    )
                    .build()

            }

    private fun genGetter(property: PropertyInfo): List<MethodDeclaration> {

        val name = property.propertyName
        val getterName = property.getterName!!
        val propertyType = property.type

        val methods = mutableListOf<MethodDeclaration>()

        val fieldType = propertyType.koresType
        val inferredType = property.inferredType.koresType

        val castType = getCastType(inferredType)

        val ret: Return = if (!fieldType.isPrimitive && property.isNotNull)
            returnValue(
                fieldType, cast(
                    Types.OBJECT, castType,
                    Objects::class.java.invokeStatic(
                        "requireNonNull",
                        TypeSpec(Types.OBJECT, listOf(Types.OBJECT)),
                        listOf(accessThisField(inferredType, name))
                    )
                )
            )
        else
            returnValue(
                castType,
                cast(inferredType, castType, accessThisField(inferredType, name))
            )


        if (!castType.isConcreteIdEq(fieldType)) {
            methods += MethodDeclaration.Builder.builder()
                .modifiers(EnumSet.of(KoresModifier.PUBLIC))
                .returnType(castType)
                .name(getterName)
                .body(Instructions.fromPart(ret))
                .build()
        } else if (!inferredType.isConcreteIdEq(fieldType)) {
            methods += MethodDeclaration.Builder.builder()
                .modifiers(EnumSet.of(KoresModifier.PUBLIC))
                .returnType(inferredType)
                .name(getterName)
                .body(
                    Instructions.fromPart(
                        returnValue(inferredType, cast(ret.type, inferredType, ret.value))
                    )
                )
                .build()
        } else {
            methods += MethodDeclaration.Builder.builder()
                .modifiers(KoresModifier.PUBLIC)
                .returnType(fieldType)
                .name(getterName)
                .body(
                    Instructions.fromPart(
                        returnValue(
                            fieldType,
                            accessThisField(inferredType, name)
                        )
                    )
                )
                .build()
        }

        return methods
    }


    private fun genSetter(property: PropertyInfo): List<MethodDeclaration> {

        val setterName = property.setterName!!
        val name = property.propertyName
        val propertyType = property.type
        val validator = property.validator
        val methods = mutableListOf<MethodDeclaration>()
        val fieldType = propertyType.koresType

        val inferredType = property.inferredType.koresType


        val base = if (validator == null)
            if (!inferredType.isPrimitive && property.isNotNull)
                Instructions.fromVarArgs(
                    Objects::class.java.invokeStatic(
                        "requireNonNull",
                        TypeSpec(Types.OBJECT, listOf(Types.OBJECT)),
                        listOf(accessVariable(inferredType, name))
                    )
                )
            else
                Instructions.empty()
        else
            Instructions.fromVarArgs(
                accessStaticField(validator, validator, "INSTANCE").invokeInterface(
                    Validator::class.java,
                    "validate",
                    voidTypeSpec(Any::class.java, Property::class.java),
                    listOf(
                        accessVariable(inferredType, name),
                        invokeInterface(
                            PropertyHolder::class.java,
                            Access.THIS,
                            "getProperty",
                            typeSpec(Property::class.java, Class::class.java, String::class.java),
                            listOf(Literals.CLASS(fieldType), Literals.STRING(name))
                        )
                    )
                )
            )

        methods += MethodDeclaration.Builder.builder()
            .modifiers(EnumSet.of(KoresModifier.PUBLIC))
            .returnType(Types.VOID)
            .parameters(parameter(type = fieldType, name = name))
            .name(setterName)
            .body(
                base +
                        setFieldValue(
                            Alias.THIS, Access.THIS, fieldType, name,
                            cast(fieldType, inferredType, accessVariable(fieldType, name))
                        )
            )
            .build()

        val castType = getCastType(fieldType)

        if (castType != fieldType) {
            methods += MethodDeclaration.Builder.builder()
                .modifiers(EnumSet.of(KoresModifier.PUBLIC))
                .returnType(Types.VOID)
                .parameters(parameter(type = castType, name = name))
                .name(setterName)
                .body(
                    base + setFieldValue(
                        Alias.THIS,
                        Access.THIS,
                        fieldType,
                        name,
                        cast(castType, fieldType, accessVariable(castType, name))
                    )
                )
                .build()
        }

        return methods
    }

    private fun genPropertyHolderMethods(): MethodDeclaration {
        return MethodDeclaration.Builder.builder()
            .modifiers(KoresModifier.PUBLIC)
            .name("getProperties")
            .returnType(propertiesFieldType)
            .annotations(runtimeAnnotation(Override::class.java))
            .body(
                Instructions.fromPart(
                    returnValue(
                        propertiesFieldType,
                        accessThisField(propertiesFieldType, propertiesUnmodName)
                    )
                )
            )
            .build()
    }

    private fun validateExtensions(
        extensions: List<ExtensionSpecification>,
        type: ClassDeclaration,
        eventGenerator: EventGenerator,
        cache: DeclarationCache
    ) {

        extensions.forEach { extension ->
            extension.extensionClass?.let {
                eventGenerator.checkHandler.validateExtension(
                    extension,
                    cache[it],
                    type,
                    eventGenerator
                )
            }
        }
    }

    internal fun TypeSpec.concrete() =
        this.copy(returnType = this.returnType.concreteType,
            parameterTypes = this.parameterTypes.map { it.concreteType })

    internal fun genDefaultMethodsImpl(
        baseClass: TypeDeclaration,
        methods: List<MethodDeclaration>,
        cache: DeclarationCache
    ): List<MethodDeclaration> {
        val funcs = cache.getMethods(baseClass)
            .filter { base ->
                methods.none { it.isEqual(base.methodDeclaration) }
            }
            .mapNotNull { base ->
                findImplementation(baseClass, base, cache)?.let { Pair(base, it) }
            }

        return funcs.map {
            val base = it.first
            val baseDeclaration = base.type
            val baseMethod = base.methodDeclaration
            val delegateClass = it.second.first
            val delegate = it.second.second

            val parameters = baseMethod.parameters.mapIndexed { i, _ ->
                parameter(type = delegate.parameters[i + 1].type, name = "arg$i")
            }

            val arguments =
                mutableListOf<Instruction>(Access.THIS) + parameters.map { it.toVariableAccess() }

            val invoke: Instruction = invoke(
                InvokeType.INVOKE_STATIC,
                delegateClass.koresType,
                Access.STATIC,
                delegate.name,
                TypeSpec(delegate.returnType, delegate.parameters.map { it.type }),
                arguments
            ).let {
                if (baseMethod.returnType.`is`(Void.TYPE))
                    it
                else
                    returnValue(baseMethod.returnType, it)
            }

            MethodDeclaration.Builder.builder()
                .annotations(overrideAnnotation())
                .modifiers(KoresModifier.PUBLIC, KoresModifier.BRIDGE)
                .genericSignature(baseMethod.genericSignature)
                .name(baseMethod.name)
                .returnType(baseMethod.returnType)
                .parameters(parameters)
                .body(Instructions.fromPart(invoke))
                .build()

        }
    }

}

const val eventTypeFieldName = "eventType"

const val propertiesFieldName = "_properties"
const val propertiesUnmodName = "_immutable_properties"
val propertiesFieldType = Generic.type(Map::class.java)
    .of(Types.STRING)
    .of(Generic.type(Property::class.java).of(Generic.wildcard()))

fun MethodDeclaration.isAnyMethod(): Boolean =
    (this.name == "toString" && this.parameters.isEmpty() && this.returnType.`is`(Types.STRING))
            || (this.name == "hashCode" && this.parameters.isEmpty() && this.returnType.`is`(Types.INT))
            || (this.name == "equals" && this.parameters.size == 1 && this.returnType.`is`(Types.BOOLEAN))
            || (this.name == "finalize" && this.parameters.isEmpty() && this.returnType.`is`(Types.VOID))

fun MethodDeclaration.isNative(): Boolean =
    this.modifiers.contains(KoresModifier.NATIVE)

fun getProperties(
    type: TypeDeclaration,
    additional: List<PropertyInfo>,
    extensions: List<ExtensionSpecification>,
    cache: DeclarationCache
): List<PropertyInfo> {
    val list = mutableListOf<PropertyInfo>()

    /*!it.isAnyMethod() && !it.isNative()*/
    val methods = cache.getMethods(type)
        .filter { (type, _) -> !type.`is`(typeOf<Any>()) }
        .toMutableList()


    extensions.mapNotNull { it.implement }.forEach {
        /*!it.isAnyMethod() && !it.isNative()*/
        methods += cache.getMethods(it).filter { (type, _) -> !type.`is`(typeOf<Any>()) }
    }

    val extensionClasses = extensions.mapNotNull { it.extensionClass }

    methods.forEach { (type, method) ->

        // Since: 1.1.2: Extensions are allowed to implement properties getter and setter.
        if (extensionClasses.any { hasMethod(it, method, cache) })
            return@forEach

        val name = method.name

        val isGet = name.startsWith("get") && method.parameters.isEmpty()
        val isIs = name.startsWith("is") && method.parameters.isEmpty()
        val isSet = name.startsWith("set") && method.parameters.size == 1

        // Skip PropertyHolder methods
        // We could use method.declaringClass == PropertyHolder::class.java
        // but override methods will return false.
        if (hasMethod(typeOf<PropertyHolder>(), method, cache)
                || hasMethod(typeOf<Event>(), method, cache)
        )
            return@forEach

        if (isGet || isIs || isSet) {
            // hasProperty of PropertyHolder
            // 3 = "get".length & "set".length
            // 2 = "is".length
            val propertyName =
                (if (isGet || isSet) name.substring(3 until name.length) else name.substring(2 until name.length))
                    .decapitalize()

            val propertyType = if (isGet || isIs) method.returnType else method.parameters[0].type

            val genericPropertyType =
                if (isGet || isIs) method.returnType.asGeneric
                else method.parameters[0].type.asGeneric

            if (!list.any { it.propertyName == propertyName }) {

                val setter = getSetter(type, propertyName, propertyType, cache)
                //?: getSetter(method.declaringClass, propertyName, propertyType)

                val getter = getGetter(type, propertyName, cache)
                //?: getGetter(method.declaringClass, propertyName)

                val getterName = getter?.name
                val setterName = setter?.name

                val validator = setter?.annotations
                    ?.firstOrNull { it.type.`is`(typeOf<Validate>()) }
                    ?.values?.get("value") as? Type

                val isNotNull =
                    setter?.parameters?.firstOrNull()?.annotations?.any { it.type.`is`(typeOf<NotNullValue>()) } == true
                            || getter?.annotations?.any { it.type.`is`(typeOf<NotNullValue>()) } == true
                            || method.annotations.any { it.type.`is`(typeOf<NotNullValue>()) }

                list += PropertyInfo(
                    type.toGeneric,
                    propertyName,
                    getterName,
                    setterName,
                    propertyType,
                    isNotNull,
                    validator,
                    PropertyType(
                        genericPropertyType,
                        method.genericSignature
                    )
                )
            }

        }

    }

    additional.forEach { ad ->
        if (!list.any { it.propertyName == ad.propertyName })
            list.add(ad)
    }

    return list
}

fun getPropertyFields(): List<FieldDeclaration> {
    return listOf(
        FieldDeclaration.Builder.builder()
            .modifiers(KoresModifier.PRIVATE, KoresModifier.FINAL)
            .type(propertiesFieldType)
            .name(propertiesFieldName)
            .value(HashMap::class.java.invokeConstructor())
            .build(),
        FieldDeclaration.Builder.builder()
            .modifiers(KoresModifier.PRIVATE, KoresModifier.FINAL)
            .type(propertiesFieldType)
            .name(propertiesUnmodName)
            .value(
                Collections::class.java.invokeStatic(
                    "unmodifiableMap",
                    typeSpec(Map::class.java, Map::class.java),
                    listOf(accessThisField(propertiesFieldType, propertiesFieldName))
                )
            )
            .build()
    )
}

private fun getSetter(
    type: TypeDeclaration,
    name: String,
    propertyType: Type,
    cache: DeclarationCache
): MethodDeclaration? {

    val capitalized = name.capitalize()
    val setterName = "set$capitalized"

    return cache.getMethods(type).map { (_, it) -> it }.firstOrNull {
        it.name == setterName && it.parameters.singleOrNull()?.type?.`is`(
            propertyType
        ) == true
    }
}

private fun getGetter(
    type: TypeDeclaration,
    name: String,
    cache: DeclarationCache
): MethodDeclaration? {

    val capitalized = name.capitalize()
    val getterName = "get$capitalized"
    val isName = "is$capitalized"

    return cache.getMethods(type).map { (_, it) -> it }.firstOrNull {
        it.name == getterName || it.name == isName
    }
}

private fun hasMethod(klass: Type, method: MethodDeclaration, cache: DeclarationCache): Boolean =
    cache.getMethods(klass.koresType).any { (_, it) -> it.isEqual(method) }

fun genConstructorPropertiesMap(
    constructorBody: MutableInstructions,
    properties: List<PropertyInfo>
) {
    val accessMap = accessThisField(propertiesFieldType, propertiesFieldName)

    properties.forEach {
        val realType = it.type
        val inferredType = it.inferredType

        constructorBody += if (!inferredType.`is`(realType)) {
            invokePut(
                accessMap,
                com.github.jonathanxd.kores.literal.Literals.STRING(it.propertyName),
                propertyToSProperty(it, inferredType)
            )
        } else {
            invokePut(
                accessMap,
                com.github.jonathanxd.kores.literal.Literals.STRING(it.propertyName),
                propertyToSProperty(it, realType)
            )
        }


    }

}

fun invokePut(accessMap: Instruction, vararg arguments: Instruction): Instruction =
    invokeInterface(
        Map::class.java,
        accessMap,
        "put",
        typeSpec(Any::class.java, Any::class.java, Any::class.java),
        listOf(*arguments)
    )

private fun propertyToSProperty(property: PropertyInfo, registryType: Type): Instruction {

    val hasGetter = property.hasGetter()
    val hasSetter = property.hasSetter()

    val realType = property.type

    val typeToInvoke = getTypeToInvoke(hasGetter, hasSetter, realType).koresType

    val arguments = mutableListOf<Instruction>()
    val argumentTypes = mutableListOf<KoresType>()

    if (!property.type.isPrimitive) {
        arguments.add(Literals.CLASS(registryType.koresType))
        argumentTypes.add(Types.CLASS)
    }

    if (hasGetter) {
        val supplierInfo = getSupplierType(realType)
        val supplierType = supplierInfo.second

        arguments += invokeGetter(realType, supplierInfo, property)
        argumentTypes += supplierType.koresType
    }

    if (hasSetter) {
        val consumerType = getConsumerType(realType).koresType

        arguments += invokeSetter(realType, consumerType, property)
        argumentTypes += consumerType
    }

    val typeSpec = TypeSpec(Types.VOID, argumentTypes)

    return typeToInvoke.invokeConstructor(typeSpec, arguments)
}

private fun invokeGetter(
    type: Type,
    supplierInfo: Pair<String, Type>,
    property: PropertyInfo
): Instruction {
    val propertyType = property.type
    val getterName = property.getterName!!

    val supplierType = supplierInfo.second.koresType
    val realType = getCastType(propertyType).koresType
    val rtype = if (type.isPrimitive) realType /*type.koresType*/ else Types.OBJECT

    val spec = MethodInvokeSpec(
        InvokeType.INVOKE_VIRTUAL,
        MethodTypeSpec(
            Alias.THIS,
            getterName,
            typeSpec(realType)
        )
    )

    return InvokeDynamic.LambdaMethodRef.Builder.builder()
        .methodRef(spec)
        .target(Access.THIS)
        .baseSam(MethodTypeSpec(supplierType, supplierInfo.first, typeSpec(rtype)))
        .expectedTypes(typeSpec(realType /*propertyType*/))
        .build()
}

private fun invokeSetter(
    type: Type,
    consumerType: KoresType,
    property: PropertyInfo
): Instruction {
    val setterName = property.setterName!!

    val realType = getCastType(property.type).koresType
    val ptype = if (type.isPrimitive) realType/*type.koresType*/ else Types.OBJECT

    val spec = MethodInvokeSpec(
        InvokeType.INVOKE_VIRTUAL,
        MethodTypeSpec(
            Alias.THIS,
            setterName,
            typeSpec(Types.VOID, realType)
        )
    )

    return InvokeDynamic.LambdaMethodRef.Builder.builder()
        .methodRef(spec)
        .target(Access.THIS)
        .arguments()
        .baseSam(MethodTypeSpec(consumerType, "accept", constructorTypeSpec(ptype)))
        .expectedTypes(constructorTypeSpec(realType/*propertyType*/))
        .build()
}

private fun getTypeToInvoke(hasGetter: Boolean, hasSetter: Boolean, type: Type): Class<*> =
    if (hasGetter && hasSetter) when (type.identification) {
        java.lang.Byte.TYPE.identification,
        java.lang.Short.TYPE.identification,
        java.lang.Character.TYPE.identification,
        java.lang.Integer.TYPE.identification -> IntGSProperty.Impl::class.java
        java.lang.Boolean.TYPE.identification -> BooleanGSProperty.Impl::class.java
        java.lang.Double.TYPE.identification,
        java.lang.Float.TYPE.identification -> DoubleGSProperty.Impl::class.java
        java.lang.Long.TYPE.identification -> LongGSProperty.Impl::class.java
        else -> GSProperty.Impl::class.java
    } else if (hasGetter) when (type.identification) {
        java.lang.Byte.TYPE.identification,
        java.lang.Short.TYPE.identification,
        Character.TYPE.identification,
        java.lang.Integer.TYPE.identification -> IntGetterProperty.Impl::class.java
        java.lang.Boolean.TYPE.identification -> BooleanGetterProperty.Impl::class.java
        java.lang.Double.TYPE.identification,
        java.lang.Float.TYPE.identification -> DoubleGetterProperty.Impl::class.java
        java.lang.Long.TYPE.identification -> LongGetterProperty.Impl::class.java
        else -> GetterProperty.Impl::class.java
    } else if (hasSetter) when (type.identification) {
        java.lang.Byte.TYPE.identification,
        java.lang.Short.TYPE.identification,
        java.lang.Character.TYPE.identification,
        java.lang.Integer.TYPE.identification -> IntSetterProperty.Impl::class.java
        java.lang.Boolean.TYPE.identification -> BooleanSetterProperty.Impl::class.java
        java.lang.Double.TYPE.identification,
        java.lang.Float.TYPE.identification -> DoubleSetterProperty.Impl::class.java
        java.lang.Long.TYPE.identification -> LongSetterProperty.Impl::class.java
        else -> SetterProperty.Impl::class.java
    } else when (type.identification) {
        java.lang.Byte.TYPE.identification,
        java.lang.Short.TYPE.identification,
        java.lang.Character.TYPE.identification,
        java.lang.Integer.TYPE.identification -> IntProperty.Impl::class.java
        java.lang.Boolean.TYPE.identification -> BooleanProperty.Impl::class.java
        java.lang.Double.TYPE.identification,
        java.lang.Float.TYPE.identification -> DoubleProperty.Impl::class.java
        java.lang.Long.TYPE.identification -> LongProperty.Impl::class.java
        else -> Property.Impl::class.java
    }

private fun getSupplierType(type: Type): Pair<String, Class<*>> = when (type.identification) {
    java.lang.Byte.TYPE.identification,
    java.lang.Short.TYPE.identification,
    java.lang.Character.TYPE.identification,
    java.lang.Integer.TYPE.identification -> "getAsInt" to IntSupplier::class.java
    java.lang.Boolean.TYPE.identification -> "getAsBoolean" to BooleanSupplier::class.java
    java.lang.Double.TYPE.identification,
    java.lang.Float.TYPE.identification -> "getAsDouble" to DoubleSupplier::class.java
    java.lang.Long.TYPE.identification -> "getAsLong" to LongSupplier::class.java
    else -> "get" to Supplier::class.java
}

private fun getConsumerType(type: Type): Class<*> = when (type.identification) {
    java.lang.Byte.TYPE.identification,
    java.lang.Short.TYPE.identification,
    java.lang.Character.TYPE.identification,
    java.lang.Integer.TYPE.identification -> IntConsumer::class.java
    java.lang.Boolean.TYPE.identification -> BooleanConsumer::class.java
    java.lang.Double.TYPE.identification,
    java.lang.Float.TYPE.identification -> DoubleConsumer::class.java
    java.lang.Long.TYPE.identification -> LongConsumer::class.java
    else -> Consumer::class.java
}

/*
private fun getCastType(type: Type): Class<*> = when (type.identification) {
    java.lang.Byte.TYPE.identification, // -> java.lang.Byte.TYPE // Temporary workaround until Kores-BytecodeWriter:hotfix3
    java.lang.Short.TYPE.identification, // -> java.lang.Short.TYPE // Temporary workaround until Kores-BytecodeWriter:hotfix3
    java.lang.Character.TYPE.identification, // -> java.lang.Character.TYPE // Temporary workaround until Kores-BytecodeWriter:hotfix3
    java.lang.Integer.TYPE.identification -> java.lang.Integer.TYPE
    java.lang.Boolean.TYPE.identification -> java.lang.Boolean.TYPE
    java.lang.Double.TYPE.identification,
    java.lang.Float.TYPE.identification -> java.lang.Double.TYPE
    java.lang.Long.TYPE.identification -> java.lang.Long.TYPE
    else -> type
}
*/

private fun getCastType(koresType: Type): Type = when (koresType.identification) {
    Types.BYTE.identification,
    Types.SHORT.identification,
    Types.CHAR.identification,
    Types.INT.identification -> Types.INT
    Types.BOOLEAN.identification -> Types.BOOLEAN
    Types.DOUBLE.identification,
    Types.FLOAT.identification -> Types.DOUBLE
    Types.LONG.identification -> Types.LONG
    else -> koresType
}