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
import com.github.jonathanxd.codeapi.MutableCodeSource
import com.github.jonathanxd.codeapi.Types
import com.github.jonathanxd.codeapi.base.Access
import com.github.jonathanxd.codeapi.base.ClassDeclaration
import com.github.jonathanxd.codeapi.base.CodeModifier
import com.github.jonathanxd.codeapi.base.InvokeType
import com.github.jonathanxd.codeapi.bytecode.VISIT_LINES
import com.github.jonathanxd.codeapi.bytecode.VisitLineType
import com.github.jonathanxd.codeapi.bytecode.processor.BytecodeProcessor
import com.github.jonathanxd.codeapi.factory.*
import com.github.jonathanxd.codeapi.literal.Literals
import com.github.jonathanxd.codeapi.util.canonicalName
import com.github.jonathanxd.codeapi.util.codeType
import com.github.jonathanxd.codeapi.util.conversion.parameterNames
import com.github.jonathanxd.codeapi.util.conversion.toMethodDeclaration
import com.github.jonathanxd.codeapi.util.conversion.toVariableAccess
import com.github.jonathanxd.iutils.`object`.Default
import com.github.jonathanxd.iutils.type.TypeInfo
import com.github.jonathanxd.iutils.type.TypeUtil
import com.github.projectsandstone.eventsys.Debug
import com.github.projectsandstone.eventsys.event.Cancellable
import com.github.projectsandstone.eventsys.event.Event
import com.github.projectsandstone.eventsys.event.annotation.*
import com.github.projectsandstone.eventsys.gen.GeneratedEventClass
import com.github.projectsandstone.eventsys.gen.save.ClassSaver
import com.github.projectsandstone.eventsys.reflect.findImplementation
import com.github.projectsandstone.eventsys.reflect.getAllAnnotationsOfType
import java.lang.reflect.Parameter

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
    internal fun <T : Any> create(eventGenerator: EventGenerator, factoryClass: Class<T>): T {

        if (this.cached.containsKey(factoryClass))
            return this.cached[factoryClass]!! as T

        val superClass = factoryClass.superclass

        if (!factoryClass.isInterface)
            throw IllegalArgumentException("Factory class must be an interface.")

        if (superClass != null && factoryClass != Any::class.java || factoryClass.interfaces.isNotEmpty())
            throw IllegalArgumentException("Factory class must not extend any class.")

        val declaration = ClassDeclaration.Builder.builder()
                .modifiers(CodeModifier.PUBLIC)
                .qualifiedName("${factoryClass.canonicalName}\$Impl")
                .implementations(factoryClass.codeType)
                .superClass(Types.OBJECT)
                .methods(factoryClass.declaredMethods
                        .filter { !it.isDefault }
                        .map mMap@{ factoryMethod ->

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

                                if (!Event::class.java.isAssignableFrom(eventType))
                                    throw IllegalArgumentException("Failed to generate implementation of method '$factoryMethod': event factory methods must return a type assignable to 'Event'.")

                                val parameterNames = factoryMethod.parameters.mapIndexed { i, it ->
                                    if (it.isAnnotationPresent(Name::class.java))
                                        it.getDeclaredAnnotation(Name::class.java).value
                                    else
                                        ktNames[i]
                                }

                                val allExtensionAnnotations = mutableListOf<Extension>()

                                allExtensionAnnotations += eventType.getAllAnnotationsOfType(Extension::class.java)
                                allExtensionAnnotations += eventType.getAllAnnotationsOfType(Extensions::class.java)
                                        .flatMap { it.value.toList() }

                                allExtensionAnnotations += factoryMethod.getDeclaredAnnotationsByType(Extension::class.java)
                                allExtensionAnnotations += factoryMethod.getDeclaredAnnotationsByType(Extensions::class.java)
                                        .flatMap { it.value.toList() }

                                val extensions =
                                        allExtensionAnnotations.map {
                                            val implement = it.implement.java.let { if (it == Default::class.java) null else it }
                                            val extension = it.extensionClass.java.let { if (it == Default::class.java) null else it }
                                            ExtensionSpecification(factoryMethod, implement, extension)
                                        }

                                val properties = EventClassGenerator.getProperties(eventType, emptyList(), extensions.map { it.implement }.filterNotNull())
                                val additionalProperties = mutableListOf<PropertyInfo>()

                                factoryMethod.parameters.forEachIndexed { i, parameter ->
                                    val find = properties.any { it.propertyName == parameterNames[i] && it.type == parameter.type }

                                    if (!find) {
                                        val name = parameterNames[i]

                                        val getterName = "get${name.capitalize()}"
                                        val setterName = if (parameter.isAnnotationPresent(Mutable::class.java)) "set${name.capitalize()}" else null


                                        additionalProperties += PropertyInfo(
                                                propertyName = name,
                                                type = parameter.type,
                                                getterName = getterName,
                                                setterName = setterName,
                                                validator = parameter.getDeclaredAnnotation(Validate::class.java)?.value?.java
                                        )
                                    }
                                }

                                val eventTypeInfo = TypeUtil.toTypeInfo(factoryMethod.genericReturnType) as TypeInfo<Event>

                                if (!Event::class.java.isAssignableFrom(eventTypeInfo.typeClass))
                                    throw IllegalStateException("Factory method '$factoryMethod' present in factory class '${factoryClass.canonicalName}' must returns a class that extends 'Event' class (currentClass: ${eventTypeInfo.typeClass.canonicalName}).")

                                val implClass = eventGenerator.createEventClass(eventTypeInfo, additionalProperties, extensions)

                                val methodDeclaration = factoryMethod.toMethodDeclaration { index, parameter ->
                                    parameterNames[index]
                                }

                                val methodBody = methodDeclaration.body as MutableCodeSource

                                val ctr = implClass.declaredConstructors[0]
                                val names by lazy { ctr.parameterNames }

                                val arguments = ctr.parameters.mapIndexed<Parameter, CodeInstruction> map@ { index, it ->
                                    val name = it.getDeclaredAnnotation(Name::class.java)?.value
                                            ?: names[index] // Should we remove it?

                                    if (!methodDeclaration.parameters.any { codeParameter -> codeParameter.name == it.name && codeParameter.type.canonicalName == it.type.canonicalName })
                                        throw IllegalStateException("Cannot find property '[name: $name, type: ${it.type.canonicalName}]' in factory method '$factoryMethod'. Please provide a parameter with this name, use '-parameters' javac option or annotate parameters with '@${Name::class.java.canonicalName}' annotation.",
                                                IllegalStateException("Found properties: ${methodDeclaration.parameters.map { "${it.type.canonicalName} ${it.name}" }}. Required: ${ctr.parameters.contentToString()}."))

                                    if (name == "cancelled"
                                            && it.type == java.lang.Boolean.TYPE
                                            && Cancellable::class.java.isAssignableFrom(eventType))
                                        return@map Literals.FALSE

                                    return@map accessVariable(it.type.codeType, name)
                                }

                                methodBody.add(returnValue(
                                        eventType,
                                        implClass.invokeConstructor(constructorTypeSpec(*ctr.parameterTypes), arguments)
                                ))

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
            this.cached.put(factoryClass, it)
            it.getConstructor().newInstance()
        }
    }

}