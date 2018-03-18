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

import com.github.jonathanxd.iutils.`object`.Default
import com.github.jonathanxd.iutils.collection.Collections3
import com.github.jonathanxd.iutils.exception.RethrowException
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
import com.github.jonathanxd.kores.type.*
import com.github.jonathanxd.kores.util.conversion.toVariableAccess
import com.github.projectsandstone.eventsys.Debug
import com.github.projectsandstone.eventsys.bootstrap.FactoryBootstrap
import com.github.projectsandstone.eventsys.event.Cancellable
import com.github.projectsandstone.eventsys.event.Event
import com.github.projectsandstone.eventsys.event.annotation.*
import com.github.projectsandstone.eventsys.extension.ExtensionSpecification
import com.github.projectsandstone.eventsys.gen.GeneratedEventClass
import com.github.projectsandstone.eventsys.gen.GenerationEnvironment
import com.github.projectsandstone.eventsys.gen.ResolvableDeclaration
import com.github.projectsandstone.eventsys.gen.save.ClassSaver
import com.github.projectsandstone.eventsys.logging.LoggerInterface
import com.github.projectsandstone.eventsys.logging.MessageType
import com.github.projectsandstone.eventsys.reflect.PropertiesSort
import com.github.projectsandstone.eventsys.reflect.findImplementation
import com.github.projectsandstone.eventsys.reflect.getAllKoresAnnotationsOfType
import com.github.projectsandstone.eventsys.util.*
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.lang.reflect.Type
import java.lang.reflect.TypeVariable
import java.util.concurrent.CompletableFuture

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
     * Create [factoryType] instance invoking generated event classes constructor.
     */
    @Suppress("UNCHECKED_CAST")
    internal fun <T : Any> create(
        eventGenerator: EventGenerator,
        factoryType: Type,
        logger: LoggerInterface,
        generationEnvironment: GenerationEnvironment
    ): ResolvableDeclaration<T> {
        val (declaration, resolves) =
                createDeclaration(eventGenerator, factoryType, logger, generationEnvironment)

        val resolver = lazy(LazyThreadSafetyMode.NONE) {
            resolves.forEach { it.resolve() }

            val generator = BytecodeGenerator()

            generator.options.set(VISIT_LINES, VisitLineType.GEN_LINE_INSTRUCTION)

            val bytecodeClass = try {
                generator.process(declaration)[0]
            } catch (t: Throwable) {
                throw IllegalStateException(
                    "Failed to generate factory implementation of ${factoryType.simpleName}. Declaration: $declaration",
                    t
                )
            }

            val bytes = bytecodeClass.bytecode
            val disassembled = lazy(LazyThreadSafetyMode.NONE) { bytecodeClass.disassembledCode }

            @Suppress("UNCHECKED_CAST")
            val generatedEventClass = EventGenClassLoader.defineClass(
                declaration,
                bytes,
                disassembled
            ) as GeneratedEventClass<T>

            if (Debug.FACTORY_GEN_DEBUG) {
                ClassSaver.save("factorygen", generatedEventClass)
            }

            generatedEventClass.javaClass.getConstructor(EventGenerator::class.java)
                .newInstance(eventGenerator)
        }

        return ResolvableDeclaration(declaration, resolver)
    }

    /**
     * Create [factoryType] instance invoking generated event classes constructor.
     */
    @Suppress("UNCHECKED_CAST")
    internal fun createDeclaration(
        eventGenerator: EventGenerator,
        factoryType: Type,
        logger: LoggerInterface,
        generationEnvironment: GenerationEnvironment
    ): Pair<ClassDeclaration, List<ResolvableDeclaration<*>>> {

        val cache = generationEnvironment.declarationCache
        val factoryDeclaration = cache[factoryType]
        val checkHandler = eventGenerator.checkHandler

        checkHandler.validateFactoryClass(factoryDeclaration, eventGenerator)

        val eventGeneratorField = VariableRef(EventGenerator::class.java, "eventGenerator")

        val futures = mutableListOf<CompletableFuture<*>>()

        val declaration = ClassDeclaration.Builder.builder()
            .modifiers(KoresModifier.PUBLIC)
            .qualifiedName("${factoryDeclaration.canonicalName}\$Impl")
            .implementations(factoryDeclaration)
            .superClass(Types.OBJECT)
            .fields(
                FieldDeclaration.Builder()
                    .modifiers(KoresModifier.PRIVATE, KoresModifier.FINAL)
                    .base(eventGeneratorField)
                    .build()
            )
            .constructors(
                ConstructorDeclaration.Builder.builder()
                    .modifiers(KoresModifier.PUBLIC)
                    .parameters(
                        parameter(
                            name = eventGeneratorField.name,
                            type = eventGeneratorField.type
                        )
                    )
                    .body(
                        Instructions.fromVarArgs(
                            setThisFieldValue(
                                eventGeneratorField.type, eventGeneratorField.name,
                                accessVariable(eventGeneratorField)
                            )
                        )
                    )
                    .build()
            )
            .methods(cache.getMethods(factoryDeclaration)
                .filter {
                    it.methodDeclaration.body.isEmpty
                            && !it.methodDeclaration.modifiers.contains(KoresModifier.DEFAULT)
                }
                .map mMap@{ declaredMethod ->

                    val (declaring, factoryMethod) = declaredMethod

                    val impl = findImplementation(declaring, declaredMethod, cache)

                    if (impl != null) {
                        val methodDeclaration =
                            factoryMethod.copy(
                                modifiers = setOf(KoresModifier.PUBLIC),
                                parameters = factoryMethod.parameters.map {
                                    it.copy(
                                        name = getName(
                                            it
                                        )
                                    )
                                },
                                body = MutableInstructions.create()
                            )
                        val methodBody = methodDeclaration.body as MutableInstructions
                        methodBody.add(
                            this.genDefaultInvocation(
                                declaredMethod.methodDeclaration,
                                impl
                            )
                        )

                        return@mMap methodDeclaration
                    } else {

                        val eventType = factoryMethod.returnType

                        checkHandler.validateEventClass(
                            cache[eventType],
                            factoryMethod,
                            eventGenerator
                        )

                        val parameterNames = getNames(factoryMethod)
                        val extensions = getExtensions(eventType, factoryMethod, cache)

                        val properties =
                            getProperties(cache[eventType], emptyList(), extensions, cache)
                        val additionalProperties = mutableListOf<PropertyInfo>()

                        val isLazyGen =
                            factoryMethod.annotations.any { it.type.`is`(typeOf<LazyGeneration>()) }

                        val provider = factoryMethod.parameters
                            .filter { it.type.`is`(typeOf<TypeParam>()) }

                        extractAdditionalPropertiesTo(
                            additionalProperties,
                            factoryMethod,
                            properties,
                            parameterNames
                        )

                        val methodDeclaration = factoryMethod.copy(
                            modifiers = setOf(KoresModifier.PUBLIC),
                            parameters = factoryMethod.parameters.map {
                                it.copy(
                                    name = getName(
                                        it
                                    )
                                )
                            },
                            body = MutableInstructions.create()
                        )

                        val methodBody = methodDeclaration.body as MutableInstructions

                        if (isLazyGen) {
                            // || isSpecialized
                            val mode: LazyGenerationMode = eventGenerator
                                .options[EventGeneratorOptions.LAZY_EVENT_GENERATION_MODE]

                            when (mode) {
                                LazyGenerationMode.BOOTSTRAP ->
                                    methodBody += genCallToDynamicGeneration(
                                        eventType,
                                        eventGeneratorField.name,
                                        additionalProperties,
                                        extensions,
                                        methodDeclaration.parameters
                                    )
                                LazyGenerationMode.REFLECTION ->
                                    methodBody.addAll(
                                        genCallToDynamicGenerationReflect(
                                            eventType,
                                            eventGeneratorField.name,
                                            additionalProperties,
                                            extensions,
                                            methodDeclaration.parameters
                                        )
                                    )

                            }

                        } else {
                            futures += eventGenerator.createEventClassAsync<Event>(
                                eventType.concreteType,
                                additionalProperties,
                                extensions
                            ).apply {
                                thenAccept {
                                    methodBody.add(
                                        invokeEventConstructor(
                                            it.classDeclaration,
                                            eventType,
                                            methodDeclaration,
                                            logger,
                                            eventType,
                                            provider.singleOrNull(),
                                            declaredMethod
                                        )
                                    )
                                }
                            }

                        }

                        return@mMap methodDeclaration
                    }


                })
            .build()

        val c = CompletableFuture.allOf(*futures.toTypedArray())

        c.join()

        return declaration to futures.map { it.get() as ResolvableDeclaration<*> }
    }

    private fun getExtensions(
        eventType: Type,
        factoryMethod: MethodDeclaration,
        cache: DeclarationCache
    ): List<ExtensionSpecification> {
        val allExtensionAnnotations = mutableListOf<KoresAnnotation>() // KoresAnnotation<Extension>

        val eventTypeDeclaration = cache[eventType]
        allExtensionAnnotations += eventTypeDeclaration.getAllKoresAnnotationsOfType(Extension::class.java)
        allExtensionAnnotations += factoryMethod.annotations.filter { it.type.`is`(Extension::class.java) }

        return allExtensionAnnotations.map {
            val implement = it.values["implement"]
                ?.let { if (it !is Type || it == Default::class.java) null else it }?.koresType

            val extension = it.values["extensionClass"]
                ?.let { if (it !is Type || it == Default::class.java) null else it }?.koresType

            ExtensionSpecification(factoryMethod, implement, extension)
        }
    }

    private fun getNames(factoryMethod: MethodDeclaration): List<String> =
            /*val ktNames by lazy(LazyThreadSafetyMode.NONE) {
                factoryMethod.parameterNames
            }*/
        factoryMethod.parameters.mapIndexed { i, it ->
            when {
                it.annotations.any { it.type.`is`(typeOf<Name>()) } -> it.annotations.first {
                    it.type.`is`(
                        typeOf<Name>()
                    )
                }.values["value"] as String
                it.annotations.any { it.type.`is`(typeOf<TypeParam>()) } -> eventTypeFieldName
                else -> it.name
            }
        }


    private fun genDefaultInvocation(
        kFunc: MethodDeclaration,
        impl: Pair<Type, MethodDeclaration>
    ): Instruction {
        val base = kFunc
        val delegateClass = impl.first
        val delegate = impl.second

        val parameters = base.parameters.map { it.name }.mapIndexed { i, it ->
            parameter(type = delegate.parameters[i + 1].type.koresType, name = it)
        }

        val arguments =
            mutableListOf<Instruction>(Access.THIS) + parameters.map { it.toVariableAccess() }

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

    private fun extractAdditionalPropertiesTo(
        additionalProperties: MutableList<PropertyInfo>,
        factoryMethod: MethodDeclaration,
        properties: List<PropertyInfo>,
        parameterNames: List<String>
    ) {
        factoryMethod.parameters.forEachIndexed { i, parameter ->
            val find =
                properties.any { it.propertyName == parameterNames[i] && it.type == parameter.type }

            if (!find && !parameter.isAnnotationPresent(TypeParam::class.java)) {
                val name = parameterNames[i]

                val getterName = "get${name.capitalize()}"
                val setterName =
                    if (parameter.isAnnotationPresent(Mutable::class.java)) "set${name.capitalize()}" else null

                additionalProperties += PropertyInfo(
                    declaringType = Nothing::class.java,
                    propertyName = name,
                    type = parameter.type,
                    getterName = getterName,
                    setterName = setterName,
                    validator = parameter.getDeclaredAnnotation(Validate::class.java)?.values?.get("value") as? Type,
                    isNotNull = parameter.isAnnotationPresent(NotNullValue::class.java),
                    propertyType = PropertyType(
                        parameter.type.asGeneric,
                        factoryMethod.genericSignature
                    )
                )
            }
        }
    }

    private fun invokeEventConstructor(
        implClass: TypeDeclaration,
        eventGType: Type,
        methodDeclaration: MethodDeclaration,
        logger: LoggerInterface,
        eventType: Type,
        specParam: KoresParameter?,
        factoryMethod: DeclaredMethod
    ): Instruction {

        val ctr = (implClass as ConstructorsHolder).constructors.first()

        val arguments = ctr.parameters.mapIndexed map@{ index, it ->

            if (it.isAnnotationPresent(typeOf<TypeParam>())) {
                return@map if (specParam != null)
                    accessVariable(Generic.type(GenericType::class.java), eventTypeFieldName)
                else
                    createGenericType(eventGType)
            }

            val name = it.getDeclaredAnnotation(typeOf<Name>())
                ?.values
                ?.get("value") as? String
                    ?: ctr.parameters[index].name // Should we remove it?

            if (methodDeclaration.parameters.none { koresParameter ->
                        getName(koresParameter) == name
                                && koresParameter.type.canonicalName == it.type.canonicalName
                    }) {
                logger.log(
                    "Cannot find property '[name: $name, type: ${it.type.canonicalName}]' in factory method '${factoryMethod.toSimpleString()}'. Please provide a parameter with this name, use '-parameters' javac option to keep annotation names or annotate parameters with '@${Name::class.java.canonicalName}' annotation.",
                    MessageType.INVALID_FACTORY_METHOD,
                    IllegalStateException("Found properties: ${methodDeclaration.parameters.map {
                        "${it.type.canonicalName} ${getName(
                            it
                        )}"
                    }}. Required: ${ctr.parameters}.")
                )
                fail()
            }

            if (name == "cancelled"
                    && it.type == java.lang.Boolean.TYPE
                    && Cancellable::class.java.isAssignableFrom(eventType)
            )
                return@map Literals.FALSE

            return@map accessVariable(it.type.koresType, name)
        }

        return returnValue(
            eventType,
            implClass.invokeConstructor(
                constructorTypeSpec(*ctr.parameters.map { it.type }.toTypedArray()),
                arguments
            )
        )
    }

    private fun getName(parameter: KoresParameter): String =
        parameter.annotations.firstOrNull { it.type.`is`(typeOf<Name>()) }
            ?.values
            ?.get("value") as? String ?: parameter.name

    private fun genCallToDynamicGeneration(
        evType: Type,
        eventGeneratorParam: String,
        additionalProperties: MutableList<PropertyInfo>,
        extensions: List<ExtensionSpecification>,
        params: List<KoresParameter>
    ): Instruction {

        val (paramNames, args) = getNamesAndArgsArrs(params)

        return returnValue(
            evType, invokeDynamic(
                MethodInvokeSpec(InvokeType.INVOKE_STATIC, FactoryBootstrap.BOOTSTRAP_SPEC),
                DynamicMethodSpec(
                    "create",
                    TypeSpec(
                        evType,
                        listOf(
                            EventGenerator::class.java,
                            GenericType::class.java,
                            List::class.java,
                            List::class.java,
                            Array<String>::class.java,
                            Array<Any>::class.java
                        )
                    ),
                    listOf(
                        accessThisField(EventGenerator::class.java, eventGeneratorParam),
                        createGenericType(evType),
                        additionalProperties.asPropArgs(),
                        extensions.asExtArgs(),
                        paramNames,
                        args
                    )
                ),
                emptyList()
            )
        )
    }

    private fun genCallToDynamicGenerationReflect(
        evType: Type,
        eventGeneratorParam: String,
        additionalProperties: MutableList<PropertyInfo>,
        extensions: List<ExtensionSpecification>,
        params: List<KoresParameter>
    ): List<Instruction> {

        val (paramNames, args) = getNamesAndArgsArrs(params)

        val insns = mutableListOf<Instruction>()

        val eventClassVar = VariableRef(Types.CLASS, "eventClass")

        insns += variable(
            eventClassVar.type, eventClassVar.name,
            accessThisField(EventGenerator::class.java, eventGeneratorParam)
                .invokeInterface(
                    EventGenerator::class.java,
                    "createEventClass",
                    // eventType, additionalParameters, extensions
                    TypeSpec(
                        Types.CLASS,
                        listOf(Type::class.java, List::class.java, List::class.java)
                    ),
                    listOf(
                        createGenericType(evType),
                        additionalProperties.asPropArgs(),
                        extensions.asExtArgs()
                    )
                ).invokeVirtual(
                    typeOf<ResolvableDeclaration<*>>(),
                    "resolve",
                    typeSpec(typeOf<Any>()),
                    emptyList()
                )
        )

        val getFirstCtr = accessArrayValue(
            Constructor::class.java.koresType.toArray(1),
            invokeVirtual(
                Class::class.java,
                accessVariable(eventClassVar.type, eventClassVar.name),
                "getDeclaredConstructors",
                TypeSpec(Constructor::class.java.koresType.toArray(1)),
                listOf()
            ),
            Literals.INT(0),
            Constructor::class.java
        )

        val ctrVar = VariableRef(Constructor::class.java, "ctr")

        insns += variable(ctrVar.type, ctrVar.name, getFirstCtr)

        val sortArgs = PropertiesSort::class.java.invokeStatic(
            "sort",
            TypeSpec(
                Array<Any>::class.java, listOf(
                    Constructor::class.java, Array<String>::class.java, Array<Any>::class.java
                )
            ),
            listOf(
                accessVariable(ctrVar),
                paramNames,
                args
            )
        )

        val sortedVar = VariableRef(Array<Any>::class.java, "sorted")
        insns += variable(sortedVar.type, sortedVar.name, sortArgs)

        insns += returnValue(
            evType,
            cast(
                typeOf<Any>(), evType,
                invokeVirtual(
                    Constructor::class.java,
                    accessVariable(ctrVar),
                    "newInstance",
                    TypeSpec(Object::class.java, listOf(Array<Any>::class.java)),
                    listOf(accessVariable(sortedVar))
                )
            )
        )

        return listOf(
            tryStm()
                .body(Instructions.fromIterable(insns))
                .catchStatements(
                    listOf(
                        catchStm()
                            .variable(varDec().name("throwable").type(typeOf<Throwable>()).build())
                            .exceptionTypes(typeOf<Throwable>())
                            .body(
                                Instructions.fromPart(
                                    throwException(
                                        invokeStatic(
                                            typeOf<RethrowException>(),
                                            Access.STATIC,
                                            "rethrow",
                                            typeSpec(
                                                typeOf<RuntimeException>(),
                                                typeOf<Throwable>()
                                            ),
                                            listOf(accessVariable(typeOf<Throwable>(), "throwable"))
                                        )
                                    )
                                )
                            )
                            .build()
                    )
                )
                .build()
        )
    }

    private fun getNamesAndArgs(params: List<KoresParameter>) =
        params.map { Literals.STRING(it.name) } to params.map {
            it.toVariableAccess()
        }

    private fun getNamesAndArgsArrs(params: List<KoresParameter>) =
        getNamesAndArgs(params).let { (pNames, cArgs) ->
            createArray(
                Array<String>::class.java, listOf(Literals.INT(pNames.size)),
                pNames
            ) to createArray(
                Array<Any>::class.java, listOf(Literals.INT(cArgs.size)),
                cArgs
            )
        }

    private fun List<PropertyInfo>.asPropArgs(): Instruction {
        return Collections3::class.java.invokeStatic("listOf",
            TypeSpec(List::class.java, listOf(Array<Any>::class.java)),
            listOf(createArray(Array<PropertyInfo>::class.java, listOf(Literals.INT(this.size)),
                this.map { it.asArg() }
            )))
    }

    private fun List<ExtensionSpecification>.asExtArgs(): Instruction {
        return Collections3::class.java.invokeStatic(
            "listOf",
            TypeSpec(List::class.java, listOf(Array<Any>::class.java)),
            listOf(
                createArray(Array<ExtensionSpecification>::class.java,
                    listOf(Literals.INT(this.size)),
                    this.map { it.asArg() })
            )
        )
    }

    private fun PropertyInfo.asArg(): Instruction {
        return PropertyInfo::class.java.invokeConstructor(

            constructorTypeSpec(
                Types.CLASS, // declaringClass
                // propertyName, getterName, setterName
                Types.STRING, Types.STRING, Types.STRING,
                // type, itsNotNull, validator
                Types.CLASS, Types.BOOLEAN, Types.CLASS,
                //propertyTypeInfo, inferredType
                PropertyType::class.java, Type::class.java
            ),
            listOf(
                Literals.CLASS(Nothing::class.java),
                Literals.STRING(this.propertyName),
                this.getterName?.let { Literals.STRING(it) } ?: Literals.NULL,
                this.setterName?.let { Literals.STRING(it) } ?: Literals.NULL,
                Literals.CLASS(this.type),
                Literals.BOOLEAN(this.isNotNull),
                this.validator?.let { Literals.CLASS(it) } ?: Literals.NULL,
                this.propertyType.toArg(),
                Literals.CLASS(this.type)
            )
        )
    }

    private fun PropertyType.toArg(): Instruction {
        return PropertyType::class.java.invokeConstructor(
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


fun createGenericType(evType: Type): Instruction = evType.koresType.toArg()
