/*
 *      EventSys - Event implementation generator written on top of CodeAPI
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

import com.github.jonathanxd.codeapi.CodeInstruction
import com.github.jonathanxd.codeapi.CodeSource
import com.github.jonathanxd.codeapi.MutableCodeSource
import com.github.jonathanxd.codeapi.Types
import com.github.jonathanxd.codeapi.base.*
import com.github.jonathanxd.codeapi.bytecode.VISIT_LINES
import com.github.jonathanxd.codeapi.bytecode.VisitLineType
import com.github.jonathanxd.codeapi.bytecode.extra.Dup
import com.github.jonathanxd.codeapi.bytecode.extra.Pop
import com.github.jonathanxd.codeapi.bytecode.processor.BytecodeGenerator
import com.github.jonathanxd.codeapi.common.Stack
import com.github.jonathanxd.codeapi.factory.*
import com.github.jonathanxd.codeapi.literal.Literals
import com.github.jonathanxd.codeapi.type.Generic
import com.github.jonathanxd.codeapi.util.Alias
import com.github.jonathanxd.codeapi.util.codeType
import com.github.jonathanxd.codeapi.util.conversion.toInvocation
import com.github.projectsandstone.eventsys.Debug
import com.github.projectsandstone.eventsys.event.Event
import com.github.projectsandstone.eventsys.event.EventListener
import com.github.projectsandstone.eventsys.event.EventPriority
import com.github.projectsandstone.eventsys.event.ListenerSpec
import com.github.projectsandstone.eventsys.event.annotation.Listener
import com.github.projectsandstone.eventsys.event.property.GetterProperty
import com.github.projectsandstone.eventsys.event.property.Property
import com.github.projectsandstone.eventsys.event.property.PropertyHolder
import com.github.projectsandstone.eventsys.gen.GeneratedEventClass
import com.github.projectsandstone.eventsys.gen.save.ClassSaver
import com.github.projectsandstone.eventsys.reflect.getName
import com.github.projectsandstone.eventsys.util.*
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.Type

/**
 * Creates [EventListener] class that invokes a method (that are annotated with [Listener]) directly (without reflection).
 */
internal object MethodListenerGenerator {

    fun create(owner: Any, method: Method, instance: Any?, listenerSpec: ListenerSpec): EventListener<Event> {

        val klass = this.createClass(owner, instance, method, listenerSpec)

        val isStatic = Modifier.isStatic(method.modifiers)

        return if (!isStatic) {
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

    @Suppress("UNCHECKED_CAST")
    private fun createClass(owner: Any, instance: Any?, method: Method, listenerSpec: ListenerSpec): Class<EventListener<Event>> {
        val baseCanonicalName = "${EventListener::class.java.`package`.name}.generated."
        val declaringName = method.declaringClass.canonicalName.replace('.', '_')

        val name = getName("${baseCanonicalName}_${declaringName}_${method.name}")

        val eventType = listenerSpec.eventType

        val (field, constructor, methods) = genBody(method, instance, listenerSpec)

        val codeClass = ClassDeclaration.Builder.builder()
                .modifiers(CodeModifier.PUBLIC)
                .qualifiedName(name)
                .implementations(Generic.type(EventListener::class.java.codeType).of(eventType.toGeneric()))
                .superClass(Types.OBJECT)
                .fields(field?.let(::listOf) ?: emptyList())
                .constructors(constructor?.let(::listOf) ?: emptyList())
                .methods(methods)
                .build()

        val generator = BytecodeGenerator()

        generator.options.set(VISIT_LINES, VisitLineType.FOLLOW_CODE_SOURCE)

        val bytecodeClass = generator.process(codeClass)[0]

        val bytes = bytecodeClass.bytecode

        val definedClass = EventGenClassLoader.defineClass(codeClass, bytes, lazy { bytecodeClass.disassembledCode }, (owner::class.java.classLoader as ClassLoader)) as GeneratedEventClass<EventListener<Event>>

        if (Debug.LISTENER_GEN_DEBUG) {
            ClassSaver.save("listenergen", definedClass)
        }

        return definedClass.javaClass
    }

    private const val eventVariableName: String = "event"
    private const val ownerVariableName: String = "pluginContainer"
    private const val instanceFieldName: String = "\$instance"

    private fun genBody(method: Method, instance: Any?, listenerSpec: ListenerSpec):
            Triple<FieldDeclaration?, ConstructorDeclaration?, List<MethodDeclaration>> {

        val isStatic = Modifier.isStatic(method.modifiers)

        val (field, constructor) = if (!isStatic) {
            val instanceType = instance!!::class.java.codeType

            fieldDec()
                    .modifiers(CodeModifier.PRIVATE, CodeModifier.FINAL)
                    .type(instanceType)
                    .name(instanceFieldName)
                    .build() to constructorDec()
                    .modifiers(CodeModifier.PUBLIC)
                    .parameters(parameter(type = instanceType, name = instanceFieldName))
                    .body(source(
                            setFieldValue(localization = Alias.THIS,
                                    target = Access.THIS,
                                    type = instanceType,
                                    name = instanceFieldName,
                                    value = accessVariable(instanceType, instanceFieldName))
                    ))
                    .build()
        } else null to null

        return Triple(field, constructor, genMethods(method, instance, listenerSpec))
    }

    private fun genMethods(method: Method, instance: Any?, listenerSpec: ListenerSpec): List<MethodDeclaration> {
        val methods = mutableListOf<MethodDeclaration>()

        val isStatic = Modifier.isStatic(method.modifiers)
        val eventType = Event::class.java.codeType

        // This is hard to maintain, but, is funny :D
        fun genOnEventBody(): CodeSource {
            val body = MutableCodeSource.create()

            val parameters = listenerSpec.parameters

            val arguments = mutableListOf<CodeInstruction>()

            val accessEventVar = accessVariable(eventType, eventVariableName)

            if (listenerSpec.firstIsEvent)
                arguments.add(cast(eventType, listenerSpec.eventType.typeClass.codeType, accessEventVar))

            parameters.forEachIndexed { i, param ->
                if (!listenerSpec.firstIsEvent || i > 0) {
                    val name = param.name
                    val typeInfo = param.type

                    if (typeInfo.typeClass == Property::class.java && typeInfo.typeParameters.isNotEmpty()) {
                        body.addAll(
                        this.callGetPropertyDirectOn(accessEventVar,
                                name,
                                typeInfo.typeParameters[0].typeClass,
                                true,
                                param.isOptional,
                                param.optType,
                                param.shouldLookup))
                    } else {
                        body.addAll(this.callGetPropertyDirectOn(accessEventVar,
                                name,
                                typeInfo.typeClass,
                                false,
                                param.isOptional,
                                param.optType,
                                param.shouldLookup))
                    }

                    // toAdd/*cast(Types.OBJECT, param.type.typeClass, toAdd)*/
                    arguments.add(accessVariable(param.type.typeClass, "prop$$name"))
                }
            }


            if (isStatic) {
                body.add(method.toInvocation(InvokeType.INVOKE_STATIC, Access.STATIC, arguments))
            } else {
                body.add(method.toInvocation(
                        invokeType = null,
                        target = accessThisField(instance!!::class.java.codeType, instanceFieldName),
                        arguments = arguments))
            }

            return body
        }

        val onEvent = MethodDeclaration.Builder.builder()
                .annotations(overrideAnnotation())
                .modifiers(CodeModifier.PUBLIC)
                .body(genOnEventBody())
                .returnType(Types.VOID)
                .name("onEvent")
                .parameters(
                        parameter(type = eventType, name = eventVariableName), parameter(type = Any::class.java, name = ownerVariableName)
                )
                .build()

        methods += onEvent

        val getPriorityMethod = MethodDeclaration.Builder.builder()
                .annotations(overrideAnnotation())
                .modifiers(CodeModifier.PUBLIC)
                .body(source(
                        returnValue(EventPriority::class.java,
                                accessStaticField(EventPriority::class.java, EventPriority::class.java, listenerSpec.priority.name)
                        )
                ))
                .name("getPriority")
                .returnType(EventPriority::class.java.codeType)
                .build()

        methods += getPriorityMethod

        val getPhaseMethod = MethodDeclaration.Builder.builder()
                .modifiers(CodeModifier.PUBLIC)
                .annotations(overrideAnnotation())
                .body(source(
                        returnValue(Types.INT, Literals.INT(listenerSpec.channel))
                ))
                .name("getChannel")
                .returnType(Types.INT)
                .build()

        methods += getPhaseMethod

        val ignoreCancelledMethod = MethodDeclaration.Builder.builder()
                .annotations(overrideAnnotation())
                .modifiers(CodeModifier.PUBLIC)
                .body(source(
                        returnValue(Types.BOOLEAN, Literals.BOOLEAN(listenerSpec.ignoreCancelled))
                ))
                .name("getIgnoreCancelled")
                .returnType(Types.BOOLEAN)
                .build()

        methods += ignoreCancelledMethod

        val toStringMethod = MethodDeclaration.Builder.builder()
                .annotations(overrideAnnotation())
                .modifiers(CodeModifier.PUBLIC)
                .body(source(
                        returnValue(Types.STRING,
                                Literals.STRING("GeneratedMethodListener[\"${method.toSimpleString()}\"]")
                        )
                ))
                .name("toString")
                .returnType(Types.STRING)
                .build()

        methods += toStringMethod

        return methods
    }

    private fun getPropertyAccessName(name: String) = "prop\$$name"

    private fun callGetPropertyDirectOn(target: CodeInstruction,
                                        name: String,
                                        type: Class<*>,
                                        propertyOnly: Boolean,
                                        isOptional: Boolean,
                                        optType: Type?,
                                        shouldLookup: Boolean): CodeSource {

        val source = MutableCodeSource.create()

        val getPropertyMethod = invokeInterface(PropertyHolder::class.java, target,
                when {
                    shouldLookup -> "lookup"
                    propertyOnly -> "getProperty"
                    else -> "getGetterProperty"
                },
                typeSpec(if (propertyOnly || shouldLookup) Property::class.java else GetterProperty::class.java,
                        Class::class.java,
                        String::class.java),
                listOf(Literals.CLASS(type), Literals.STRING(name))
        )

        val getPropertyVariable = variable(Property::class.java,
                "access\$$name",
                getPropertyMethod)

        val propertyValue = variable(if (propertyOnly) Property::class.java else type, getPropertyAccessName(name))

        source += getPropertyVariable
        source += propertyValue

        val elsePart: CodeInstruction =
                if (isOptional) {
                    setVariableValue(propertyValue, optType?.createNone() ?: Literals.NULL)
                } else {
                    returnVoid()
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

        val ret = cast(reifType, type, invokeInterface(getterType, cast(GetterProperty::class.java, getterType, getPropMethod),
                type.getInvokeName(),
                typeSpec(reifType),
                emptyList()))

        source += ifStatement(checkNotNull(getPropMethod/*Dup(getPropMethod, GetterProperty::class.codeType)*/),
                // Body
                source(setVariableValue(propertyValue, optType?.createSome(ret) ?: ret)),
                // Else
                source(
                        //Pop,
                        elsePart
                ))
        return source
    }

    private fun checkNull(part: Typed, else_: CodeInstruction) =
            ifStatement(checkNull(part as CodeInstruction), source(else_))
}
