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
package com.github.koresframework.eventsys.gen.check

import com.github.jonathanxd.iutils.description.Description
import com.github.jonathanxd.iutils.description.DescriptionUtil
import com.github.jonathanxd.iutils.type.TypeInfo
import com.github.jonathanxd.kores.base.*
import com.github.jonathanxd.kores.type.*
import com.github.koresframework.eventsys.context.EnvironmentContext
import com.github.koresframework.eventsys.event.Event
import com.github.koresframework.eventsys.event.annotation.Check
import com.github.koresframework.eventsys.event.annotation.SuppressCheck
import com.github.koresframework.eventsys.extension.ExtensionSpecification
import com.github.koresframework.eventsys.gen.event.EventGenerator
import com.github.koresframework.eventsys.gen.event.EventGeneratorOptions
import com.github.koresframework.eventsys.logging.MessageType
import com.github.koresframework.eventsys.util.DeclaredMethod
import com.github.koresframework.eventsys.util.fail
import com.github.koresframework.eventsys.util.toSimpleString
import java.util.*
import java.util.concurrent.ConcurrentSkipListSet

class DefaultCheckHandler : SuppressCapableCheckHandler {

    private val suppress = ConcurrentSkipListSet<String>()

    override fun addSuppression(elementDescription: Description) {
        suppress.add(elementDescription.plainDescription)
    }

    override fun checkImplementation(
            implementedMethods: List<MethodDeclaration>,
            type: TypeDeclaration,
            extensions: List<ExtensionSpecification>,
            eventGenerator: EventGenerator,
            ctx: EnvironmentContext
    ) {

        val logger = eventGenerator.logger
        val unimplMethods = mutableListOf<String>()

        type.methods.forEach { method ->
            val name = method.name

            val hasImpl = implementedMethods.any {
                it.name == name
                        && it.returnType.canonicalName == method.returnType.canonicalName
                        && it.parameters.map { it.type.canonicalName } == method.parameters.map { it.type.canonicalName }
            }

            if (!hasImpl && method.body.isEmpty && !method.modifiers.contains(KoresModifier.DEFAULT)) {

                if (eventGenerator.options[EventGeneratorOptions.ENABLE_SUPPRESSION]
                        && ((method.annotations.any { it.type.`is`(typeOf<SuppressCheck>()) }
                                && method.containsCheckSuppress("IMPLEMENTATION"))
                                || this.shouldSuppressImplementationCheck(
                                method,
                                type,
                                extensions,
                                eventGenerator
                        ))
                ) {
                    // Suppress
                } else {
                    unimplMethods += method.toSimpleString()
                }
            }
        }

        if (unimplMethods.isNotEmpty()) {
            val classes =
                    extensions.filter { it.extensionClass != null }.map { it.extensionClass!! }

            val messages = mutableListOf<String>()

            messages += ""
            messages += "Following methods was not implemented for event ${type.simpleName}:"
            messages += ""

            unimplMethods.forEach {
                messages += "  $it"
            }

            messages += ""
            if (classes.isEmpty()) messages += "Provide an extension which implement them."
            else {
                messages += "Provide an extension which implement them or add implementation in one of existing extensions:"
                messages += classes.joinToString { it.simpleName }
            }


            logger.log(messages, MessageType.IMPLEMENTATION_NOT_FOUND, ctx)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun Annotable.containsCheckSuppress(name: String) =
            this.annotations.any {
                it.type.`is`(typeOf<SuppressCheck>()) && it.containsCheckSuppress(
                        name
                )
            }

    @Suppress("UNCHECKED_CAST")
    private fun KoresAnnotation.containsCheckSuppress(name: String) =
            this.values["value"].let {
                it != null && (it as? List<EnumValue>)?.any { it.type.`is`(typeOf<Check>()) && it.name == name } == true
            }

    override fun shouldSuppressImplementationCheck(
            method: MethodDeclaration,
            type: TypeDeclaration,
            extensions: List<ExtensionSpecification>,
            eventGenerator: EventGenerator
    ): Boolean {
        return suppress.contains(desc(type, method).plainDescription)
    }

    fun desc(type: TypeDeclaration, method: MethodDeclaration): Description {
        Objects.requireNonNull(method, "Method cannot be null")

        val desc = (type.javaSpecName.fixed()
                + ":"
                + method.name
                + method.parameters.joinToString(
                separator = "",
                prefix = "(",
                postfix = ")"
        ) { it.type.javaSpecName.fixed() }
                + method.returnType.javaSpecName.fixed())

        return DescriptionUtil.parseDescription(desc)
    }

    private fun String.fixed() = this.replace('/', '.')

    override fun checkDuplicatedMethods(methods: List<MethodDeclaration>,
                                        ctx: EnvironmentContext) {
        val methodList = mutableListOf<MethodDeclaration>()

        methods.forEach { outer ->
            if (methodList.any {
                        outer.name == it.name
                                && outer.returnType.concreteType.`is`(it.returnType.concreteType)
                                && outer.parameters.map { it.type.concreteType } == it.parameters.map { it.type.concreteType }
                    })
            // I will not use logging here, class loader will fail if duplicated methods are found
                throw IllegalStateException("Duplicated method: ${outer.name}")
            else
                methodList += outer
        }
    }


    override fun validateExtension(
            extension: ExtensionSpecification,
            extensionClass: TypeDeclaration,
            type: ClassDeclaration,
            eventGenerator: EventGenerator,
            ctx: EnvironmentContext
    ): ConstructorDeclaration {
        val logger = eventGenerator.logger
        val resolver = type.defaultResolver
        val foundsCtr = mutableListOf<ConstructorDeclaration>()

        (extensionClass as? ConstructorsHolder)?.constructors?.forEach {
            if (it.parameters.size == 1) {
                if (resolver.isAssignableFrom(
                                it.parameters.single().type,
                                type
                        ).run { isRight && right }
                )
                    return it
                else
                    foundsCtr += it
            }
        }

        logger.log("Provided extension class '${extensionClass.canonicalName}' (spec: '$extension') does not have a single-arg constructor with an argument which receives a '${type.canonicalName}' (or a super type). Found single-arg constructors: ${foundsCtr.joinToString {
            it.parameters.joinToString(
                    prefix = "(",
                    postfix = ")"
            ) { it.type.simpleName }
        }}.", MessageType.INVALID_EXTENSION, ctx)
        fail()
    }


    // Factory

    override fun validateFactoryClass(type: TypeDeclaration,
                                      eventGenerator: EventGenerator,
                                      ctx: EnvironmentContext) {
        val logger = eventGenerator.logger
        val superClass = (type as? SuperClassHolder)?.superClass

        if (!type.isInterface) {
            logger.log("Factory class must be an interface.", MessageType.INVALID_FACTORY, ctx)
            fail()
        }

        if (superClass != null && type != Any::class.java || type.interfaces.isNotEmpty()) {
            logger.log("Factory class must not extend any class.", MessageType.INVALID_FACTORY, ctx)
            fail()
        }
    }

    override fun validateEventClass(
            type: TypeDeclaration,
            factoryMethod: MethodDeclaration,
            eventGenerator: EventGenerator,
            ctx: EnvironmentContext
    ) {
        val logger = eventGenerator.logger

        if (!Event::class.java.isAssignableFrom(type)) {
            logger.log(
                    "Failed to generate implementation of method '$factoryMethod': event factory methods must return a type assignable to 'Event'.",
                    MessageType.INVALID_FACTORY_METHOD,
                    ctx
            )
            fail()
        }
    }

    override fun validateTypeProvider(
            providerParams: List<KoresParameter>,
            factoryMethod: DeclaredMethod,
            eventGenerator: EventGenerator,
            ctx: EnvironmentContext
    ) {
        val logger = eventGenerator.logger
        val factoryClass = factoryMethod.type

        if (providerParams.isEmpty()) {
            logger.log(
                    "Factory method '$factoryMethod' present in factory class '${factoryClass.canonicalName}' must have a parameter of type 'TypeInfo' annotated with @TypeParam.",
                    MessageType.INVALID_FACTORY_METHOD,
                    ctx
            )
            fail()
        }

        if (providerParams.size != 1) {
            logger.log(
                    "Factory method '$factoryMethod' present in factory class '${factoryClass.canonicalName}' must have only one parameter of type 'TypeInfo' annotated with @TypeParam.",
                    MessageType.INVALID_FACTORY_METHOD,
                    ctx
            )
            fail()
        }

        if (providerParams.single().type != TypeInfo::class.java) {
            logger.log(
                    "@TypeParam should be only annotated in parameter of 'TypeInfo' type. Factory method: '$factoryMethod'. Factory class '${factoryClass.canonicalName}'.",
                    MessageType.INVALID_FACTORY_METHOD,
                    ctx
            )
            fail()
        }
    }
}