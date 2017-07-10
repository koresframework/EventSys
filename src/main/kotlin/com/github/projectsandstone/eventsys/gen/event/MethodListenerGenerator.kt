/*
 *      EventSys - Event implementation generator written on top of CodeAPI
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
import com.github.jonathanxd.codeapi.bytecode.extra.Dup
import com.github.jonathanxd.codeapi.bytecode.extra.Pop
import com.github.jonathanxd.codeapi.bytecode.processor.BytecodeProcessor
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
import com.github.projectsandstone.eventsys.util.toGeneric
import com.github.projectsandstone.eventsys.util.toSimpleString
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * Creates [EventListener] class that invokes a method (that are annotated with [Listener]) directly (without reflection).
 */
internal object MethodListenerGenerator {

    private val cache = mutableMapOf<Method, EventListener<Event>>()

    fun create(owner: Any, method: Method, instance: Any?, listenerSpec: ListenerSpec): EventListener<Event> {

        if (this.cache.containsKey(method)) {
            return this.cache[method]!!
        }

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
        }.let {
            this.cache[method] = it
            it
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

        val generator = BytecodeProcessor()

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

            arguments.add(cast(eventType, parameters[0].type.typeClass.codeType, accessEventVar))

            parameters.forEachIndexed { i, param ->
                if (i > 0) {
                    val name = param.name
                    val typeInfo = param.type

                    val toAdd: CodeInstruction = if (typeInfo.typeClass == Property::class.java && typeInfo.related.isNotEmpty()) {
                        this.callGetPropertyDirectOn(accessEventVar,
                                name, typeInfo.related[0].typeClass,
                                true,
                                param.isNullable,
                                param.isErased)
                    } else {
                        this.callGetPropertyDirectOn(accessEventVar,
                                name,
                                typeInfo.typeClass,
                                false,
                                param.isNullable,
                                param.isErased)
                    }

                    arguments.add(cast(Types.OBJECT, param.type.typeClass, toAdd))
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
                        returnValue(Types.INT, Literals.INT(listenerSpec.phase))
                ))
                .name("getPhase")
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

    private fun callGetPropertyDirectOn(target: CodeInstruction,
                                        name: String,
                                        type: Class<*>,
                                        propertyOnly: Boolean,
                                        isNullable: Boolean,
                                        isErased: Boolean): CodeInstruction {

        val getPropertyMethod = invokeInterface(PropertyHolder::class.java, target,
                if (isErased) "lookup"
                else if (propertyOnly) "getProperty" else "getGetterProperty",
                typeSpec(if (propertyOnly || isErased) Property::class.java else GetterProperty::class.java,
                        Class::class.java,
                        String::class.java),
                listOf(Literals.CLASS(type), Literals.STRING(name))
        )

        val elsePart: CodeInstruction = if (isNullable) Literals.NULL else returnVoid()

        val getPropMethod = checkNull(getPropertyMethod, elsePart)

        if (propertyOnly)
            return getPropMethod

        return ifStatement(checkNotNull(Dup(getPropMethod, GetterProperty::class.codeType)),
                // Body
                source(invokeInterface(GetterProperty::class.java, Stack,
                        "getValue",
                        typeSpec(Any::class.java),
                        emptyList())),
                // Else
                source(
                        Pop,
                        elsePart
                ))

    }

    private fun checkNull(part: Typed, else_: CodeInstruction) = ifStatement(checkNotNull(Dup(part)), source(Stack), source(Pop, else_))
}
