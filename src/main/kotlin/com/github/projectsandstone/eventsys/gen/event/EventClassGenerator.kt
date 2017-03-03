/**
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

import com.github.jonathanxd.codeapi.*
import com.github.jonathanxd.codeapi.base.MethodDeclaration
import com.github.jonathanxd.codeapi.base.MethodInvocation
import com.github.jonathanxd.codeapi.base.impl.ConstructorDeclarationImpl
import com.github.jonathanxd.codeapi.builder.MethodDeclarationBuilder
import com.github.jonathanxd.codeapi.bytecode.CHECK
import com.github.jonathanxd.codeapi.bytecode.VISIT_LINES
import com.github.jonathanxd.codeapi.bytecode.VisitLineType
import com.github.jonathanxd.codeapi.bytecode.gen.BytecodeGenerator
import com.github.jonathanxd.codeapi.common.*
import com.github.jonathanxd.codeapi.conversions.createInvocation
import com.github.jonathanxd.codeapi.conversions.parameterNames
import com.github.jonathanxd.codeapi.conversions.toCodeArgument
import com.github.jonathanxd.codeapi.factory.field
import com.github.jonathanxd.codeapi.generic.GenericSignature
import com.github.jonathanxd.codeapi.inspect.SourceInspect
import com.github.jonathanxd.codeapi.literal.Literals
import com.github.jonathanxd.codeapi.type.CodeType
import com.github.jonathanxd.codeapi.type.Generic
import com.github.jonathanxd.codeapi.util.Alias
import com.github.jonathanxd.codeapi.util.codeType
import com.github.jonathanxd.codeapi.util.source.CodeArgumentUtil
import com.github.jonathanxd.iutils.type.TypeInfo
import com.github.projectsandstone.eventsys.Debug
import com.github.projectsandstone.eventsys.event.Event
import com.github.projectsandstone.eventsys.event.annotation.Name
import com.github.projectsandstone.eventsys.event.property.*
import com.github.projectsandstone.eventsys.event.property.primitive.*
import com.github.projectsandstone.eventsys.gen.GeneratedEventClass
import com.github.projectsandstone.eventsys.gen.genericFromTypeInfo
import com.github.projectsandstone.eventsys.gen.save.ClassSaver
import com.github.projectsandstone.eventsys.reflect.getImplementation
import com.github.projectsandstone.eventsys.reflect.isEqual
import com.github.projectsandstone.eventsys.reflect.parameterNames
import com.github.projectsandstone.eventsys.util.BooleanConsumer
import java.lang.Void
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.Type
import java.util.*
import java.util.function.*
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.jvm.jvmErasure

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
    val propertiesFieldType = Generic.type(CodeAPI.getJavaType(Map::class.java))
            .of(Types.STRING)
            .of(CodeAPI.getJavaType(Property::class.java))

    private fun TypeInfo<*>.toStr(): String {
        if (this.related.isEmpty()) {
            return this.toFullString()
        } else {
            val base = StringBuilder(this.aClass.name)

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

    private fun getName(base: String): String {

        var base_ = base
        var count = 0

        fun findClass(base: String): Boolean =
                try {
                    Class.forName(base)
                    true
                } catch (t: Throwable) {
                    false
                }

        while (findClass(base_)) {
            if (count == 0)
                base_ += "\$"

            base_ += "$base$count"
            count++
        }

        return base_
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Event> genImplementation(eventClassSpecification: EventClassSpecification<T>): Class<T> {

        if (cached.containsKey(eventClassSpecification)) {
            return cached[eventClassSpecification]!! as Class<T>
        }

        val typeInfo = eventClassSpecification.typeInfo
        val additionalProperties = eventClassSpecification.additionalProperties
        val extensions = eventClassSpecification.extensions
        val type: CodeType = genericFromTypeInfo(typeInfo)
        val classType = typeInfo.aClass
        val isItf = classType.isInterface

        val typeInfoLiter = typeInfo.toStr()

        val name = getName("${typeInfoLiter}Impl")

        var codeClassBuilder = CodeAPI
                .aClassBuilder()
                .withBody(MutableCodeSource())
                .withModifiers(CodeModifier.PUBLIC)
                .withQualifiedName(name)

        val implementations = mutableListOf<CodeType>()

        if (isItf) {
            implementations += type
            codeClassBuilder = codeClassBuilder.withSuperClass(Types.OBJECT)
        } else
            codeClassBuilder = codeClassBuilder.withSuperClass(type)

        val properties = this.getProperties(classType) + additionalProperties

        extensions.forEach {
            it.implement?.let {
                implementations += it.codeType
            }
        }


        codeClassBuilder = codeClassBuilder.withImplementations(implementations)

        val codeClass = codeClassBuilder.build()

        val body = codeClass.body as MutableCodeSource

        this.genFields(body, properties)
        this.genConstructor(classType, body, properties)
        this.genMethods(classType, body, properties)

        extensions.forEach {
            it.extensionMethodsClass?.let {
                this.genExtensionMethods(classType, it, body)
            }
        }

        // Gen getProperties & getProperty & hasProperty
        this.genPropertyHolderMethods(body)
        this.checkDuplicates(body)
        this.checkMethodImplementations(classType, body)


        val codeSource = CodeAPI.sourceOfParts(codeClass)

        val generator = BytecodeGenerator()

        generator.options.set(VISIT_LINES, VisitLineType.FOLLOW_CODE_SOURCE)
        generator.options.set(CHECK, false)

        val bytecodeClass = generator.gen(codeSource)[0]

        val bytes = bytecodeClass.bytecode
        val disassembled = lazy { bytecodeClass.disassembledCode }

        val generatedEventClass = EventGenClassLoader.defineClass(codeClass, bytes, disassembled) as GeneratedEventClass<T>

        if (Debug.EVENT_GEN_DEBUG) {
            ClassSaver.save("eventgen", generatedEventClass)
        }

        return generatedEventClass.javaClass.let {
            this.cached.put(eventClassSpecification, it)
            it
        }
    }

    private fun checkDuplicates(codeSource: CodeSource) {

        val methodList = mutableListOf<MethodDeclaration>()

        codeSource.forEach { outer ->
            if (outer is MethodDeclaration) {
                if (methodList.any {
                    it.name == outer.name
                            && it.returnType == outer.returnType
                            && it.parameters.map { it.type } == outer.parameters.map { it.type }
                })
                    throw IllegalStateException("Duplicated method: ${outer.name}")
                else
                    methodList += outer
            }
        }
    }

    private fun genFields(body: MutableCodeSource, properties: List<PropertyInfo>) {
        properties.forEach {
            val name = it.propertyName

            val modifiers = EnumSet.of(CodeModifier.PRIVATE)

            if (!it.isMutable()) {
                modifiers.add(CodeModifier.FINAL)
            }

            val codeType: CodeType = CodeAPI.getJavaType(it.type)

            val field = field(modifiers, codeType, name)

            body += field
        }

        genPropertyField(body)
    }

    private fun genPropertyField(body: MutableCodeSource) {
        body += field(EnumSet.of(CodeModifier.PRIVATE, CodeModifier.FINAL),
                propertiesFieldType,
                propertiesFieldName,
                CodeAPI.invokeConstructor(HashMap::class.java))

        body += field(EnumSet.of(CodeModifier.PRIVATE, CodeModifier.FINAL),
                propertiesFieldType,
                propertiesUnmodName,
                CodeAPI.invokeStatic(Collections::class.java,
                        "unmodifiableMap",
                        CodeAPI.typeSpec(Map::class.java, Map::class.java),
                        listOf(CodeAPI.accessThisField(propertiesFieldType, propertiesFieldName))))
    }

    private fun genConstructor(type: Class<*>, body: MutableCodeSource, properties: List<PropertyInfo>) {
        val parameters = mutableListOf<CodeParameter>()

        properties.forEach {
            val name = it.propertyName

            val valueType: CodeType = CodeAPI.getJavaType(it.type)

            parameters += CodeParameter(
                    name = name,
                    type = valueType,
                    annotations = listOf(CodeAPI.visibleAnnotation(Name::class.java, mapOf<String, Any>("value" to name)))
            )
        }

        val constructor = ConstructorDeclarationImpl(
                modifiers = EnumSet.of(CodeModifier.PUBLIC),
                parameters = parameters,
                annotations = emptyList(),
                body = MutableCodeSource(),
                genericSignature = GenericSignature.empty())

        body += constructor

        val constructorBody = constructor.body as MutableCodeSource

        properties.forEach {
            val valueType: CodeType = CodeAPI.getJavaType(it.type)

            constructorBody += CodeAPI.setThisField(valueType, it.propertyName, CodeAPI.accessLocalVariable(valueType, it.propertyName))
        }

        genConstructorPropertiesMap(constructorBody, properties)

    }

    private fun genConstructorPropertiesMap(constructorBody: MutableCodeSource, properties: List<PropertyInfo>) {
        val accessMap = CodeAPI.accessThisField(propertiesFieldType, propertiesFieldName)

        properties.forEach {
            constructorBody += this.invokePut(accessMap,
                    com.github.jonathanxd.codeapi.literal.Literals.STRING(it.propertyName),
                    this.propertyToSProperty(it))
        }

    }

    private fun invokePut(accessMap: CodePart, vararg arguments: CodePart): CodePart {
        return CodeAPI.invokeInterface(Map::class.java, accessMap, "put", CodeAPI.typeSpec(Any::class.java, Any::class.java, Any::class.java), listOf(*arguments))
    }

    private fun propertyToSProperty(property: PropertyInfo): CodePart {

        val hasGetter = property.hasGetter()
        val hasSetter = property.hasSetter()

        val typeToInvoke = CodeAPI.getJavaType(this.getTypeToInvoke(hasGetter, hasSetter, property.type))

        val arguments = mutableListOf<CodePart>()
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
            val consumerType = CodeAPI.getJavaType(this.getConsumerType(property.type))

            arguments += this.invokeSetter(property.type, consumerType, property)
            argumentTypes += consumerType
        }

        val typeSpec = TypeSpec(Types.VOID, argumentTypes)

        return CodeAPI.invokeConstructor(typeToInvoke, typeSpec, arguments)
    }

    private fun invokeGetter(type: Class<*>, supplierInfo: Pair<String, Class<*>>, property: PropertyInfo): CodePart {
        val propertyType = property.type
        val getterName = property.getterName!!

        val supplierType = supplierInfo.second.codeType
        val realType = this.getCastType(propertyType).codeType
        val rtype = if (type.isPrimitive) realType /*type.codeType*/ else Types.OBJECT

        val invocation = CodeAPI.invoke(InvokeType.INVOKE_VIRTUAL,
                Alias.THIS,
                CodeAPI.accessThis(),
                getterName,
                CodeAPI.typeSpec(realType/*propertyType*/),
                mutableListOf()
        )

        return CodeAPI.invokeDynamic(
                InvokeDynamic.LambdaMethodReference(
                        methodTypeSpec = MethodTypeSpec(supplierType, supplierInfo.first, CodeAPI.typeSpec(rtype)),
                        expectedTypes = CodeAPI.typeSpec(realType /*propertyType*/)
                ),
                invocation
        )
    }

    private fun invokeSetter(type: Class<*>, consumerType: CodeType, property: PropertyInfo): CodePart {
        val setterName = property.setterName!!

        val realType = this.getCastType(property.type).codeType
        val ptype = if (type.isPrimitive) realType/*type.codeType*/ else Types.OBJECT

        val invocation: MethodInvocation

        invocation = CodeAPI.invokeVirtual(Alias.THIS, CodeAPI.accessThis(), setterName,
                TypeSpec(Types.VOID, listOf(realType/*propertyType*/)),
                emptyList()
        )

        return CodeAPI.invokeDynamic(
                InvokeDynamic.LambdaMethodReference(
                        methodTypeSpec = MethodTypeSpec(consumerType, "accept", CodeAPI.constructorTypeSpec(ptype)),
                        expectedTypes = CodeAPI.constructorTypeSpec(realType/*propertyType*/)
                ),
                invocation
        )
    }

    private fun genMethods(type: Class<*>, body: MutableCodeSource, properties: List<PropertyInfo>) {

        genDefaultMethodsImpl(body)

        properties.forEach {
            val name = it.propertyName

            if (it.hasGetter()) {
                genGetter(type, body, it.getterName!!, name, it.type)
            }

            if (it.isMutable()) {
                genSetter(type, body, it.setterName!!, name, it.type)
            }
        }

    }

    private fun genExtensionMethods(base: Class<*>, methodClass: Class<*>, body: MutableCodeSource) {
        methodClass.declaredMethods.forEach {

            if (Modifier.isPublic(it.modifiers) && Modifier.isStatic(it.modifiers)) {

                val names = it.parameterNames

                if (!it.parameters[0].type.isAssignableFrom(base))
                    throw IllegalArgumentException("Method '$it' of provided methodClass '${methodClass.canonicalName}' must receive '${base.canonicalName}' (or supertype or superinterface of the class) as first parameter!")

                val parameters = it.parameters.mapIndexed { index, parameter ->
                    if (index > 0) {
                        val name = parameter.getDeclaredAnnotation(Name::class.java)?.value ?: names[index]
                        return@mapIndexed CodeParameter(parameter.parameterizedType.codeType, name)
                    } else null
                }.filterNotNull()

                val arguments = mutableListOf<CodePart>(CodeAPI.accessThis()).let {
                    it.addAll(CodeArgumentUtil.argumentsFromParameters(parameters))
                    it
                }

                body.add(MethodDeclarationBuilder.builder()
                        .withModifiers(CodeModifier.PUBLIC)
                        .withName(it.name)
                        .withReturnType(it.genericReturnType.codeType)
                        .withParameters(parameters)
                        .withBody(CodeAPI.source(
                                CodeAPI.returnValue(it.returnType.codeType, it.createInvocation(null, arguments))
                        ))
                        .build())
            }
        }
    }

    private fun genGetter(type: Class<*>, body: MutableCodeSource, getterName: String, name: String, propertyType: Class<*>?) {
        val fieldType = propertyType?.let { CodeAPI.getJavaType(it) } ?: getPropertyType(type, name)

        val method = MethodDeclarationBuilder.builder()
                .withModifiers(EnumSet.of(CodeModifier.PUBLIC))
                .withReturnType(fieldType)
                .withName(getterName)
                .withBody(CodeAPI.sourceOfParts(CodeAPI.returnThisField(fieldType, name)))
                .build()

        body += method

        val castType = this.getCastType(fieldType)

        if (castType != fieldType) {
            body += MethodDeclarationBuilder.builder()
                    .withModifiers(EnumSet.of(CodeModifier.PUBLIC))
                    .withReturnType(castType)
                    .withName(getterName)
                    .withBody(CodeAPI.sourceOfParts(
                            CodeAPI.returnValue(castType,
                                    CodeAPI.cast(fieldType, castType, CodeAPI.accessThisField(fieldType, name))
                            )
                    ))
                    .build()
        }
    }

    private fun genSetter(type: Class<*>, body: MutableCodeSource, setterName: String, name: String, propertyType: Class<*>?) {
        val fieldType = propertyType?.let { CodeAPI.getJavaType(it) } ?: getPropertyType(type, name)

        val method = MethodDeclarationBuilder.builder()
                .withModifiers(EnumSet.of(CodeModifier.PUBLIC))
                .withReturnType(Types.VOID)
                .withParameters(CodeAPI.parameter(fieldType, name))
                .withName(setterName)
                .withBody(CodeAPI.sourceOfParts(CodeAPI.setThisField(fieldType, name, CodeAPI.accessLocalVariable(fieldType, name))))
                .build()

        body += method

        val castType = this.getCastType(fieldType)

        if (castType != fieldType) {
            body += MethodDeclarationBuilder.builder()
                    .withModifiers(EnumSet.of(CodeModifier.PUBLIC))
                    .withReturnType(Types.VOID)
                    .withParameters(CodeAPI.parameter(castType, name))
                    .withName(setterName)
                    .withBody(CodeAPI.sourceOfParts(CodeAPI.setThisField(fieldType, name, CodeAPI.cast(castType, fieldType, CodeAPI.accessLocalVariable(castType, name)))))
                    .build()
        }
    }

    private fun genPropertyHolderMethods(body: MutableCodeSource) {
        val getPropertiesMethod = CodeAPI.methodBuilder()
                .withModifiers(CodeModifier.PUBLIC)
                .withName("getProperties")
                .withReturnType(propertiesFieldType)
                .withAnnotations(CodeAPI.annotation(Override::class.java))
                .withBody(CodeAPI.sourceOfParts(
                        CodeAPI.returnValue(propertiesFieldType, CodeAPI.accessThisField(propertiesFieldType, propertiesUnmodName))
                ))
                .build()

        body += getPropertiesMethod
    }

    private fun hasSetter(type: Class<*>, name: String, propertyType: Class<*>): Boolean {

        val capitalized = name.capitalize()

        return try {
            type.getMethod("set$capitalized", propertyType)
            true
        } catch (t: Throwable) {
            false
        }
    }

    private fun hasGetter(type: Class<*>, name: String): Boolean {

        val capitalized = name.capitalize()

        return try {
            type.getMethod("is$capitalized")
            true
        } catch (t: Throwable) {
            false
        } || try {
            type.getMethod("get$capitalized")
            true
        } catch (t: Throwable) {
            false
        }
    }

    internal fun checkMethodImplementations(type: Class<*>, body: MutableCodeSource) { //, properties: List<PropertyInfo>, extensions: List<MethodTypeSpec>

        val implementedMethods: List<MethodDeclaration> = SourceInspect.find { it is MethodDeclaration }
                .includeSource(true)
                .mapTo { it as MethodDeclaration }
                .inspect(body)

        type.methods.forEach { method ->
            val name = method.name

            val hasImpl = implementedMethods.any {
                it.name == name
                && it.returnType.canonicalName == method.returnType.canonicalName
                && it.parameters.map { it.type.canonicalName } == method.parameterTypes.map { it.canonicalName }
            }

            /*
            val isGet = name.startsWith("get")
            val isIs = name.startsWith("is")
            val isSet = name.startsWith("set")

            if (this.hasMethod(PropertyHolder::class.java, method))
                return@forEach


            val hasImpl = properties.any {
                if ((isGet || isIs)
                        && it.getterName == name
                        && method.parameterCount == 0
                        && method.returnType == it.type)
                    return@any true

                if (isSet && it.setterName == name
                        && method.returnType == Void.TYPE
                        && method.parameterCount == 1
                        && method.parameterTypes[0] == it.type)
                    return@any true

                return@any false
            } || extensions.any {
                it.methodName == name
                        && it.typeSpec.returnType.`is`(method.returnType.codeType)
                        && it.typeSpec.parameterTypes.equals(method.parameterTypes.map { it.codeType })
            }*/

            if(!hasImpl && !method.isDefault) {
                throw IllegalStateException("Cannot find implementation of method '$method'! Provide an extension that implements this method.")
            }
        }
    }

    internal fun getProperties(type: Class<*>): List<PropertyInfo> {
        val list = mutableListOf<PropertyInfo>()

        type.methods.forEach { method ->

            val name = method.name

            val isGet = name.startsWith("get")
            val isIs = name.startsWith("is")
            val isSet = name.startsWith("set")

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
                    val getterName = if (hasGetter(type, propertyName)) "get${propertyName.capitalize()}" else null
                    val setterName = if (hasSetter(type, propertyName, propertyType)) "set${propertyName.capitalize()}" else null

                    list += PropertyInfo(propertyName, getterName, setterName, propertyType)
                }

            }

        }

        return list
    }

    private fun getPropertyType(type: Class<*>, name: String): CodeType {
        val capitalized = name.capitalize()

        val method = try {
            type.getMethod("get$capitalized")
        } catch (t: Throwable) {
            null
        }

        if (method != null) {
            return CodeAPI.getJavaType(method.returnType)
        } else {
            throw IllegalArgumentException("Cannot find property with name: $name!")
        }
    }

    internal fun genDefaultMethodsImpl(body: MutableCodeSource) {
        val funcs = PropertyHolder::class.memberFunctions.map { base ->
            getImplementation(PropertyHolder::class, base)?.let { Pair(base, it) }
        }.filterNotNull()

        funcs.forEach {
            val base = it.first
            val delegateClass = it.second.first
            val delegate = it.second.second

            val parameters = base.parameterNames.mapIndexed { i, it ->
                CodeParameter(delegate.parameters[i + 1].type.codeType, it)
            }

            val arguments = mutableListOf<CodePart>(CodeAPI.accessThis()) + parameters.map { it.toCodeArgument() }

            val invoke = CodeAPI.invoke(
                    InvokeType.INVOKE_STATIC,
                    delegateClass.codeType,
                    CodeAPI.accessStatic(),
                    delegate.name,
                    TypeSpec(delegate.returnType.codeType, delegate.parameters.map { it.type.codeType }),
                    arguments
            ).let {
                if (base.returnType.jvmErasure.java == Void.TYPE)
                    it
                else
                    CodeAPI.returnValue(base.returnType.jvmErasure.codeType, it)
            }

            body.add(MethodDeclarationBuilder.builder()
                    .withAnnotations(CodeAPI.overrideAnnotation())
                    .withModifiers(CodeModifier.PUBLIC, CodeModifier.BRIDGE)
                    .withName(base.name)
                    .withReturnType(base.returnType.jvmErasure.codeType)
                    .withParameters(parameters)
                    .withBody(CodeAPI.sourceOfParts(invoke))
                    .build()
            )

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