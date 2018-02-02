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
package com.github.projectsandstone.eventsys.gen.event

import com.github.jonathanxd.kores.Instruction
import com.github.jonathanxd.kores.Instructions
import com.github.jonathanxd.kores.MutableInstructions
import com.github.jonathanxd.kores.Types
import com.github.jonathanxd.kores.base.*
import com.github.jonathanxd.kores.bytecode.VISIT_LINES
import com.github.jonathanxd.kores.bytecode.VisitLineType
import com.github.jonathanxd.kores.bytecode.processor.BytecodeGenerator
import com.github.jonathanxd.kores.common.DynamicMethodSpec
import com.github.jonathanxd.kores.common.MethodInvokeSpec
import com.github.jonathanxd.kores.common.Nothing
import com.github.jonathanxd.kores.common.VariableRef
import com.github.jonathanxd.kores.factory.*
import com.github.jonathanxd.kores.generic.GenericSignature
import com.github.jonathanxd.kores.literal.Literals
import com.github.jonathanxd.kores.type.KoresType
import com.github.jonathanxd.kores.type.Generic
import com.github.jonathanxd.kores.type.GenericType
import com.github.jonathanxd.kores.type.LoadedKoresType
import com.github.jonathanxd.kores.type.asGeneric
import com.github.jonathanxd.kores.type.canonicalName
import com.github.jonathanxd.kores.type.koresType
import com.github.jonathanxd.kores.util.conversion.parameterNames
import com.github.jonathanxd.kores.util.conversion.toMethodDeclaration
import com.github.jonathanxd.kores.util.conversion.toVariableAccess
import com.github.jonathanxd.iutils.`object`.Default
import com.github.jonathanxd.iutils.collection.Collections3
import com.github.jonathanxd.iutils.type.TypeInfo
import com.github.projectsandstone.eventsys.Debug
import com.github.projectsandstone.eventsys.bootstrap.FactoryBootstrap
import com.github.projectsandstone.eventsys.event.Cancellable
import com.github.projectsandstone.eventsys.event.Event
import com.github.projectsandstone.eventsys.event.annotation.*
import com.github.projectsandstone.eventsys.extension.ExtensionSpecification
import com.github.projectsandstone.eventsys.gen.GeneratedEventClass
import com.github.projectsandstone.eventsys.gen.save.ClassSaver
import com.github.projectsandstone.eventsys.logging.LoggerInterface
import com.github.projectsandstone.eventsys.logging.MessageType
import com.github.projectsandstone.eventsys.reflect.PropertiesSort
import com.github.projectsandstone.eventsys.reflect.findImplementation
import com.github.projectsandstone.eventsys.reflect.getAllAnnotationsOfType
import com.github.projectsandstone.eventsys.util.JavaCodePartUtil
import com.github.projectsandstone.eventsys.util.fail
import com.github.projectsandstone.eventsys.util.toSimpleString
import java.lang.reflect.*
import java.util.concurrent.atomic.AtomicInteger

/**
 * This class generates implementation of an event factory interface, this method will create the event class
 * and direct-call the constructor. Factory generated events cannot have [Extensions][Extension] plugged
 * after first generation of classes (does not apply to factories annotated with [LazyGeneration]).
 *
 * Additional properties that are mutable must be annotated with [Mutable] annotation.
 *
 * Extensions are provided via [Extension] annotation in the factory method.
 */
internal object EventFactoryClassGenerator {

    /**
     * Create [factoryClass] instance invoking generated event classes constructor.
     */
    @Suppress("UNCHECKED_CAST")
    internal fun <T : Any> create(eventGenerator: EventGenerator,
                                  factoryClass: Class<T>,
                                  logger: LoggerInterface): T {

        val checkHandler = eventGenerator.checkHandler

        checkHandler.validateFactoryClass(factoryClass, eventGenerator)

        val eventGeneratorField = VariableRef(EventGenerator::class.java, "eventGenerator")

        val declaration = ClassDeclaration.Builder.builder()
                .modifiers(KoresModifier.PUBLIC)
                .qualifiedName("${factoryClass.canonicalName}\$Impl")
                .implementations(factoryClass.koresType)
                .superClass(Types.OBJECT)
                .fields(FieldDeclaration.Builder()
                        .modifiers(KoresModifier.PRIVATE, KoresModifier.FINAL)
                        .base(eventGeneratorField)
                        .build()
                )
                .constructors(ConstructorDeclaration.Builder.builder()
                        .modifiers(KoresModifier.PUBLIC)
                        .parameters(parameter(name = eventGeneratorField.name, type = eventGeneratorField.type))
                        .body(Instructions.fromVarArgs(
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

                                val methodDeclaration = factoryMethod.toMethodDeclaration()
                                val methodBody = methodDeclaration.body as MutableInstructions
                                methodBody.add(this.genDefaultInvocation(kFunc, impl))

                                return@mMap methodDeclaration
                            } else {

                                val eventType = factoryMethod.returnType

                                checkHandler.validateEventClass(eventType, factoryMethod, eventGenerator)

                                val parameterNames = getNames(factoryMethod)
                                val extensions = getExtensions(eventType, factoryMethod)

                                val properties = getProperties(eventType, emptyList(), extensions)
                                val additionalProperties = mutableListOf<PropertyInfo>()

                                val eventTypeInfo = TypeInfo.of(eventType) as TypeInfo<Event>

                                val isLazyGen = factoryMethod.isAnnotationPresent(LazyGeneration::class.java)

                                val provider = factoryMethod.parameters
                                        .filter { it.isAnnotationPresent(TypeParam::class.java) }

                                extractAdditionalPropertiesTo(
                                        additionalProperties,
                                        factoryMethod,
                                        properties,
                                        parameterNames
                                )

                                val methodDeclaration = factoryMethod.toMethodDeclaration { index, _ ->
                                    parameterNames[index]
                                }

                                val methodBody = methodDeclaration.body as MutableInstructions

                                if (isLazyGen) {
                                    // || isSpecialized
                                    val mode: LazyGenerationMode = eventGenerator
                                            .options[EventGeneratorOptions.LAZY_EVENT_GENERATION_MODE]

                                    when (mode) {
                                        LazyGenerationMode.BOOTSTRAP ->
                                            methodBody += genCallToDynamicGeneration(
                                                    eventTypeInfo.typeClass,
                                                    eventGeneratorField.name,
                                                    additionalProperties,
                                                    extensions,
                                                    provider.singleOrNull(),
                                                    methodDeclaration.parameters)
                                        LazyGenerationMode.REFLECTION ->
                                            methodBody.addAll(
                                                    genCallToDynamicGenerationReflect(
                                                            eventTypeInfo.typeClass,
                                                            eventGeneratorField.name,
                                                            additionalProperties,
                                                            extensions,
                                                            provider.singleOrNull(),
                                                            methodDeclaration.parameters
                                                    ))

                                    }

                                } else {

                                    val implClass = eventGenerator.createEventClass(
                                            eventTypeInfo,
                                            additionalProperties,
                                            extensions
                                    )

                                    methodBody.add(invokeEventConstructor(
                                            implClass,
                                            eventTypeInfo,
                                            methodDeclaration,
                                            logger,
                                            eventType,
                                            provider.singleOrNull(),
                                            factoryMethod
                                    ))

                                }

                                return@mMap methodDeclaration
                            }


                        })
                .build()

        val generator = BytecodeGenerator()

        generator.options.set(VISIT_LINES, VisitLineType.GEN_LINE_INSTRUCTION)

        val bytecodeClass = try {
            generator.process(declaration)[0]
        } catch (t: Throwable) {
            throw IllegalStateException("Failed to generate factory implementation of ${factoryClass.simpleName}. Declaration: $declaration", t)
        }

        val bytes = bytecodeClass.bytecode
        val disassembled = lazy(LazyThreadSafetyMode.NONE) { bytecodeClass.disassembledCode }

        @Suppress("UNCHECKED_CAST")
        val generatedEventClass = EventGenClassLoader.defineClass(declaration, bytes, disassembled) as GeneratedEventClass<T>

        if (Debug.FACTORY_GEN_DEBUG) {
            ClassSaver.save("factorygen", generatedEventClass)
        }

        return generatedEventClass.javaClass.getConstructor(EventGenerator::class.java).newInstance(eventGenerator)
    }

    private fun getExtensions(eventType: Class<*>, factoryMethod: Method): List<ExtensionSpecification> {
        val allExtensionAnnotations = mutableListOf<Extension>()

        allExtensionAnnotations += eventType.getAllAnnotationsOfType(Extension::class.java)
        allExtensionAnnotations += factoryMethod.getDeclaredAnnotationsByType(Extension::class.java)

        return allExtensionAnnotations.map {
            val implement = it.implement.java
                    .let { if (it == Default::class.java) null else it }

            val extension = it.extensionClass.java
                    .let { if (it == Default::class.java) null else it }

            ExtensionSpecification(factoryMethod, implement, extension)
        }
    }

    private fun getNames(factoryMethod: Method): List<String> {
        val ktNames by lazy(LazyThreadSafetyMode.NONE) {
            factoryMethod.parameterNames
        }

        return factoryMethod.parameters.mapIndexed { i, it ->
            if (it.isAnnotationPresent(Name::class.java))
                it.getDeclaredAnnotation(Name::class.java).value
            else if (it.isAnnotationPresent(TypeParam::class.java))
                eventTypeInfoFieldName
            else ktNames[i]
        }

    }

    private fun genDefaultInvocation(kFunc: Method, impl: Pair<Class<*>, Method>): Instruction {
        val base = kFunc
        val delegateClass = impl.first
        val delegate = impl.second

        val parameters = base.parameterNames.mapIndexed { i, it ->
            parameter(type = delegate.parameters[i + 1].type.koresType, name = it)
        }

        val arguments = mutableListOf<Instruction>(Access.THIS) + parameters.map { it.toVariableAccess() }

        return invoke(
                InvokeType.INVOKE_STATIC,
                delegateClass.koresType,
                Access.STATIC,
                delegate.name,
                typeSpec(delegate.returnType.koresType, delegate.parameters.map { it.type.koresType }),
                arguments
        ).let {
            if (kFunc.returnType == Void.TYPE)
                it
            else
                returnValue(kFunc.returnType.koresType, it)
        }
    }

    private fun extractAdditionalPropertiesTo(additionalProperties: MutableList<PropertyInfo>,
                                              factoryMethod: Method,
                                              properties: List<PropertyInfo>,
                                              parameterNames: List<String>) {
        factoryMethod.parameters.forEachIndexed { i, parameter ->
            val find = properties.any { it.propertyName == parameterNames[i] && it.type == parameter.type }

            if (!find && !parameter.isAnnotationPresent(TypeParam::class.java)) {
                val name = parameterNames[i]

                val getterName = "get${name.capitalize()}"
                val setterName = if (parameter.isAnnotationPresent(Mutable::class.java)) "set${name.capitalize()}" else null

                additionalProperties += PropertyInfo(
                        declaringType = Nothing::class.java,
                        propertyName = name,
                        type = parameter.type,
                        getterName = getterName,
                        setterName = setterName,
                        validator = parameter.getDeclaredAnnotation(Validate::class.java)?.value?.java,
                        isNotNull = parameter.isAnnotationPresent(NotNullValue::class.java),
                        propertyTypeInfo = PropertyTypeInfo(
                                parameter.parameterizedType.koresType.asGeneric,
                                GenericSignature.create(*factoryMethod.typeParameters
                                        .map { it.koresType.asGeneric }.toTypedArray())
                        )
                )
            }
        }
    }

    private fun invokeEventConstructor(implClass: Class<*>,
                                       eventTypeInfo: TypeInfo<*>,
                                       methodDeclaration: MethodDeclaration,
                                       logger: LoggerInterface,
                                       eventType: Class<*>,
                                       specParam: Parameter?,
                                       factoryMethod: Method): Instruction {

        val ctr = implClass.declaredConstructors[0]

        val names by lazy(LazyThreadSafetyMode.NONE) {
            ctr.parameterNames
        }

        val arguments = ctr.parameters.mapIndexed<Parameter, Instruction> map@ { index, it ->

            if (it.isAnnotationPresent(TypeParam::class.java)) {
                return@map if (specParam != null)
                    accessVariable(Generic.type(TypeInfo::class.java), eventTypeInfoFieldName)
                else
                    createTypeInfo(eventTypeInfo.typeClass)
            }

            val name = it.getDeclaredAnnotation(Name::class.java)?.value
                    ?: names[index] // Should we remove it?


            if (!methodDeclaration.parameters.any { koresParameter ->
                koresParameter.name == name
                        && koresParameter.type.canonicalName == it.type.canonicalName
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

            return@map accessVariable(it.type.koresType, name)
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
                                           single: Parameter?,
                                           params: List<KoresParameter>): Instruction {

        val (paramNames, args) = getNamesAndArgsArrs(params)

        return returnValue(evType, invokeDynamic(
                MethodInvokeSpec(InvokeType.INVOKE_STATIC, FactoryBootstrap.BOOTSTRAP_SPEC),
                DynamicMethodSpec(
                        "create",
                        TypeSpec(evType,
                                listOf(EventGenerator::class.java,
                                        TypeInfo::class.java,
                                        List::class.java,
                                        List::class.java,
                                        Array<String>::class.java,
                                        Array<Any>::class.java)),
                        listOf(accessThisField(EventGenerator::class.java, eventGeneratorParam),
                                createTypeInfo(evType),
                                additionalProperties.asPropArgs(),
                                extensions.asExtArgs(),
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
                                                  single: Parameter?,
                                                  params: List<KoresParameter>): List<Instruction> {

        val (paramNames, args) = getNamesAndArgsArrs(params)

        val insns = mutableListOf<Instruction>()

        val eventClassVar = VariableRef(Types.CLASS, "eventClass")

        insns += variable(eventClassVar.type, eventClassVar.name,
                accessThisField(EventGenerator::class.java, eventGeneratorParam)
                        .invokeInterface(EventGenerator::class.java,
                                "createEventClass",
                                // eventType, additionalParameters, extensions
                                TypeSpec(Types.CLASS, listOf(TypeInfo::class.java, List::class.java, List::class.java)),
                                listOf(createTypeInfo(evType),
                                        additionalProperties.asPropArgs(),
                                        extensions.asExtArgs()
                                )
                        ))

        val getFirstCtr = accessArrayValue(Constructor::class.java.koresType.toArray(1),
                invokeVirtual(Class::class.java,
                        accessVariable(eventClassVar.type, eventClassVar.name),
                        "getDeclaredConstructors",
                        TypeSpec(Constructor::class.java.koresType.toArray(1)),
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

    private fun getNamesAndArgs(params: List<KoresParameter>) =
            params.map { Literals.STRING(it.name) } to params.map {
                it.toVariableAccess()
            }

    private fun getNamesAndArgsArrs(params: List<KoresParameter>) =
            getNamesAndArgs(params).let { (pNames, cArgs) ->
                createArray(Array<String>::class.java, listOf(Literals.INT(pNames.size)),
                        pNames) to createArray(Array<Any>::class.java, listOf(Literals.INT(cArgs.size)),
                        cArgs)
            }

    private fun List<PropertyInfo>.asPropArgs(): Instruction {
        return Collections3::class.java.invokeStatic("listOf",
                TypeSpec(List::class.java, listOf(Array<Any>::class.java)),
                listOf(createArray(Array<Any>::class.java, listOf(Literals.INT(this.size)),
                        this.map { it.asArg() }
                )))
    }

    private fun List<ExtensionSpecification>.asExtArgs(): Instruction {
        return Collections3::class.java.invokeStatic("listOf",
                TypeSpec(List::class.java, listOf(Array<Any>::class.java)),
                listOf(createArray(Array<Any>::class.java, listOf(Literals.INT(this.size)),
                        this.map { it.asArg() })
                ))
    }

    private fun PropertyInfo.asArg(): Instruction {
        return PropertyInfo::class.java.invokeConstructor(

                constructorTypeSpec(Types.CLASS, // declaringClass
                        // propertyName, getterName, setterName
                        Types.STRING, Types.STRING, Types.STRING,
                        // type, itsNotNull, validator
                        Types.CLASS, Types.BOOLEAN, Types.CLASS,
                        //propertyTypeInfo, inferredType
                        PropertyTypeInfo::class.java, Type::class.java
                ),
                listOf(
                        Literals.CLASS(Nothing::class.java),
                        Literals.STRING(this.propertyName),
                        this.getterName?.let { Literals.STRING(it) } ?: Literals.NULL,
                        this.setterName?.let { Literals.STRING(it) } ?: Literals.NULL,
                        Literals.CLASS(this.type),
                        Literals.BOOLEAN(this.isNotNull),
                        this.validator?.let { Literals.CLASS(it) } ?: Literals.NULL,
                        this.propertyTypeInfo.toArg(),
                        Literals.CLASS(this.type)
                )
        )
    }

    private fun PropertyTypeInfo.toArg(): Instruction {
        return PropertyTypeInfo::class.java.invokeConstructor(
                constructorTypeSpec(
                        GenericType::class.java,
                        GenericSignature::class.java
                ),
                listOf(this.type.toArg(), this.definedParams.toArg())
        )
    }

    private fun GenericSignature.toArg(): Instruction =
            GenericSignature::class.java.invokeStatic(
                    "create",
                    TypeSpec(GenericSignature::class.java, listOf(Array<GenericType>::class.java)),
                    listOf(this.types.map { it.toArg() }.let {
                        createArray(Array<GenericType>::class.java, listOf(Literals.INT(it.size)), it)
                    })
            )

}

fun GenericType.toArg(): Instruction {
    val insn = when {
        this.isType -> Generic::class.java.invokeStatic(
                "type",
                TypeSpec(Generic::class.java, listOf(KoresType::class.java)),
                listOf(this.resolvedType.toArg())
        )
        this.isWildcard -> Generic::class.java.invokeStatic(
                "wildcard",
                TypeSpec(Generic::class.java),
                emptyList()
        )
        else -> Generic::class.java.invokeStatic(
                "type",
                TypeSpec(Generic::class.java, listOf(Types.STRING)),
                listOf(Literals.STRING(this.name))
        )
    }

    return if (this.bounds.isEmpty()) {
        insn
    } else {
        val args = this.bounds.toArgs()
        val arrType = GenericType.Bound::class.java.koresType.toArray(1)

        invokeVirtual(Generic::class.java,
                insn,
                "of",
                TypeSpec(Generic::class.java, listOf(arrType)),
                listOf(createArray(arrType, listOf(Literals.INT(args.size)), args))
        )
    }
}

fun Array<out GenericType.Bound>.toArgs(): List<Instruction> =
        this.map { it.toArg() }

fun GenericType.Bound.toArg(): Instruction =
        this::class.java.invokeConstructor(
                constructorTypeSpec(GenericType::class.java),
                listOf(this.type.toArg())
        )


fun Instruction.callGetKoresType(): Instruction =
        JavaCodePartUtil.callGetKoresType(this)

fun KoresType.toArg(): Instruction =
        when {
            this is LoadedKoresType<*> -> Literals.CLASS(this.loadedType.koresType).callGetKoresType()
            this is GenericType -> this.toArg()
            else -> Literals.CLASS(this).callGetKoresType()
        }

fun ExtensionSpecification.asArg(): Instruction {
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

fun KoresType.usesParameters(parameters: Array<TypeVariable<Method>>): Boolean {
    if (this !is GenericType)
        return false

    return if (!this.isType && !this.isWildcard && parameters.any { it.name == this.name })
        true
    else this.bounds.map { it.type }.any { it.usesParameters(parameters) }
}


fun createTypeInfo(evType: Class<*>): Instruction =
        TypeInfo::class.java.invokeStatic("of",
                TypeSpec(TypeInfo::class.java, listOf(Class::class.java)),
                listOf(Literals.CLASS(evType))
        )

