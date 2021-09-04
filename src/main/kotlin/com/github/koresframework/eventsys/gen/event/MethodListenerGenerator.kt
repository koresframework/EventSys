/*
 *      EventSys - Event implementation generator written on top of Kores
 *
 *         The MIT License (MIT)
 *
 *      Copyright (c) 2021 ProjectSandstone <https://github.com/ProjectSandstone/EventSys>
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

import com.koresframework.kores.Instruction
import com.koresframework.kores.Instructions
import com.koresframework.kores.MutableInstructions
import com.koresframework.kores.Types
import com.koresframework.kores.base.*
import com.koresframework.kores.bytecode.VISIT_LINES
import com.koresframework.kores.bytecode.VisitLineType
import com.koresframework.kores.bytecode.processor.BytecodeGenerator
import com.koresframework.kores.factory.*
import com.koresframework.kores.literal.Literals
import com.koresframework.kores.type.*
import com.github.koresframework.eventsys.Debug
import com.github.koresframework.eventsys.error.ListenError
import com.github.koresframework.eventsys.error.PropertyNotFoundError
import com.github.koresframework.eventsys.event.*
import com.github.koresframework.eventsys.event.annotation.Listener
import com.github.koresframework.eventsys.event.property.GetterProperty
import com.github.koresframework.eventsys.event.property.Property
import com.github.koresframework.eventsys.event.property.PropertyHolder
import com.github.koresframework.eventsys.gen.GeneratedEventClass
import com.github.koresframework.eventsys.gen.ResolvableDeclaration
import com.github.koresframework.eventsys.gen.save.ClassSaver
import com.github.koresframework.eventsys.reflect.getName
import com.github.koresframework.eventsys.result.ListenResult
import com.github.koresframework.eventsys.util.*
import java.lang.reflect.Type
import kotlin.coroutines.Continuation

/**
 * Creates [EventListener] class that invokes a method (that are annotated with [Listener]) directly (without reflection).
 */
internal object MethodListenerGenerator {

    private val nameCaching = NameCaching()

    fun create(
            declaration: ResolvableDeclaration<Class<out EventListener<Event>>>,
            method: MethodDeclaration,
            instance: Any?
    ): ResolvableDeclaration<EventListener<Event>> {
        return ResolvableDeclaration(declaration.classDeclaration) {
            val klass = declaration.resolve()

            val isStatic = method.modifiers.contains(KoresModifier.STATIC)

            if (!isStatic) {
                try {
                    klass.classLoader.loadClass(instance!!::class.java.binaryName)
                } catch (e: ClassNotFoundException) {
                    throw IllegalStateException("Cannot lookup for Listener class: '${instance!!::class.java}' from class loader: '${klass.classLoader}'")
                }

                klass.getConstructor(instance::class.java).newInstance(instance)
            } else {
                klass.newInstance()
            }
        }
    }

    fun create(
            owner: Any,
            method: MethodDeclaration,
            instance: Any?,
            listenerSpec: ListenerSpec
    ): ResolvableDeclaration<EventListener<Event>> {

        val declaration = this.createClass(owner::class.java, method, listenerSpec)

        return ResolvableDeclaration(declaration.classDeclaration) {
            val klass = declaration.resolve()

            val isStatic = method.modifiers.contains(KoresModifier.STATIC)

            if (!isStatic) {
                try {
                    klass.classLoader.loadClass(instance!!::class.java.canonicalName)
                } catch (e: ClassNotFoundException) {
                    throw IllegalStateException("Cannot lookup for Plugin class: '${instance!!::class.java}' from class loader: '${klass.classLoader}'")
                }

                klass.getConstructor(instance::class.java).newInstance(instance)
            } else {
                klass.newInstance()
            }
        }

    }

    @Suppress("UNCHECKED_CAST")
    fun createClass(
            targetType: Type,
            method: MethodDeclaration,
            listenerSpec: ListenerSpec
    ): ResolvableDeclaration<Class<out EventListener<Event>>> {
        val codeClass = createClassDeclaration(targetType, method, listenerSpec)

        return ResolvableDeclaration(codeClass) {
            val generator = BytecodeGenerator()

            generator.options.set(VISIT_LINES, VisitLineType.GEN_LINE_INSTRUCTION)

            val bytecodeClass = generator.process(codeClass)[0]

            val bytes = bytecodeClass.bytecode

            val klass = targetType.concreteType.bindedDefaultResolver.resolve().rightOrNull()

            val definedClass = if (klass is Class<*>) {
                EventGenClassLoader.defineClass(
                        codeClass,
                        bytes,
                        lazy { bytecodeClass.disassembledCode },
                        klass.classLoader
                ) as GeneratedEventClass<EventListener<Event>>
            } else {
                EventGenClassLoader.defineClass(
                        codeClass,
                        bytes,
                        lazy { bytecodeClass.disassembledCode }
                ) as GeneratedEventClass<EventListener<Event>>
            }

            if (Debug.isSaveEnabled()) {
                ClassSaver.save(Debug.LISTENER_GEN_DEBUG, definedClass)
            }

            definedClass.javaClass
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun createClassDeclaration(
            targetType: Type,
            method: MethodDeclaration,
            listenerSpec: ListenerSpec
    ): ClassDeclaration {
        val baseCanonicalName = "${EventListener::class.java.`package`.name}.generated."
        val declaringName = targetType.canonicalName.replace('.', '_')

        val name = getName("${baseCanonicalName}_${declaringName}_${method.name}", nameCaching)

        val eventType = listenerSpec.eventType

        val (field, constructor, methods) = genBody(method, targetType, listenerSpec)

        return ClassDeclaration.Builder.builder()
                .modifiers(KoresModifier.PUBLIC)
                .qualifiedName(name)
                .implementations(Generic.type(DynamicEventListener::class.java.koresType).of(eventType.toGeneric))
                .superClass(Types.OBJECT)
                .fields(field?.let(::listOf) ?: emptyList())
                .constructors(constructor?.let(::listOf) ?: emptyList())
                .methods(methods)
                .build()
    }

    private const val eventVariableName: String = "event"
    private const val ownerVariableName: String = "owner"
    private const val continuationVariableName: String = "\$continuation"
    private const val instanceFieldName: String = "\$instance"

    private fun genBody(method: MethodDeclaration, targetType: Type, listenerSpec: ListenerSpec):
            Triple<FieldDeclaration?, ConstructorDeclaration?, List<MethodDeclaration>> {

        val isStatic = method.modifiers.contains(KoresModifier.STATIC)

        val (field, constructor) = if (!isStatic) {

            fieldDec()
                    .modifiers(KoresModifier.PRIVATE, KoresModifier.FINAL)
                    .type(targetType)
                    .name(instanceFieldName)
                    .build() to constructorDec()
                    .modifiers(KoresModifier.PUBLIC)
                    .parameters(parameter(type = targetType, name = instanceFieldName))
                    .body(
                            source(
                                    setFieldValue(
                                            localization = Alias.THIS,
                                            target = Access.THIS,
                                            type = targetType,
                                            name = instanceFieldName,
                                            value = accessVariable(targetType, instanceFieldName)
                                    )
                            )
                    )
                    .build()
        } else null to null

        return Triple(field, constructor, genMethods(method, targetType, listenerSpec))
    }

    private fun genMethods(
            method: MethodDeclaration,
            targetType: Type,
            listenerSpec: ListenerSpec
    ): List<MethodDeclaration> {
        val methods = mutableListOf<MethodDeclaration>()

        val isStatic = method.modifiers.contains(KoresModifier.STATIC)
        val eventType = Event::class.java.koresType

        // This is hard to maintain, but, is funny :D
        fun genOnEventBody(): Instructions {
            val body = MutableInstructions.create()

            val parameters = listenerSpec.parameters

            val arguments = mutableListOf<Instruction>()

            val accessEventVar = accessVariable(eventType, eventVariableName)

            if (listenerSpec.firstIsEvent)
                arguments.add(cast(eventType, listenerSpec.eventType, accessEventVar))

            parameters.forEachIndexed { i, param ->
                if (!listenerSpec.firstIsEvent || i > 0) {
                    if (i == parameters.lastIndex && param.type.isContinuation) {
                        val accessContVar = accessVariable(typeOf<Continuation<*>>(), continuationVariableName)
                        arguments.add(cast(param.type, typeOf<Continuation<*>>(), accessContVar))
                    } else {
                        val name = param.name
                        val typeInfo = param.type

                        if (typeInfo is GenericType
                            && typeInfo.resolvedType.`is`(Property::class.java)
                            && typeInfo.bounds.isNotEmpty()
                        ) {
                            body.addAll(
                                this.callGetPropertyDirectOn(
                                    accessEventVar,
                                    name,
                                    typeInfo.bounds[0].type,
                                    true,
                                    param.isOptional,
                                    param.optType,
                                    param.shouldLookup
                                )
                            )
                        } else {
                            body.addAll(
                                this.callGetPropertyDirectOn(
                                    accessEventVar,
                                    name,
                                    typeInfo,
                                    false,
                                    param.isOptional,
                                    param.optType,
                                    param.shouldLookup
                                )
                            )
                        }

                        // toAdd/*cast(Types.OBJECT, param.type.typeClass, toAdd)*/
                        arguments.add(accessVariable(param.type, "prop$$name"))
                    }
                }
            }

            val isSuspend = method.typeSpec.parameterTypes.lastOrNull()?.isContinuation == true
            val isVoid = method.typeSpec.returnType.`is`(Types.VOID)
            val isListenResult = method.typeSpec.returnType.`is`(typeOf<ListenResult>())
                    || method.typeSpec.returnType.`is`(typeOf<ListenResult.Value>())
                    || method.typeSpec.returnType.`is`(typeOf<ListenResult.Failed>())
                    || isSuspend

            val invoke = invoke(
                    invokeType = if (isStatic) InvokeType.INVOKE_STATIC else InvokeType.get(
                            targetType
                    ),
                    localization = targetType,
                    target = if (isStatic) Access.STATIC else accessThisField(
                            targetType,
                            instanceFieldName
                    ),
                    name = method.name,
                    spec = method.typeSpec,
                    arguments = arguments
            )

            val returnValue = if (!isListenResult) {
                val valueArgument: Instruction =
                        if (isVoid) accessStaticField(typeOf<Unit>(), typeOf<Unit>(), "INSTANCE")
                        else invoke

                typeOf<ListenResult.Value>().invokeConstructor(
                        constructorTypeSpec(Types.OBJECT),
                        listOf(valueArgument)
                )
            } else {
                invoke
            }

            val returnExpr = returnValue(typeOf<Any>(),
                    returnValue
            )

            if (isVoid) {
                body += invoke
                body += returnExpr
            } else {
                body += returnExpr
            }

            return body
        }

        val onEvent = MethodDeclaration.Builder.builder()
                .annotations(overrideAnnotation())
                .modifiers(KoresModifier.PUBLIC)
                .body(genOnEventBody())
                // Careful here, `suspend` listeners MUST return ListenResult directly because their
                // return is propagated directly to avoid handling Coroutine Machine Code
                .returnType(Any::class.java) // Implicitly ListenResult
                .name("on")
                .parameters(
                        parameter(type = eventType, name = eventVariableName),
                        parameter(type = Any::class.java, name = ownerVariableName),
                        parameter(type = Continuation::class.java, name = continuationVariableName)
                )
                .build()

        methods += onEvent

        val getPriorityMethod = MethodDeclaration.Builder.builder()
                .annotations(overrideAnnotation())
                .modifiers(KoresModifier.PUBLIC)
                .body(
                        source(
                                returnValue(
                                        EventPriority::class.java,
                                        accessStaticField(
                                                EventPriority::class.java,
                                                EventPriority::class.java,
                                                listenerSpec.priority.name
                                        )
                                )
                        )
                )
                .name("getPriority")
                .returnType(EventPriority::class.java.koresType)
                .build()

        methods += getPriorityMethod

        val getPhaseMethod = MethodDeclaration.Builder.builder()
                .modifiers(KoresModifier.PUBLIC)
                .annotations(overrideAnnotation())
                .body(
                        source(
                                returnValue(Types.STRING, Literals.STRING(listenerSpec.channel))
                        )
                )
                .name("getChannel")
                .returnType(Types.STRING)
                .build()

        methods += getPhaseMethod

        val getCancelAffected = MethodDeclaration.Builder.builder()
                .modifiers(KoresModifier.PUBLIC)
                .annotations(overrideAnnotation())
                .body(
                        source(
                                returnValue(Types.BOOLEAN, Literals.BOOLEAN(listenerSpec.cancelAffected))
                        )
                )
                .name("getCancelAffected")
                .returnType(Types.BOOLEAN)
                .build()

        methods += getCancelAffected

        val ignoreCancelledMethod = MethodDeclaration.Builder.builder()
                .annotations(overrideAnnotation())
                .modifiers(KoresModifier.PUBLIC)
                .body(
                        source(
                                returnValue(Types.BOOLEAN, Literals.BOOLEAN(listenerSpec.ignoreCancelled))
                        )
                )
                .name("getIgnoreCancelled")
                .returnType(Types.BOOLEAN)
                .build()

        methods += ignoreCancelledMethod

        val toStringMethod = MethodDeclaration.Builder.builder()
                .annotations(overrideAnnotation())
                .modifiers(KoresModifier.PUBLIC)
                .body(
                        source(
                                returnValue(
                                        Types.STRING,
                                        Literals.STRING("GeneratedMethodListener[\"${method.toSimpleString()}\"]")
                                )
                        )
                )
                .name("toString")
                .returnType(Types.STRING)
                .build()

        methods += toStringMethod

        return methods
    }

    private fun getPropertyAccessName(name: String) = "prop\$$name"

    private fun callGetPropertyDirectOn(
            target: Instruction,
            name: String,
            type: Type,
            propertyOnly: Boolean,
            isOptional: Boolean,
            optType: Type?,
            shouldLookup: Boolean
    ): Instructions {

        val source = MutableInstructions.create()

        val getPropertyMethod = invokeInterface(
                PropertyHolder::class.java, target,
                when {
                    shouldLookup -> "lookup"
                    propertyOnly -> "getProperty"
                    else -> "getGetterProperty"
                },
                typeSpec(
                        if (propertyOnly || shouldLookup) Property::class.java else GetterProperty::class.java,
                        Class::class.java,
                        String::class.java
                ),
                listOf(Literals.CLASS(type), Literals.STRING(name))
        )

        val getPropertyVariable = variable(
                Property::class.java,
                "access\$$name",
                getPropertyMethod
        )

        val propertyValue =
                variable(if (propertyOnly) Property::class.java else type, getPropertyAccessName(name))

        source += getPropertyVariable
        source += propertyValue

        val elsePart: Instruction =
                if (isOptional) {
                    setVariableValue(propertyValue, optType?.createNone() ?: Literals.NULL)
                } else {
                    returnValue(typeOf<ListenResult>(),
                            typeOf<ListenResult.Failed>().invokeConstructor(
                                    constructorTypeSpec(typeOf<ListenError>()),
                                    listOf(typeOf<PropertyNotFoundError>().invokeConstructor(
                                            constructorTypeSpec(Types.STRING, typeOf<Type>()),
                                            listOf(Literals.STRING(name), Literals.TYPE(type))
                                    ))
                            )
                    )
                }

        // if (null) prop$name = null/Opt.none() or if (null) return

        val getPropMethod = accessVariable(getPropertyVariable)

        if (propertyOnly) {
            source += ifStatement(checkNull(accessVariable(getPropertyVariable)), source(elsePart))
            source += setVariableValue(propertyValue, getPropMethod)
            return source
        }

        val getterType = type.getGetterType()
        val reifType = type.getReifiedType()

        val ret = cast(
                reifType, type, invokeInterface(
                getterType, cast(GetterProperty::class.java, getterType, getPropMethod),
                type.getInvokeName(),
                typeSpec(reifType),
                emptyList()
        )
        )

        source += ifStatement(
                checkNotNull(getPropMethod/*Dup(getPropMethod, GetterProperty::class.koresType)*/),
                // Body
                source(setVariableValue(propertyValue, optType?.createSome(ret) ?: ret)),
                // Else
                source(
                        //Pop,
                        elsePart
                )
        )
        return source
    }

    private fun checkNull(part: Typed, else_: Instruction) =
            ifStatement(checkNull(part as Instruction), source(else_))
}
