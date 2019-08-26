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
package com.github.koresframework.eventsys.gen.event

import com.github.jonathanxd.iutils.option.Option

object EventGeneratorOptions {

    /**
     * Uses asynchronous executions where possible. This does not make generator faster unless you
     * have factories with several event classes.
     */
    @JvmField
    val ASYNC = Option(true)

    /**
     * True to enable suppression of checks. This is disabled by default.
     */
    @JvmField
    val ENABLE_SUPPRESSION = Option(false)

    /**
     * Mode of lazy generation of event classes on factory classes.
     */
    @JvmField
    val LAZY_EVENT_GENERATION_MODE = Option(LazyGenerationMode.BOOTSTRAP)

    /**
     * Enables bridge method generation, this is default in EventSys because kotlin compiler
     * does not add bridge methods in interfaces like Java 8 do. If you disable this, you will probably
     * receive log messages about `not implemented methods` in some cases. Bridge methods introduces
     * a little overhead (depends on the amount of methods in generated class and inherited classes).
     */
    @JvmField
    val ENABLE_BRIDGE = Option(true)

    /**
     * Parse arguments and dispatch to listener method with Java 7 MethodHandles instead of generating listener class.
     */
    @JvmField
    val USE_METHOD_HANDLE_LISTENER = Option(false)
}