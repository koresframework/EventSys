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
package com.github.projectsandstone.eventsys.event.annotation;

import com.github.jonathanxd.iutils.object.Default;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Defines the extension of a event class that will be created by the factory class.
 *
 * Since 1.1, event interfaces (and sub-classes) can be annotated with this. If annotated in event
 * interface or sub-classes, the event generated will always implement extensions regardless the
 * factory specification.
 *
 * Since 1.1.2, extension classes can override properties getter and setter. And EventSys will not
 * generate fields for properties where both getter and setter is override (or only the getter if
 * property is immutable).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
@Repeatable(Extensions.class)
public @interface Extension {
    /**
     * Interface which generated event class should implement.
     *
     * @return Interface which generated event class should implement.
     */
    Class<?> implement() default Default.class;

    /**
     * An extension class which implements functions of {@link #implement()} interface and provide
     * additional methods. All methods of provided class is added to target event and is delegated
     * to the class, which should have a single-arg constructor which accepts target event type (or
     * a sub-type, it includes the {@link #implement()}).
     *
     * Only non-static functions are added to target event.
     *
     * @return Extension class.
     */
    Class<?> extensionClass() default Default.class;
}
