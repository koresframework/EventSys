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
import com.github.jonathanxd.codeapi.bytecode.VISIT_LINES
import com.github.jonathanxd.codeapi.bytecode.VisitLineType
import com.github.jonathanxd.codeapi.bytecode.processor.BytecodeProcessor
import com.github.jonathanxd.codeapi.common.MethodInvokeSpec
import com.github.jonathanxd.codeapi.common.VariableRef
import com.github.jonathanxd.codeapi.factory.*
import com.github.jonathanxd.codeapi.literal.Literals
import com.github.jonathanxd.codeapi.type.CodeType
import com.github.jonathanxd.codeapi.type.GenericType
import com.github.jonathanxd.codeapi.util.canonicalName
import com.github.jonathanxd.codeapi.util.codeType
import com.github.jonathanxd.codeapi.util.conversion.parameterNames
import com.github.jonathanxd.codeapi.util.conversion.toMethodDeclaration
import com.github.jonathanxd.codeapi.util.conversion.toVariableAccess
import com.github.jonathanxd.codeapi.util.getType
import com.github.jonathanxd.iutils.`object`.Default
import com.github.jonathanxd.iutils.collection.Collections3
import com.github.jonathanxd.iutils.type.TypeInfo
import com.github.jonathanxd.iutils.type.TypeInfoBuilder
import com.github.jonathanxd.iutils.type.TypeUtil
import com.github.projectsandstone.eventsys.Debug
import com.github.projectsandstone.eventsys.bootstrap.FactoryBootstrap
import com.github.projectsandstone.eventsys.event.Cancellable
import com.github.projectsandstone.eventsys.event.Event
import com.github.projectsandstone.eventsys.event.annotation.*
import com.github.projectsandstone.eventsys.gen.GeneratedEventClass
import com.github.projectsandstone.eventsys.gen.save.ClassSaver
import com.github.projectsandstone.eventsys.logging.LoggerInterface
import com.github.projectsandstone.eventsys.logging.MessageType
import com.github.projectsandstone.eventsys.reflect.PropertiesSort
import com.github.projectsandstone.eventsys.reflect.findImplementation
import com.github.projectsandstone.eventsys.reflect.getAllAnnotationsOfType
import com.github.projectsandstone.eventsys.util.fail
import com.github.projectsandstone.eventsys.util.toSimpleString
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.lang.reflect.Parameter
import java.lang.reflect.TypeVariable

/**
 * This class generates an implementation of an event factory, this method will create the event class
 * and direct-call the constructor.
 *
 * Additional properties that are mutable must be annotated with [Mutable] annotation.
 *
 * Extensions are provided via [Extension] annotation in the factory method.
 *
 */
internal object EventFactoryClassGenerator {

    private val cached = mutableMapOf<Class<*>, Any>()

    /**
     * Create [factoryClass] instance invoking generated event classes constructor.
     */
    @Suppress("UNCHECKED_CAST")
    internal fun <T : Any> create(eventGenerator: EventGenerator,
                                  factoryClass: Class<T>,
                                  logger: LoggerInterface): T {

        if (this.cached.containsKey(factoryClass))
            return this.cached[factoryClass]!! as T

        val superClass = factoryClass.superclass

        if (!factoryClass.isInterface) {
            logger.log("Factory class must be an interface.", MessageType.INVALID_FACTORY)
            fail()
        }

        if (superClass != null && factoryClass != Any::class.java || factoryClass.interfaces.isNotEmpty()) {
            logger.log("Factory class must not extend any class.", MessageType.INVALID_FACTORY)
            fail()
        }

        val eventGeneratorField = VariableRef(EventGenerator::class.java, "eventGenerator")

        val declaration = ClassDeclaration.Builder.builder()
                .modifiers(CodeModifier.PUBLIC)
                .qualifiedName("${factoryClass.canonicalName}\$Impl")
                .implementations(factoryClass.codeType)
                .superClass(Types.OBJECT)
                .fields(FieldDeclaration.Builder()
                        .modifiers(CodeModifier.PRIVATE, CodeModifier.FINAL)
                        .base(eventGeneratorField)
                        .build()
                )
                .constructors(ConstructorDeclaration.Builder.builder()
                        .modifiers(CodeModifier.PUBLIC)
                        .parameters(parameter(name = eventGeneratorField.name, type = eventGeneratorField.type))
                        .body(CodeSource.fromVarArgs(
                                setThisFieldValue(eventGeneratorField.type, eventGeneratorField.name,
                                        accessVariable(eventGeneratorField))
                        ))
                        .build())
                .methods(factoryClass.declaredMethods
                        .filter { !it.isDefault }
                        .map mMap@ { factoryMethod ->

                            val kFunc = factoryMethod
                            val cl = factoryMethod.declaringClass

                            val impl = kFunc?.let { findImplementation(cl, it) }

                            if (kFunc != null && impl != null) {
                                val base = kFunc
                                val delegateClass = impl.first
                                val delegate = impl.second

                                val parameters = base.parameterNames.mapIndexed { i, it ->
                                    parameter(type = delegate.parameters[i + 1].type.codeType, name = it)
                                }

                                val arguments = mutableListOf<CodeInstruction>(Access.THIS) + parameters.map { it.toVariableAccess() }

                                val invoke: CodeInstruction = invoke(
                                        InvokeType.INVOKE_STATIC,
                                        delegateClass.codeType,
                                        Access.STATIC,
                                        delegate.name,
                                        typeSpec(delegate.returnType.codeType, delegate.parameters.map { it.type.codeType }),
                                        arguments
                                ).let {
                                    if (kFunc.returnType == Void.TYPE)
                                        it
                                    else
                                        returnValue(kFunc.returnType.codeType, it)
                                }

                                val methodDeclaration = factoryMethod.toMethodDeclaration()
                                val methodBody = methodDeclaration.body as MutableCodeSource
                                methodBody.add(invoke)

                                return@mMap methodDeclaration
                            } else {

                                val eventType = factoryMethod.returnType
                                val ktNames by lazy { factoryMethod.parameterNames }

                                if (!Event::class.java.isAssignableFrom(eventType)) {
                                    logger.log("Failed to generate implementation of method '$factoryMethod': event factory methods must return a type assignable to 'Event'.", MessageType.INVALID_FACTORY_METHOD)
                                    fail()
                                }

                                val parameterNames = factoryMethod.parameters.mapIndexed { i, it ->
                                    if (it.isAnnotationPresent(Name::class.java))
                                        it.getDeclaredAnnotation(Name::class.java).value
                                    else
                                        ktNames[i]
                                }

                                val allExtensionAnnotations = mutableListOf<Extension>()

                                allExtensionAnnotations += eventType.getAllAnnotationsOfType(Extension::class.java)
                                allExtensionAnnotations += factoryMethod.getDeclaredAnnotationsByType(Extension::class.java)

                                val extensions =
                                        allExtensionAnnotations.map {
                                            val implement = it.implement.java.let { if (it == Default::class.java) null else it }
                                            val extension = it.extensionClass.java.let { if (it == Default::class.java) null else it }
                                            ExtensionSpecification(factoryMethod, implement, extension)
                                        }

                                val properties = EventClassGenerator.getProperties(eventType, emptyList(), extensions)
                                val additionalProperties = mutableListOf<PropertyInfo>()

                                val eventTypeInfo =
                                        TypeUtil.toTypeInfo(factoryMethod.genericReturnType) as TypeInfo<Event>

                                val eventGenericType = factoryMethod.genericReturnType.getType()

                                val isGeneric = factoryMethod.typeParameters.isNotEmpty()
                                        && eventGenericType.usesParameters(factoryMethod.typeParameters)

                                val provider = factoryMethod.parameters
                                        .filter { it.isAnnotationPresent(TypeParam::class.java) }

                                if (isGeneric) {
                                    if (provider.isEmpty()) {
                                        logger.log("Factory method '$factoryMethod' present in factory class '${factoryClass.canonicalName}' must have a parameter of type 'TypeInfo' annotated with @TypeParam.", MessageType.INVALID_FACTORY_METHOD)
                                        fail()
                                    }

                                    if (provider.size != 1) {
                                        logger.log("Factory method '$factoryMethod' present in factory class '${factoryClass.canonicalName}' must have only one parameter of type 'TypeInfo' annotated with @TypeParam.", MessageType.INVALID_FACTORY_METHOD)
                                        fail()
                                    }

                                    if (provider.single().type != TypeInfo::class.java) {
                                        logger.log("@TypeParam should be only annotated in parameter of 'TypeInfo' type. Factory method: '$factoryMethod'. Factory class '${factoryClass.canonicalName}'.", MessageType.INVALID_FACTORY_METHOD)
                                        fail()
                                    }
                                }

                                factoryMethod.parameters.forEachIndexed { i, parameter ->
                                    val find = properties.any { it.propertyName == parameterNames[i] && it.type == parameter.type }

                                    if (!find && !parameter.isAnnotationPresent(TypeParam::class.java)) {
                                        val name = parameterNames[i]

                                        val getterName = "get${name.capitalize()}"
                                        val setterName = if (parameter.isAnnotationPresent(Mutable::class.java)) "set${name.capitalize()}" else null

                                        additionalProperties += PropertyInfo(
                                                propertyName = name,
                                                type = parameter.type,
                                                getterName = getterName,
                                                setterName = setterName,
                                                validator = parameter.getDeclaredAnnotation(Validate::class.java)?.value?.java,
                                                isNotNull = parameter.isAnnotationPresent(NotNullValue::class.java)
                                        )
                                    }
                                }

                                if (!Event::class.java.isAssignableFrom(eventTypeInfo.typeClass)) {
                                    logger.log("Factory method '$factoryMethod' present in factory class '${factoryClass.canonicalName}' must returns a class that extends 'Event' class (currentClass: ${eventTypeInfo.typeClass.canonicalName}).", MessageType.INVALID_FACTORY_METHOD)
                                    fail()
                                }

                                val methodDeclaration = factoryMethod.toMethodDeclaration { index, parameter ->
                                    parameterNames[index]
                                }

                                val methodBody = methodDeclaration.body as MutableCodeSource

                                if (isGeneric) {
                                    val mode = eventGenerator.options[EventGeneratorOptions.GENERIC_EVENT_GENERATION_MODE]

                                    when (mode) {
                                        EventGeneratorOptions.GenericGenerationMode.BOOTSTRAP ->
                                            methodBody += genCallToDynamicGeneration(
                                                    eventTypeInfo.typeClass,
                                                    eventGeneratorField.name,
                                                    additionalProperties,
                                                    extensions,
                                                    provider.single(),
                                                    methodDeclaration.parameters)
                                        EventGeneratorOptions.GenericGenerationMode.REFLECTION ->
                                            methodBody.addAll(
                                                    genCallToDynamicGenerationReflect(
                                                    eventTypeInfo.typeClass,
                                                    eventGeneratorField.name,
                                                    additionalProperties,
                                                    extensions,
                                                    provider.single(),
                                                    methodDeclaration.parameters))
                                    }

                                } else {

                                    val implClass = eventGenerator.createEventClass(eventTypeInfo, additionalProperties, extensions)

                                    methodBody.add(invokeEventConstructor(implClass, methodDeclaration, logger, eventType, factoryMethod))


                                }

                                return@mMap methodDeclaration
                            }


                        })
                .build()

        val generator = BytecodeProcessor()

        generator.options.set(VISIT_LINES, VisitLineType.FOLLOW_CODE_SOURCE)

        val bytecodeClass = generator.process(declaration)[0]

        val bytes = bytecodeClass.bytecode
        val disassembled = lazy { bytecodeClass.disassembledCode }

        @Suppress("UNCHECKED_CAST")
        val generatedEventClass = EventGenClassLoader.defineClass(declaration, bytes, disassembled) as GeneratedEventClass<T>

        if (Debug.FACTORY_GEN_DEBUG) {
            ClassSaver.save("factorygen", generatedEventClass)
        }

        return generatedEventClass.javaClass.let {
            val instance = it.getConstructor(EventGenerator::class.java).newInstance(eventGenerator)
            this.cached.put(factoryClass, instance)
            instance
        }
    }

    fun invokeEventConstructor(implClass: Class<*>,
                               methodDeclaration: MethodDeclaration,
                               logger: LoggerInterface,
                               eventType: Class<*>,
                               factoryMethod: Method): CodeInstruction {

        val ctr = implClass.declaredConstructors[0]

        val names by lazy { ctr.parameterNames }

        val arguments = ctr.parameters.mapIndexed<Parameter, CodeInstruction> map@ { index, it ->
            val name = it.getDeclaredAnnotation(Name::class.java)?.value
                    ?: names[index] // Should we remove it?

            if (!methodDeclaration.parameters.any { codeParameter ->
                codeParameter.name == it.name
                        && codeParameter.type.canonicalName == it.type.canonicalName
            }) {
                logger.log("Cannot find property '[name: $name, type: ${it.type.canonicalName}]' in factory method '${factoryMethod.toSimpleString()}'. Please provide a parameter with this name, use '-parameters' javac option to keep annotation names or annotate parameters with '@${Name::class.java.canonicalName}' annotation.",
                        MessageType.INVALID_FACTORY_METHOD,
                        IllegalStateException("Found properties: ${methodDeclaration.parameters.map { "${it.type.canonicalName} ${it.name}" }}. Required: ${ctr.parameters.contentToString()}."))
                fail()
            }

            if (name == "cancelled"
                    && it.type == java.lang.Boolean.TYPE
                    && Cancellable::class.java.isAssignableFrom(eventType))
                return@map Literals.FALSE

            return@map accessVariable(it.type.codeType, name)
        }

        return returnValue(
                eventType,
                implClass.invokeConstructor(constructorTypeSpec(*ctr.parameterTypes), arguments)
        )
    }

    private fun genCallToDynamicGeneration(evType: Class<*>,
                                           eventGeneratorParam: String,
                                           additionalProperties: MutableList<PropertyInfo>,
                                           extensions: List<ExtensionSpecification>,
                                           single: Parameter,
                                           params: List<CodeParameter>): CodeInstruction {

        val paramNames = createArray(Array<String>::class.java, listOf(Literals.INT(params.size - 1)),
                params.filterNot { it.name == single.name }.map { Literals.STRING(it.name) })

        val args = createArray(Array<Any>::class.java, listOf(Literals.INT(params.size - 1)),
                params.filterNot { it.name == single.name }.map { it.toVariableAccess() })

        return returnValue(evType, invokeDynamic(
                evType,
                MethodInvokeSpec(InvokeType.INVOKE_STATIC, FactoryBootstrap.BOOTSTRAP_SPEC),
                invoke(InvokeType.INVOKE_INTERFACE, evType, accessStatic(),
                        "create",
                        TypeSpec(evType,
                                listOf(EventGenerator::class.java,
                                        TypeInfo::class.java,
                                        List::class.java,
                                        List::class.java,
                                        Array<String>::class.java,
                                        Array<Any>::class.java)),
                        listOf(accessThisField(EventGenerator::class.java, eventGeneratorParam),
                                createTypeInfo(evType, single),
                                additionalProperties.asArgs(),
                                extensions.asArgs(),
                                paramNames,
                                args
                        )
                ),
                emptyList()
        ))
    }

    private fun genCallToDynamicGenerationReflect(evType: Class<*>,
                                                  eventGeneratorParam: String,
                                                  additionalProperties: MutableList<PropertyInfo>,
                                                  extensions: List<ExtensionSpecification>,
                                                  single: Parameter,
                                                  params: List<CodeParameter>): List<CodeInstruction> {
        val paramNames = createArray(Array<String>::class.java, listOf(Literals.INT(params.size - 1)),
                params.filterNot { it.name == single.name }.map { Literals.STRING(it.name) })

        val args = createArray(Array<Any>::class.java, listOf(Literals.INT(params.size - 1)),
                params.filterNot { it.name == single.name }.map { it.toVariableAccess() })

        val insns = mutableListOf<CodeInstruction>()

        val eventClassVar = VariableRef(Types.CLASS, "eventClass")

        insns += variable(eventClassVar.type, eventClassVar.name,
                accessThisField(EventGenerator::class.java, eventGeneratorParam)
                .invokeInterface(EventGenerator::class.java,
                        "createEventClass",
                        // eventType, additionalParameters, extensions
                        TypeSpec(Types.CLASS, listOf(TypeInfo::class.java, List::class.java, List::class.java)),
                        listOf(createTypeInfo(evType, single),
                                additionalProperties.asArgs(),
                                extensions.asArgs()
                        )
                ))

        val getFirstCtr = accessArrayValue(Constructor::class.java.codeType.toArray(1),
                invokeVirtual(Class::class.java,
                        accessVariable(eventClassVar.type, eventClassVar.name),
                        "getDeclaredConstructors",
                        TypeSpec(Constructor::class.java.codeType.toArray(1)),
                        listOf()
                ),
                Literals.INT(0),
                Constructor::class.java)

        val ctrVar = VariableRef(Constructor::class.java, "ctr")

        insns += variable(ctrVar.type, ctrVar.name, getFirstCtr)

        val sortArgs = PropertiesSort::class.java.invokeStatic("sort",
                TypeSpec(Array<Any>::class.java, listOf(
                        Constructor::class.java, Array<String>::class.java, Array<Any>::class.java
                )),
                listOf(accessVariable(ctrVar),
                        paramNames,
                        args
                ))

        val sortedVar = VariableRef(Array<Any>::class.java, "sorted")
        insns += variable(sortedVar.type, sortedVar.name, sortArgs)

        insns += returnValue(evType, invokeVirtual(Constructor::class.java,
                accessVariable(ctrVar),
                "newInstance",
                TypeSpec(Object::class.java, listOf(Array<Any>::class.java)),
                listOf(accessVariable(sortedVar))
        ))

        return insns
    }

    fun createTypeInfo(evType: Class<*>, parameter: Parameter): CodeInstruction {

        return TypeInfo::class.java.invokeStatic("builderOf",
                TypeSpec(TypeInfoBuilder::class.java, listOf(Class::class.java)),
                listOf(Literals.CLASS(evType))
        ).invokeVirtual(TypeInfoBuilder::class.java, "of",
                TypeSpec(TypeInfoBuilder::class.java, listOf(TypeInfo::class.java.codeType.toArray(1))),
                listOf(createArray(TypeInfo::class.java.codeType.toArray(1), listOf(Literals.INT(1)),
                        listOf(parameter.toVariableAccess())
                ))
        ).invokeVirtual(TypeInfoBuilder::class.java, "build", TypeSpec(TypeInfo::class.java), emptyList())
    }

    fun List<PropertyInfo>.asArgs(): CodeInstruction {
        return Collections3::class.java.invokeStatic("listOf",
                TypeSpec(List::class.java, listOf(Array<Any>::class.java)),
                listOf(createArray(Array<Any>::class.java, listOf(Literals.INT(this.size)),
                        this.map { it.asArg() })
                ))
    }

    @JvmName("asArgs2")
    fun List<ExtensionSpecification>.asArgs(): CodeInstruction {
        return Collections3::class.java.invokeStatic("listOf",
                TypeSpec(List::class.java, listOf(Array<Any>::class.java)),
                listOf(createArray(Array<Any>::class.java, listOf(Literals.INT(this.size)),
                        this.map { it.asArg() })
                ))
    }

    fun PropertyInfo.asArg(): CodeInstruction {
        return PropertyInfo::class.java.invokeConstructor(
                // propertyName, getterName, setterName
                constructorTypeSpec(Types.STRING, Types.STRING, Types.STRING,
                        // type, itsNotNull, validator
                        Types.CLASS, Types.BOOLEAN, Types.CLASS),
                listOf<CodeInstruction>(
                        Literals.STRING(this.propertyName),
                        this.getterName?.let { Literals.STRING(it) } ?: Literals.NULL,
                        this.setterName?.let { Literals.STRING(it) } ?: Literals.NULL,
                        Literals.CLASS(this.type),
                        Literals.BOOLEAN(this.isNotNull),
                        this.validator?.let { Literals.CLASS(it) } ?: Literals.NULL
                )
        )
    }

    fun ExtensionSpecification.asArg(): CodeInstruction {
        return ExtensionSpecification::class.java.invokeConstructor(
                // residence, implement, extensionClass
                constructorTypeSpec(Types.OBJECT, Types.CLASS, Types.CLASS),
                listOf(
                        Access.THIS,
                        this.implement?.let { Literals.CLASS(it) } ?: Literals.NULL,
                        this.extensionClass?.let { Literals.CLASS(it) } ?: Literals.NULL
                )
        )
    }

    fun CodeType.usesParameters(parameters: Array<TypeVariable<Method>>): Boolean {
        if (this !is GenericType)
            return false

        if (!this.isType && !this.isWildcard && parameters.any { it.name == this.name })
            return true
        else return this.bounds.map { it.type }.any { it.usesParameters(parameters) }
    }
}

