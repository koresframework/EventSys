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
package com.github.projectsandstone.eventsys.logging

/**
 * Message type.
 *
 * Exceptions which have [FATAL Level][Level.FATAL] will always fail after logging the message.
 *
 * @property level Level of the message.
 */
enum class MessageType(val level: Level) {
    /**
     * Missing event method implementations.
     */
    IMPLEMENTATION_NOT_FOUND(Level.ERROR),

    /**
     * Extension is invalid if:
     *
     * - Does not provide a constructor with a single parameter of event type or super types of event value.
     */
    INVALID_EXTENSION(Level.FATAL),

    /**
     * A property method is missing.
     */
    MISSING_PROPERTY(Level.FATAL),

    /**
     * Factory class is invalid. A factory class is invalid if:
     *
     * - Is not an interface
     * - Implements any type.
     */
    INVALID_FACTORY(Level.FATAL),

    /**
     * Factory method is invalid if:
     *
     * - Does not return a type which extends `Event`
     * - Does not have name retention and does not provide names via `Name` annotation
     * (obs: methods with name retention does not require `Name` annotation, but if present, it will
     * override original name).
     */
    INVALID_FACTORY_METHOD(Level.FATAL),

    /**
     * Exception occurred inside an event listener method.
     */
    EXCEPTION_IN_LISTENER(Level.WARN),

}