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
package com.github.projectsandstone.eventsys.gen.event

import com.github.jonathanxd.codeapi.CodeInstruction
import com.github.jonathanxd.codeapi.CodeSource
import com.github.jonathanxd.codeapi.MutableCodeSource
import com.github.jonathanxd.codeapi.Types
import com.github.jonathanxd.codeapi.base.*
import com.github.jonathanxd.codeapi.bytecode.CHECK
import com.github.jonathanxd.codeapi.bytecode.VISIT_LINES
import com.github.jonathanxd.codeapi.bytecode.VisitLineType
import com.github.jonathanxd.codeapi.bytecode.processor.BytecodeProcessor
import com.github.jonathanxd.codeapi.common.FieldRef
import com.github.jonathanxd.codeapi.common.MethodTypeSpec
import com.github.jonathanxd.codeapi.factory.*
import com.github.jonathanxd.codeapi.generic.GenericSignature
import com.github.jonathanxd.codeapi.literal.Literals
import com.github.jonathanxd.codeapi.type.CodeType
import com.github.jonathanxd.codeapi.type.Generic
import com.github.jonathanxd.codeapi.type.PlainCodeType
import com.github.jonathanxd.codeapi.util.*
import com.github.jonathanxd.codeapi.util.conversion.access
import com.github.jonathanxd.codeapi.util.conversion.toInvocation
import com.github.jonathanxd.codeapi.util.conversion.toVariableAccess
import com.github.jonathanxd.codeapi.util.conversion.typeSpec
import com.github.jonathanxd.iutils.type.TypeInfo
import com.github.projectsandstone.eventsys.Debug
import com.github.projectsandstone.eventsys.common.ExtensionHolder
import com.github.projectsandstone.eventsys.event.Cancellable
import com.github.projectsandstone.eventsys.event.Event
import com.github.projectsandstone.eventsys.event.annotation.*
import com.github.projectsandstone.eventsys.event.property.*
import com.github.projectsandstone.eventsys.event.property.primitive.*
import com.github.projectsandstone.eventsys.gen.GeneratedEventClass
import com.github.projectsandstone.eventsys.gen.genericFromTypeInfo
import com.github.projectsandstone.eventsys.gen.save.ClassSaver
import com.github.projectsandstone.eventsys.logging.LoggerInterface
import com.github.projectsandstone.eventsys.logging.MessageType
import com.github.projectsandstone.eventsys.reflect.findImplementation
import com.github.projectsandstone.eventsys.reflect.getName
import com.github.projectsandstone.eventsys.reflect.isEqual
import com.github.projectsandstone.eventsys.util.BooleanConsumer
import com.github.projectsandstone.eventsys.util.fail
import com.github.projectsandstone.eventsys.validation.Validator
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.lang.reflect.Modifier
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
 */
internal object EventClassGenerator {
    private val cached: MutableMap<EventClassSpecification<*>, Class<*>> = mutableMapOf()

    val propertiesFieldName = "#properties"
    val propertiesUnmodName = "immutable#properties"
    val propertiesFieldType = Generic.type(Map::class.java)
            .of(Types.STRING)
            .of(Property::class.java)

    private fun TypeInfo<*>.toStr(): String {
        if (this.related.isEmpty()) {
            return this.toFullString()
        } else {
            val base = StringBuilder(this.typeClass.name)

            base.append("_of_")
            this.related.forEach {
                base.append(it.toFullString()
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

    @Suppress("UNCHECKED_CAST")
    fun <T : Event> genImplementation(eventClassSpecification: EventClassSpecification<T>, logger: LoggerInterface): Class<T> {

        if (cached.containsKey(eventClassSpecification)) {
            return cached[eventClassSpecification]!! as Class<T>
        }

        val typeInfo = eventClassSpecification.typeInfo
        val additionalProperties = eventClassSpecification.additionalProperties
        val extensions = eventClassSpecification.extensions
        val type: CodeType = genericFromTypeInfo(typeInfo)
        val classType = typeInfo.typeClass
        val isItf = classType.isInterface

        val typeInfoLiter = typeInfo.toStr()

        val name = getName("${typeInfoLiter}Impl")

        var classDeclarationBuilder = ClassDeclaration.Builder.builder()
                .modifiers(CodeModifier.PUBLIC)
                .qualifiedName(name)

        val implementations = mutableListOf<Type>()

        if (isItf) {
            implementations += type
            classDeclarationBuilder = classDeclarationBuilder.superClass(Types.OBJECT)
        } else
            classDeclarationBuilder = classDeclarationBuilder.superClass(type)

        val extensionImplementations = mutableListOf<Class<*>>()

        extensions.forEach {
            it.implement?.let {
                implementations += it
                extensionImplementations += it

                if (!implementations.contains(ExtensionHolder::class.java))
                    implementations += ExtensionHolder::class.java
            }
        }

        val properties = this.getProperties(classType, additionalProperties, extensions)

        classDeclarationBuilder = classDeclarationBuilder.implementations(implementations)

        val plain = PlainCodeType(type = name,
                isInterface = false,
                superclass_ = { classDeclarationBuilder.superClass },
                superinterfaces_ = { classDeclarationBuilder.implementations })

        classDeclarationBuilder = classDeclarationBuilder
                .fields(this.genFields(properties, extensions, plain, logger))
                .constructors(this.genConstructor(classType, properties))

        val methods = this.genMethods(classType, properties, logger).toMutableList()

        extensions.forEach { ext ->
            ext.extensionClass?.let {
                methods += this.genExtensionMethods(it)
            }
        }

        // gen getExtension method of ExtensionHolder
        methods += this.genExtensionGetter(extensions)

        // Gen getProperties & getProperty & hasProperty
        methods += this.genPropertyHolderMethods()

        val classDeclaration = classDeclarationBuilder.methods(methods).build()

        this.checkDuplicates(classDeclaration)
        this.validateExtensions(extensions, classDeclaration, logger)
        this.checkMethodImplementations(classType, classDeclaration, extensions, logger)


        val generator = BytecodeProcessor()

        generator.options.set(VISIT_LINES, VisitLineType.FOLLOW_CODE_SOURCE)
        generator.options.set(CHECK, false)

        val bytecodeClass = generator.process(classDeclaration)[0]

        val bytes = bytecodeClass.bytecode
        val disassembled = lazy { bytecodeClass.disassembledCode }

        val generatedEventClass = EventGenClassLoader.defineClass(classDeclaration, bytes, disassembled) as GeneratedEventClass<T>

        if (Debug.EVENT_GEN_DEBUG) {
            ClassSaver.save("eventgen", generatedEventClass)
        }

        return generatedEventClass.javaClass.let {
            this.cached.put(eventClassSpecification, it)
            it
        }
    }

    private fun genExtensionGetter(extensions: List<ExtensionSpecification>): MethodDeclaration {
        val extensionClasses = extensions.filter { it.extensionClass != null }.map { it.extensionClass!! }

        val type = Generic.type("T")
        val variableType = Generic.type(Class::class.java).of("T")

        return methodDec().modifiers(CodeModifier.PUBLIC)
                .name("getExtension")
                .genericSignature(GenericSignature.create(type))
                .parameters(parameter(name = "extensionClass", type = variableType))
                .returnType(type)
                .body(source(
                        switchStm()
                                .switchType(SwitchType.STRING)
                                .value(accessVariable(variableType, "extensionClass").invokeVirtual(
                                        Class::class.java,
                                        "getCanonicalName",
                                        typeSpec(String::class.java),
                                        emptyList()))
                                .cases(
                                        extensionClasses.map {
                                            val ref = getExtensionFieldRef(it)
                                            caseStm()
                                                    .value(Literals.STRING(it.canonicalName))
                                                    .body(source(returnValue(type, fieldAccess().base(ref).build())))
                                                    .build()
                                        } + caseStm().defaultCase().body(source(returnValue(type, Literals.NULL))).build()
                                )
                                .build()
                ))
                .build()
    }

    private fun checkDuplicates(holder: ElementsHolder) {

        val methodList = mutableListOf<MethodDeclaration>()

        holder.methods.forEach { outer ->
            if (methodList.any {
                outer.name == it.name
                        && outer.returnType.`is`(it.returnType)
                        && outer.parameters.map { it.type } == it.parameters.map { it.type }
            })
                // I will not use logging here, class loader will fail if duplicated methods are found
                throw IllegalStateException("Duplicated method: ${outer.name}")
            else
                methodList += outer
        }
    }

    private fun genFields(properties: List<PropertyInfo>,
                          extensions: List<ExtensionSpecification>,
                          type: Type,
                          logger: LoggerInterface): List<FieldDeclaration> {
        return properties.map {
            val name = it.propertyName

            val modifiers = EnumSet.of(CodeModifier.PRIVATE)

            if (!it.isMutable()) {
                modifiers.add(CodeModifier.FINAL)
            }

            fieldDec().modifiers(modifiers).type(it.type).name(name).build()
        } + genPropertyField() + genExtensionsFields(extensions, type, logger)


    }

    private fun getExtensionFieldRef(extensionClass: Class<*>): FieldRef =
            extensionClass.let {
                FieldRef(localization = Alias.THIS,
                        target = Access.THIS,
                        name = "extension#${it.simpleName}",
                        type = it)
            }

    private fun getExtensionFieldRef(extension: ExtensionSpecification): FieldRef? =
            extension.extensionClass?.let(this::getExtensionFieldRef)

    private fun genExtensionsFields(extensions: List<ExtensionSpecification>,
                                    type: Type,
                                    logger: LoggerInterface): List<FieldDeclaration> =
            extensions
                    .filter { it.extensionClass != null }
                    .map {
                        it.extensionClass!! // Safe: null filtered above
                        val ref = this.getExtensionFieldRef(it)!!
                        val ctr = findConstructor(it, it.extensionClass, type, logger)
                        fieldDec()
                                .modifiers(CodeModifier.PRIVATE, CodeModifier.FINAL)
                                .type(ref.type)
                                .name(ref.name)
                                .value(it.extensionClass.invokeConstructor(ctr.typeSpec, listOf(Access.THIS)))
                                .build()
                    }


    private fun genPropertyField(): List<FieldDeclaration> {
        return listOf(FieldDeclaration.Builder.builder()
                .modifiers(CodeModifier.PRIVATE, CodeModifier.FINAL)
                .type(propertiesFieldType)
                .name(propertiesFieldName)
                .value(HashMap::class.java.invokeConstructor())
                .build(),
                FieldDeclaration.Builder.builder()
                        .modifiers(CodeModifier.PRIVATE, CodeModifier.FINAL)
                        .type(propertiesFieldType)
                        .name(propertiesUnmodName)
                        .value(Collections::class.java.invokeStatic("unmodifiableMap",
                                typeSpec(Map::class.java, Map::class.java),
                                listOf(accessThisField(propertiesFieldType, propertiesFieldName))))
                        .build())
    }

    private fun genConstructor(base: Class<*>, properties: List<PropertyInfo>): ConstructorDeclaration {
        val parameters = mutableListOf<CodeParameter>()

        val cancellable = Cancellable::class.java.isAssignableFrom(base)

        properties.forEach {
            val name = it.propertyName

            if (cancellable && name == "cancelled")
                return@forEach

            parameters += parameter(
                    name = name,
                    type = it.type,
                    annotations = listOf(visibleAnnotation(Name::class.java, mapOf<String, Any>("value" to name)))
            )
        }

        val constructor = ConstructorDeclaration.Builder.builder()
                .modifiers(CodeModifier.PUBLIC)
                .parameters(parameters)
                .body(MutableCodeSource.create())
                .build()

        val constructorBody = constructor.body as MutableCodeSource

        properties.forEach {
            val valueType: CodeType = it.type.codeType

            if (cancellable && it.propertyName == "cancelled") {
                constructorBody += setFieldValue(Alias.THIS, Access.THIS, valueType, it.propertyName, Literals.FALSE)
            } else {
                constructorBody += setFieldValue(Alias.THIS, Access.THIS, valueType, it.propertyName, accessVariable(valueType, it.propertyName))
            }
        }

        genConstructorPropertiesMap(constructorBody, properties)

        return constructor

    }

    private fun genConstructorPropertiesMap(constructorBody: MutableCodeSource, properties: List<PropertyInfo>) {
        val accessMap = accessThisField(propertiesFieldType, propertiesFieldName)

        properties.forEach {
            constructorBody += this.invokePut(accessMap,
                    com.github.jonathanxd.codeapi.literal.Literals.STRING(it.propertyName),
                    this.propertyToSProperty(it))
        }

    }

    private fun invokePut(accessMap: CodeInstruction, vararg arguments: CodeInstruction): CodeInstruction {
        return invokeInterface(Map::class.java, accessMap, "put", typeSpec(Any::class.java, Any::class.java, Any::class.java), listOf(*arguments))
    }

    private fun propertyToSProperty(property: PropertyInfo): CodeInstruction {

        val hasGetter = property.hasGetter()
        val hasSetter = property.hasSetter()

        val typeToInvoke = this.getTypeToInvoke(hasGetter, hasSetter, property.type).codeType

        val arguments = mutableListOf<CodeInstruction>()
        val argumentTypes = mutableListOf<CodeType>()

        if (!property.type.isPrimitive) {
            arguments.add(Literals.CLASS(property.type))
            argumentTypes.add(Types.CLASS)
        }

        if (hasGetter) {
            val supplierInfo = this.getSupplierType(property.type)
            val supplierType = supplierInfo.second

            arguments += this.invokeGetter(property.type, supplierInfo, property)
            argumentTypes += supplierType.codeType
        }

        if (hasSetter) {
            val consumerType = this.getConsumerType(property.type).codeType

            arguments += this.invokeSetter(property.type, consumerType, property)
            argumentTypes += consumerType
        }

        val typeSpec = TypeSpec(Types.VOID, argumentTypes)

        return typeToInvoke.invokeConstructor(typeSpec, arguments)
    }

    private fun invokeGetter(type: Class<*>, supplierInfo: Pair<String, Class<*>>, property: PropertyInfo): CodeInstruction {
        val propertyType = property.type
        val getterName = property.getterName!!

        val supplierType = supplierInfo.second.codeType
        val realType = this.getCastType(propertyType).codeType
        val rtype = if (type.isPrimitive) realType /*type.codeType*/ else Types.OBJECT

        val invocation = invoke(InvokeType.INVOKE_VIRTUAL,
                Alias.THIS,
                Access.THIS,
                getterName,
                typeSpec(realType/*propertyType*/),
                mutableListOf()
        )

        return InvokeDynamic.LambdaMethodRef.Builder.builder()
                .invocation(invocation)
                .baseSam(MethodTypeSpec(supplierType, supplierInfo.first, typeSpec(rtype)))
                .expectedTypes(typeSpec(realType /*propertyType*/))
                .build()
    }

    private fun invokeSetter(type: Class<*>, consumerType: CodeType, property: PropertyInfo): CodeInstruction {
        val setterName = property.setterName!!

        val realType = this.getCastType(property.type).codeType
        val ptype = if (type.isPrimitive) realType/*type.codeType*/ else Types.OBJECT

        val invocation: MethodInvocation

        invocation = invokeVirtual(Alias.THIS, Access.THIS, setterName,
                TypeSpec(Types.VOID, listOf(realType/*propertyType*/)),
                emptyList()
        )

        return InvokeDynamic.LambdaMethodRef.Builder.builder()
                .invocation(invocation)
                .baseSam(MethodTypeSpec(consumerType, "accept", constructorTypeSpec(ptype)))
                .expectedTypes(constructorTypeSpec(realType/*propertyType*/))
                .build()
    }

    private fun genMethods(type: Class<*>,
                           properties: List<PropertyInfo>,
                           logger: LoggerInterface): List<MethodDeclaration> {

        val methods = mutableListOf<MethodDeclaration>()

        methods += genDefaultMethodsImpl(type)

        properties.map {
            if (it.hasGetter()) {
                methods += genGetter(it)
            }

            if (it.isMutable()) {
                methods += genSetter(it)
            }
        }

        return methods
    }

    private fun genExtensionMethods(extensionClass: Class<*>): List<MethodDeclaration> {
        return extensionClass.declaredMethods
                .filter { Modifier.isPublic(it.modifiers) && !Modifier.isStatic(it.modifiers) }
                .map {

                    val parameters = it.parameters.mapIndexed { index, parameter ->
                        parameter(type = parameter.parameterizedType.codeType, name = "arg$index")
                    }.filterNotNull()

                    val arguments = parameters.access

                    val ref = getExtensionFieldRef(extensionClass)

                    MethodDeclaration.Builder.builder()
                            .modifiers(CodeModifier.PUBLIC)
                            .name(it.name)
                            .returnType(it.genericReturnType.codeType)
                            .parameters(parameters)
                            .body(source(
                                    returnValue(it.returnType.codeType, it.toInvocation(
                                            InvokeType.INVOKE_VIRTUAL,
                                            ref.let { accessField(it.localization, it.target, it.type, it.name) },
                                            arguments))
                            ))
                            .build()

                }
    }

    private fun genGetter(property: PropertyInfo): List<MethodDeclaration> {

        val name = property.propertyName
        val getterName = property.getterName!!
        val propertyType = property.type

        val methods = mutableListOf<MethodDeclaration>()

        val fieldType = propertyType.codeType

        methods += MethodDeclaration.Builder.builder()
                .modifiers(CodeModifier.PUBLIC)
                .returnType(fieldType)
                .name(getterName)
                .body(source(returnValue(fieldType, accessThisField(fieldType, name))))
                .build()

        val castType = this.getCastType(fieldType)

        val ret: CodeInstruction = if(!fieldType.isPrimitive && property.isNotNull)
            returnValue(castType, cast(Types.OBJECT, castType,
                    Objects::class.java.invokeStatic(
                            "requireNonNull",
                            TypeSpec(Types.OBJECT, listOf(Types.OBJECT)),
                            listOf(accessThisField(fieldType, name))
                    )
            ))

        else
            returnValue(castType,
                    cast(fieldType, castType, accessThisField(fieldType, name))
            )


        if (castType != fieldType) {
            methods += MethodDeclaration.Builder.builder()
                    .modifiers(EnumSet.of(CodeModifier.PUBLIC))
                    .returnType(castType)
                    .name(getterName)
                    .body(source(
                            ret
                    ))
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
        val fieldType = propertyType.codeType


        val base = if (validator == null)
            if (!fieldType.isPrimitive && property.isNotNull)
                CodeSource.fromVarArgs(
                        Objects::class.java.invokeStatic(
                                "requireNonNull",
                                TypeSpec(Types.OBJECT, listOf(Types.OBJECT)),
                                listOf(accessVariable(fieldType, name))
                        )
                )
            else
                CodeSource.empty()
        else
            CodeSource.fromVarArgs(
                    accessStaticField(validator, validator, "INSTANCE").invokeInterface(Validator::class.java,
                            "validate",
                            voidTypeSpec(Any::class.java, Property::class.java),
                            listOf(accessVariable(fieldType, name),
                                    invokeInterface(
                                            PropertyHolder::class.java,
                                            Access.THIS,
                                            "getProperty",
                                            typeSpec(Property::class.java, Class::class.java, String::class.java),
                                            listOf(Literals.CLASS(fieldType), Literals.STRING(name))
                                    )))
            )

        methods += MethodDeclaration.Builder.builder()
                .modifiers(EnumSet.of(CodeModifier.PUBLIC))
                .returnType(Types.VOID)
                .parameters(parameter(type = fieldType, name = name))
                .name(setterName)
                .body(base + setFieldValue(Alias.THIS, Access.THIS, fieldType, name, accessVariable(fieldType, name)))
                .build()

        val castType = this.getCastType(fieldType)

        if (castType != fieldType) {
            methods += MethodDeclaration.Builder.builder()
                    .modifiers(EnumSet.of(CodeModifier.PUBLIC))
                    .returnType(Types.VOID)
                    .parameters(parameter(type = castType, name = name))
                    .name(setterName)
                    .body(base + setFieldValue(Alias.THIS, Access.THIS, fieldType, name, cast(castType, fieldType, accessVariable(castType, name))))
                    .build()
        }

        return methods
    }

    private fun genPropertyHolderMethods(): MethodDeclaration {
        return MethodDeclaration.Builder.builder()
                .modifiers(CodeModifier.PUBLIC)
                .name("getProperties")
                .returnType(propertiesFieldType)
                .annotations(visibleAnnotation(Override::class.java))
                .body(source(
                        returnValue(propertiesFieldType, accessThisField(propertiesFieldType, propertiesUnmodName))
                ))
                .build()
    }

    private fun getSetter(type: Class<*>, name: String, propertyType: Class<*>): Method? {

        val capitalized = name.capitalize()

        return try {
            type.getMethod("set$capitalized", propertyType)
        } catch (t: Throwable) {
            null
        }
    }

    private fun getGetter(type: Class<*>, name: String): Method? {

        val capitalized = name.capitalize()

        return try {
            type.getMethod("is$capitalized")
        } catch (t: Throwable) {
            null
        } ?: try {
            type.getMethod("get$capitalized")
        } catch (t: Throwable) {
            null
        }
    }

    private fun validateExtensions(extensions: List<ExtensionSpecification>,
                                   type: CodeType,
                                   logger: LoggerInterface) {
        extensions.forEach { extension ->
            extension.extensionClass?.let {
                findConstructor(extension, it, type, logger)
            }
        }
    }

    private fun findConstructor(extension: ExtensionSpecification,
                                extensionClass: Class<*>,
                                type: Type,
                                logger: LoggerInterface): Constructor<*> {

        val resolver = type.defaultResolver
        val foundsCtr = mutableListOf<Constructor<*>>()

        extensionClass.declaredConstructors.forEach {
            if (it.parameterCount == 1) {
                if (resolver.isAssignableFrom(it.parameterTypes.single(), type))
                    return it
                else
                    foundsCtr += it
            }
        }

        logger.log("Provided extension class '${extensionClass.canonicalName}' (spec: '$extension') does not have a single-arg constructor with an argument which receives a '${type.canonicalName}' (or a super type). Found single-arg constructors: ${foundsCtr.joinToString { it.parameterTypes.joinToString(prefix = "(", postfix = ")") { it.simpleName } }}.", MessageType.INVALID_EXTENSION)
        fail()
    }

    internal fun checkMethodImplementations(type: Class<*>,
                                            holder: ElementsHolder,
                                            extensions: List<ExtensionSpecification>,
                                            logger: LoggerInterface) {
        val implementedMethods: List<MethodDeclaration> = holder.methods
        val unimplMethods = mutableListOf<String>()

        type.methods.forEach { method ->
            val name = method.name

            val hasImpl = implementedMethods.any {
                it.name == name
                        && it.returnType.canonicalName == method.returnType.canonicalName
                        && it.parameters.map { it.type.canonicalName } == method.parameterTypes.map { it.canonicalName }
            }

            if (!hasImpl && !method.isDefault) {

                if(!method.isAnnotationPresent(SuppressCheck::class.java)
                        || !method.getDeclaredAnnotation(SuppressCheck::class.java)
                        .value.contains(Check.IMPLEMENTATION))
                unimplMethods += "$method"
            }
        }

        if (unimplMethods.isNotEmpty()) {
            val classes = extensions.filter { it.extensionClass != null }.map { it.extensionClass!! }

            val messages = mutableListOf<String>()

            messages += ""
            messages += "Following methods was not implemented:"
            messages += ""

            unimplMethods.forEach {
                messages += "  $it"
            }

            messages += ""
            if (classes.isEmpty()) messages += "Provide an extension which implement them."
            else {
                messages += "Provide an extension which implement them or add implementation one of existing extensions:"
                messages += "${classes.joinToString { it.simpleName }}"
            }


            logger.log(messages, MessageType.IMPLEMENTATION_NOT_FOUND)
        }
    }

    internal fun getProperties(type: Class<*>, additional: List<PropertyInfo>, extensions: List<ExtensionSpecification>): List<PropertyInfo> {
        val list = mutableListOf<PropertyInfo>()

        val methods = type.methods
                .filter { it.declaringClass != Any::class.java }
                .toMutableList()

        extensions.map { it.implement }.filterNotNull().forEach {
            methods += it.methods.filter { it.declaringClass != Any::class.java }
        }

        val extensionClasses = extensions.map { it.extensionClass }.filterNotNull()

        methods.forEach { method ->

            // Since: 1.1.2: Extensions are allowed to implement properties getter and setter.
            if (extensionClasses.any { this.hasMethod(it, method) })
                return@forEach

            val name = method.name

            val isGet = name.startsWith("get") && method.parameterCount == 0
            val isIs = name.startsWith("is") && method.parameterCount == 0
            val isSet = name.startsWith("set") && method.parameterCount == 1

            // Skip PropertyHolder methods
            // We could use method.declaringClass == PropertyHolder::class.java
            // but override methods will return false.
            if (this.hasMethod(PropertyHolder::class.java, method))
                return@forEach

            if (isGet || isIs || isSet) {
                // hasProperty of PropertyHolder
                // 3 = "get".length & "set".length
                // 2 = "is".length
                val propertyName = (if (isGet || isSet) name.substring(3..name.length - 1) else name.substring(2..name.length - 1))
                        .decapitalize()

                val propertyType = if (isGet || isIs) method.returnType else method.parameterTypes[0]

                if (!list.any { it.propertyName == propertyName }) {

                    val setter = getSetter(type, propertyName, propertyType) ?: getSetter(method.declaringClass, propertyName, propertyType)

                    val getterName = (getGetter(type, propertyName) ?: getGetter(method.declaringClass, propertyName))?.name
                    val setterName = setter?.name

                    val validator = setter?.getDeclaredAnnotation(Validate::class.java)?.value?.java
                    val isNotNull = setter?.parameterAnnotations?.firstOrNull()?.any { it is NotNullValue }
                            ?: method.isAnnotationPresent(NotNullValue::class.java)

                    list += PropertyInfo(propertyName, getterName, setterName, propertyType, isNotNull, validator)
                }

            }

        }

        additional.forEach { ad ->
            if (!list.any { it.propertyName == ad.propertyName })
                list.add(ad)
        }

        return list
    }

    private fun getPropertyType(type: Class<*>, name: String, logger: LoggerInterface): CodeType {
        val capitalized = name.capitalize()

        val method = try {
            type.getMethod("get$capitalized")
        } catch (t: Throwable) {
            null
        }

        if (method != null) {
            return method.returnType.codeType
        } else {
            logger.log("Cannot find property with name: $name!", MessageType.MISSING_PROPERTY)
            fail()
        }
    }

    internal fun genDefaultMethodsImpl(baseClass: Class<*>): List<MethodDeclaration> {
        val funcs = baseClass.methods.map { base ->
            findImplementation(baseClass, base)?.let { Pair(base, it) }
        }.filterNotNull()

        return funcs.map {
            val base = it.first
            val delegateClass = it.second.first
            val delegate = it.second.second

            val parameters = base.parameters.mapIndexed { i, it ->
                parameter(type = delegate.parameters[i + 1].type, name = "arg$i")
            }

            val arguments = mutableListOf<CodeInstruction>(Access.THIS) + parameters.map { it.toVariableAccess() }

            val invoke: CodeInstruction = invoke(
                    InvokeType.INVOKE_STATIC,
                    delegateClass.codeType,
                    Access.STATIC,
                    delegate.name,
                    TypeSpec(delegate.returnType, delegate.parameters.map { it.type }),
                    arguments
            ).let {
                if (base.returnType.`is`(Void.TYPE))
                    it
                else
                    returnValue(base.returnType, it)
            }

            MethodDeclaration.Builder.builder()
                    .annotations(overrideAnnotation())
                    .modifiers(CodeModifier.PUBLIC, CodeModifier.BRIDGE)
                    .name(base.name)
                    .returnType(base.returnType)
                    .parameters(parameters)
                    .body(source(invoke))
                    .build()

        }
    }

    private fun getTypeToInvoke(hasGetter: Boolean, hasSetter: Boolean, type: Class<*>): Class<*> =
            if (hasGetter && hasSetter) when (type) {
                java.lang.Byte.TYPE,
                java.lang.Short.TYPE,
                java.lang.Character.TYPE,
                java.lang.Integer.TYPE -> IntGSProperty.Impl::class.java
                java.lang.Boolean.TYPE -> BooleanGSProperty.Impl::class.java
                java.lang.Double.TYPE,
                java.lang.Float.TYPE -> DoubleGSProperty.Impl::class.java
                java.lang.Long.TYPE -> LongGSProperty.Impl::class.java
                else -> GSProperty.Impl::class.java
            } else if (hasGetter) when (type) {
                java.lang.Byte.TYPE,
                java.lang.Short.TYPE,
                Character.TYPE,
                java.lang.Integer.TYPE -> IntGetterProperty.Impl::class.java
                java.lang.Boolean.TYPE -> BooleanGetterProperty.Impl::class.java
                java.lang.Double.TYPE,
                java.lang.Float.TYPE -> DoubleGetterProperty.Impl::class.java
                java.lang.Long.TYPE -> LongGetterProperty.Impl::class.java
                else -> GetterProperty.Impl::class.java
            } else if (hasSetter) when (type) {
                java.lang.Byte.TYPE,
                java.lang.Short.TYPE,
                java.lang.Character.TYPE,
                java.lang.Integer.TYPE -> IntSetterProperty.Impl::class.java
                java.lang.Boolean.TYPE -> BooleanSetterProperty.Impl::class.java
                java.lang.Double.TYPE,
                java.lang.Float.TYPE -> DoubleSetterProperty.Impl::class.java
                java.lang.Long.TYPE -> LongSetterProperty.Impl::class.java
                else -> SetterProperty.Impl::class.java
            } else when (type) {
                java.lang.Byte.TYPE,
                java.lang.Short.TYPE,
                java.lang.Character.TYPE,
                java.lang.Integer.TYPE -> IntProperty.Impl::class.java
                java.lang.Boolean.TYPE -> BooleanProperty.Impl::class.java
                java.lang.Double.TYPE,
                java.lang.Float.TYPE -> DoubleProperty.Impl::class.java
                java.lang.Long.TYPE -> LongProperty.Impl::class.java
                else -> Property.Impl::class.java
            }

    private fun getSupplierType(type: Class<*>): Pair<String, Class<*>> = when (type) {
        java.lang.Byte.TYPE,
        java.lang.Short.TYPE,
        java.lang.Character.TYPE,
        java.lang.Integer.TYPE -> "getAsInt" to IntSupplier::class.java
        java.lang.Boolean.TYPE -> "getAsBoolean" to BooleanSupplier::class.java
        java.lang.Double.TYPE,
        java.lang.Float.TYPE -> "getAsDouble" to DoubleSupplier::class.java
        java.lang.Long.TYPE -> "getAsLong" to LongSupplier::class.java
        else -> "get" to Supplier::class.java
    }

    private fun getConsumerType(type: Class<*>): Class<*> = when (type) {
        java.lang.Byte.TYPE,
        java.lang.Short.TYPE,
        java.lang.Character.TYPE,
        java.lang.Integer.TYPE -> IntConsumer::class.java
        java.lang.Boolean.TYPE -> BooleanConsumer::class.java
        java.lang.Double.TYPE,
        java.lang.Float.TYPE -> DoubleConsumer::class.java
        java.lang.Long.TYPE -> LongConsumer::class.java
        else -> Consumer::class.java
    }

    private fun getCastType(type: Class<*>): Class<*> = when (type) {
        java.lang.Byte.TYPE, // -> java.lang.Byte.TYPE // Temporary workaround until CodeAPI-BytecodeWriter:hotfix3
        java.lang.Short.TYPE, // -> java.lang.Short.TYPE // Temporary workaround until CodeAPI-BytecodeWriter:hotfix3
        java.lang.Character.TYPE, // -> java.lang.Character.TYPE // Temporary workaround until CodeAPI-BytecodeWriter:hotfix3
        java.lang.Integer.TYPE -> java.lang.Integer.TYPE
        java.lang.Boolean.TYPE -> java.lang.Boolean.TYPE
        java.lang.Double.TYPE,
        java.lang.Float.TYPE -> java.lang.Double.TYPE
        java.lang.Long.TYPE -> java.lang.Long.TYPE
        else -> type
    }

    private fun getCastType(codeType: CodeType): CodeType = when (codeType) {
        Types.BYTE,
        Types.SHORT,
        Types.CHAR,
        Types.INT -> Types.INT
        Types.BOOLEAN -> Types.BOOLEAN
        Types.DOUBLE,
        Types.FLOAT -> Types.DOUBLE
        Types.LONG -> Types.LONG
        else -> codeType
    }

    private fun hasMethod(klass: Class<*>, method: Method): Boolean {
        return klass.methods.any { it.isEqual(method) }
    }

}