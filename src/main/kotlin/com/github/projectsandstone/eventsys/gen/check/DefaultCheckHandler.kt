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
package com.github.projectsandstone.eventsys.gen.check

import com.github.jonathanxd.codeapi.base.MethodDeclaration
import com.github.jonathanxd.codeapi.util.canonicalName
import com.github.jonathanxd.codeapi.util.concreteType
import com.github.jonathanxd.codeapi.util.defaultResolver
import com.github.jonathanxd.iutils.description.Description
import com.github.jonathanxd.iutils.description.DescriptionUtil
import com.github.jonathanxd.iutils.type.TypeInfo
import com.github.projectsandstone.eventsys.event.Event
import com.github.projectsandstone.eventsys.event.annotation.Check
import com.github.projectsandstone.eventsys.event.annotation.SuppressCheck
import com.github.projectsandstone.eventsys.gen.event.EventGenerator
import com.github.projectsandstone.eventsys.gen.event.EventGeneratorOptions
import com.github.projectsandstone.eventsys.gen.event.ExtensionSpecification
import com.github.projectsandstone.eventsys.logging.MessageType
import com.github.projectsandstone.eventsys.util.fail
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.lang.reflect.Parameter
import java.lang.reflect.Type

class DefaultCheckHandler : SuppressCapableCheckHandler {

    private val suppress = mutableListOf<String>()

    override fun addSuppression(elementDescription: Description) {
        suppress.add(elementDescription.plainDescription)
    }

    override fun checkImplementation(implementedMethods: List<MethodDeclaration>,
                                     type: Class<*>,
                                     extensions: List<ExtensionSpecification>,
                                     eventGenerator: EventGenerator) {

        val logger = eventGenerator.logger
        val unimplMethods = mutableListOf<String>()

        type.methods.forEach { method ->
            val name = method.name

            val hasImpl = implementedMethods.any {
                it.name == name
                        && it.returnType.canonicalName == method.returnType.canonicalName
                        && it.parameters.map { it.type.canonicalName } == method.parameterTypes.map { it.canonicalName }
            }

            if (!hasImpl && !method.isDefault) {

                if (eventGenerator.options[EventGeneratorOptions.ENABLE_SUPPRESSION]
                        && ((method.isAnnotationPresent(SuppressCheck::class.java)
                        && method.getDeclaredAnnotation(SuppressCheck::class.java)
                        .value.contains(Check.IMPLEMENTATION))
                        || this.shouldSuppressImplementationCheck(method, type, extensions, eventGenerator))) {
                    // Suppress
                } else {
                    unimplMethods += "$method"
                }
            }
        }

        if (unimplMethods.isNotEmpty()) {
            val classes = extensions.filter { it.extensionClass != null }.map { it.extensionClass!! }

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
                messages += "Provide an extension which implement them or add implementation one of existing extensions:"
                messages += classes.joinToString { it.simpleName }
            }


            logger.log(messages, MessageType.IMPLEMENTATION_NOT_FOUND)
        }
    }

    override fun shouldSuppressImplementationCheck(method: Method,
                                                   type: Class<*>,
                                                   extensions: List<ExtensionSpecification>,
                                                   eventGenerator: EventGenerator): Boolean {
        return suppress.contains(DescriptionUtil.from(method).plainDescription)
    }

    override fun checkDuplicatedMethods(methods: List<MethodDeclaration>) {
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


    override fun validateExtension(extension: ExtensionSpecification,
                                   extensionClass: Class<*>,
                                   type: Type,
                                   eventGenerator: EventGenerator): Constructor<*> {
        val logger = eventGenerator.logger
        val resolver = type.defaultResolver
        val foundsCtr = mutableListOf<Constructor<*>>()

        extensionClass.declaredConstructors.forEach {
            if (it.parameterCount == 1) {
                if (resolver.isAssignableFrom(it.parameterTypes.single(), type).run { isRight && right })
                    return it
                else
                    foundsCtr += it
            }
        }

        logger.log("Provided extension class '${extensionClass.canonicalName}' (spec: '$extension') does not have a single-arg constructor with an argument which receives a '${type.canonicalName}' (or a super type). Found single-arg constructors: ${foundsCtr.joinToString { it.parameterTypes.joinToString(prefix = "(", postfix = ")") { it.simpleName } }}.", MessageType.INVALID_EXTENSION)
        fail()
    }


    // Factory

    override fun validateFactoryClass(type: Class<*>, eventGenerator: EventGenerator) {
        val logger = eventGenerator.logger
        val superClass = type.superclass

        if (!type.isInterface) {
            logger.log("Factory class must be an interface.", MessageType.INVALID_FACTORY)
            fail()
        }

        if (superClass != null && type != Any::class.java || type.interfaces.isNotEmpty()) {
            logger.log("Factory class must not extend any class.", MessageType.INVALID_FACTORY)
            fail()
        }
    }

    override fun validateEventClass(type: Class<*>,
                                    factoryMethod: Method,
                                    eventGenerator: EventGenerator) {
        val logger = eventGenerator.logger

        if (!Event::class.java.isAssignableFrom(type)) {
            logger.log("Failed to generate implementation of method '$factoryMethod': event factory methods must return a type assignable to 'Event'.", MessageType.INVALID_FACTORY_METHOD)
            fail()
        }
    }

    override fun validateTypeProvider(providerParams: List<Parameter>,
                                      factoryMethod: Method,
                                      eventGenerator: EventGenerator) {
        val logger = eventGenerator.logger
        val factoryClass = factoryMethod.declaringClass

        if (providerParams.isEmpty()) {
            logger.log("Factory method '$factoryMethod' present in factory class '${factoryClass.canonicalName}' must have a parameter of type 'TypeInfo' annotated with @TypeParam.", MessageType.INVALID_FACTORY_METHOD)
            fail()
        }

        if (providerParams.size != 1) {
            logger.log("Factory method '$factoryMethod' present in factory class '${factoryClass.canonicalName}' must have only one parameter of type 'TypeInfo' annotated with @TypeParam.", MessageType.INVALID_FACTORY_METHOD)
            fail()
        }

        if (providerParams.single().type != TypeInfo::class.java) {
            logger.log("@TypeParam should be only annotated in parameter of 'TypeInfo' type. Factory method: '$factoryMethod'. Factory class '${factoryClass.canonicalName}'.", MessageType.INVALID_FACTORY_METHOD)
            fail()
        }
    }
}