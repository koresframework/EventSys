/*
 *      EventSys - Event implementation generator written on top of Kores
 *
 *         The MIT License (MIT)
 *
 *      Copyright (c) 2019 ProjectSandstone <https://github.com/ProjectSandstone/EventSys>
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

import com.github.jonathanxd.kores.base.*
import com.github.jonathanxd.kores.type.KoresType
import com.github.koresframework.eventsys.gen.event.EventGenerator
import com.github.koresframework.eventsys.extension.ExtensionSpecification
import com.github.koresframework.eventsys.util.DeclaredMethod
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.lang.reflect.Parameter
import java.lang.reflect.Type

/**
 * Handler which checks the generated code.
 */
interface CheckHandler {

    /**
     * Check if [implementedMethods] contains all non implemented methods of [type].
     */
    fun checkImplementation(implementedMethods: List<MethodDeclaration>,
                            type: TypeDeclaration,
                            extensions: List<ExtensionSpecification>,
                            eventGenerator: EventGenerator)

    /**
     * Checks if [methods] has duplicated methods.
     */
    fun checkDuplicatedMethods(methods: List<MethodDeclaration>)

    /**
     * Validates [extension] specification. Valid extensions are those which has
     * an constructor of the same type as event or a super type.
     *
     * This function should also return valid constructor.
     */
    fun validateExtension(extension: ExtensionSpecification,
                          extensionClass: TypeDeclaration,
                          type: ClassDeclaration,
                          eventGenerator: EventGenerator): ConstructorDeclaration

    // Factory

    /**
     * Validates factory class. Valid factories are those which are interfaces
     * which does not extends any other class.
     */
    fun validateFactoryClass(type: TypeDeclaration,
                             eventGenerator: EventGenerator)

    /**
     * Validates event class.
     */
    fun validateEventClass(type: TypeDeclaration,
                           factoryMethod: MethodDeclaration,
                           eventGenerator: EventGenerator)

    /**
     * Validate type provider parameters.
     */
    fun validateTypeProvider(providerParams: List<KoresParameter>,
                             factoryMethod: DeclaredMethod,
                             eventGenerator: EventGenerator)

}