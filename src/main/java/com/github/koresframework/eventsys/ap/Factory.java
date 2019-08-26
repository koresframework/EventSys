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
package com.github.koresframework.eventsys.ap;

import com.github.koresframework.eventsys.event.annotation.Extension;
import com.github.koresframework.eventsys.event.annotation.TypeParam;
import com.github.koresframework.eventsys.event.annotation.LazyGeneration;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an event which factory method should be generated.
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
@Repeatable(Factories.class)
public @interface Factory {

    /**
     * Name of target factory class which method should be added, if the class does not exists, it
     * will be created.
     *
     * @return Name of target factory class which method should be added, if the class does not
     * exists, it will be created.
     */
    String value();

    /**
     * Name of factory method. Default uses the type name.
     *
     * @return Name of factory method. Default uses the type name.
     */
    String methodName() default "";

    /**
     * True to inherit properties of sub-types.
     *
     * AnnotationProcessor will lookup for all sub-type annotations and will only inherit non
     * duplicated properties.
     *
     * This property does not have deep effect, which means that super-classes which inherits
     * annotated type will not inherit properties unless the annotation on the value specifies it.
     *
     * @return True to inherit properties of sub-types
     */
    boolean inheritProperties() default false;

    /**
     * Extensions specifications.
     *
     * @return Extensions specifications.
     */
    Extension[] extensions() default {};

    /**
     * True to omit {@link TypeParam} from factory method of generic events.
     *
     * @return True to omit {@link TypeParam} from factory method of generic events.
     */
    boolean omitTypeParam() default false;

    /**
     * True to annotated factory method with {@link LazyGeneration}.
     *
     * @return True to annotated factory method with {@link LazyGeneration}.
     */
    boolean lazy() default false;
}
