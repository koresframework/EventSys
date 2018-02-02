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
package com.github.projectsandstone.eventsys.reflect;

import com.github.jonathanxd.iutils.object.Lazy;
import com.github.jonathanxd.kores.base.KoresParameter;
import com.github.jonathanxd.kores.util.conversion.ConversionsKt;
import com.github.projectsandstone.eventsys.event.Cancellable;
import com.github.projectsandstone.eventsys.event.annotation.Name;

import java.lang.reflect.Constructor;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.List;

public class PropertiesSort {

    public static Object[] sort(Constructor<?> constructor, String[] names, Object[] args) {
        Object[] sorted = new Object[args.length];

        Class<?> aClass = constructor.getDeclaringClass();

        boolean isCancellable = Cancellable.class.isAssignableFrom(aClass);
        Parameter[] parameters = constructor.getParameters();
        Lazy<List<KoresParameter>> lazyParameters = Lazy.nonNull(Lazy.lazy(() -> ConversionsKt.getKoresParameters(constructor)));

        for (int i = 0; i < parameters.length; i++) {

            Parameter parameter = parameters[i];

            final String name;

            if (parameter.isAnnotationPresent(Name.class)) {
                name = parameter.getDeclaredAnnotation(Name.class).value();
            } else {
                String tmp = parameter.getName();
                if (tmp.startsWith("arg"))
                    name = lazyParameters.get().get(i).getName();
                else
                    name = tmp;
            }

            boolean found = false;

            if (isCancellable && name.equals("cancelled")) {
                found = true;
                sorted[i] = true;
            }

            for (int n = 0; n < names.length; n++) {
                if (names[n].equals(name)) {
                    found = true;
                    sorted[i] = args[n];
                }
            }

            if (!found)
                throw new IllegalStateException("Failed to construct event class '" + aClass + "'. No property with following name was found: " + name + "! Names: " + Arrays.toString(names) + ". Args:" + Arrays.toString(args));
        }

        return sorted;
    }

}
