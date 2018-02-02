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
package com.github.projectsandstone.eventsys.extension

/**
 * Specification of event extension. An Extension is a indirect implementation of event methods,
 * the extension may be plugged to event class before `Event class generation` (trying to plug after that
 * cause `Event class` to be regenerated in next request).
 *
 * @property residence Location which this extension was specified. For annotations,
 * this will be the annotated elements. (may be [Unit])
 * @property implement Specifies the interface to add to `event class hierarchy`.
 * @property extensionClass Specifies the extension class which implements the methods
 * of [implement], of `event base interfaces`, or provides additional features to the event.
 * This class is instantiated in event constructor and stored as variable,
 * a single-arg constructor is required, the first argument must be
 * a type assignable to target event value.
 */
data class ExtensionSpecification(val residence: Any, val implement: Class<*>?, val extensionClass: Class<*>?)