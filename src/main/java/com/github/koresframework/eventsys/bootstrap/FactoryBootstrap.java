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
package com.github.koresframework.eventsys.bootstrap;

import com.koresframework.kores.common.MethodTypeSpec;
import com.koresframework.kores.factory.Factories;
import com.koresframework.kores.type.ImplicitKoresType;
import com.koresframework.kores.type.KoresTypes;
import com.github.koresframework.eventsys.context.EnvironmentContext;
import com.github.koresframework.eventsys.event.Cancellable;
import com.github.koresframework.eventsys.event.Event;
import com.github.koresframework.eventsys.event.annotation.Name;
import com.github.koresframework.eventsys.event.annotation.TypeParam;
import com.github.koresframework.eventsys.gen.event.EventGenerator;
import com.github.koresframework.eventsys.extension.ExtensionSpecification;
import com.github.koresframework.eventsys.gen.event.PropertyInfo;

import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.MutableCallSite;
import java.lang.reflect.Constructor;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FactoryBootstrap {

    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();
    public static final MethodTypeSpec BOOTSTRAP_SPEC = new MethodTypeSpec(
            FactoryBootstrap.class,
            "factoryBootstrap",
            Factories.typeSpec(CallSite.class,
                    MethodHandles.Lookup.class,
                    String.class,
                    MethodType.class, Object[].class
            )
    );

    private static final MethodHandle FALLBACK;
    private static final Map<Class<?>, List<CachedParam>> propertyOrderCache = new HashMap<>();

    static {
        try {
            FALLBACK = LOOKUP.findStatic(
                    FactoryBootstrap.class,
                    "fallback",
                    MethodType.methodType(Object.class, MyCallSite.class, Object[].class));
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public static CallSite factoryBootstrap(MethodHandles.Lookup caller,
                                            String name,
                                            MethodType type,
                                            Object... parameters) {
        MyCallSite myCallSite = new MyCallSite(caller, name, type);

        MethodHandle methodHandle = FALLBACK.bindTo(myCallSite).asCollector(Object[].class, type.parameterCount()).asType(type);

        myCallSite.setTarget(methodHandle);

        return myCallSite;
    }

    @SuppressWarnings("unchecked")
    public static Object fallback(MyCallSite callSite, Object[] args) throws Throwable {
        if (args.length != 6)
            throw new IllegalArgumentException("Illegal dynamic invocation.");

        Object generatorObj = args[0];
        Object typeObj = args[1];
        Object additionalPropertiesObj = args[2];
        Object extensionsObj = args[3];
        Object namesObj = args[4];
        Object argsObj = args[5];

        if (generatorObj instanceof EventGenerator
                && typeObj instanceof Type
                && additionalPropertiesObj instanceof List<?>
                && extensionsObj instanceof List<?>
                && namesObj instanceof String[]
                && argsObj instanceof Object[]) {

            EventGenerator eventGenerator = (EventGenerator) generatorObj;
            Type eventType = (Type) typeObj;
            List<PropertyInfo> additionalProperties = (List<PropertyInfo>) additionalPropertiesObj;
            List<ExtensionSpecification> extensions = (List<ExtensionSpecification>) extensionsObj;
            String[] names = (String[]) namesObj;
            Object[] methodArgs = (Object[]) argsObj;

            Class<? extends Event> aClass = eventGenerator.createEventClass(
                    ImplicitKoresType.getConcreteType(eventType),
                    additionalProperties,
                    extensions,
                    new EnvironmentContext()
            ).invoke();

            if (!propertyOrderCache.containsKey(aClass)) {
                List<CachedParam> propertyOrder = new ArrayList<>();

                Constructor<?> constructor = aClass.getDeclaredConstructors()[0];

                Parameter[] parameters = constructor.getParameters();

                for (Parameter parameter : parameters) {
                    final String name;

                    if (parameter.isAnnotationPresent(Name.class))
                        name = parameter.getDeclaredAnnotation(Name.class).value();
                    else
                        name = parameter.getName();

                    propertyOrder.add(new CachedParam(parameter.isAnnotationPresent(TypeParam.class), parameter.getType(), name));

                }

                propertyOrderCache.put(aClass, propertyOrder);

            }

            List<CachedParam> cachedParams = propertyOrderCache.get(aClass);

            boolean isCancellable = KoresTypes.getKoresType(Cancellable.class).isAssignableFrom(eventType);
            Object[] sortedArgs = new Object[cachedParams.size()];
            Class<?>[] types = new Class[cachedParams.size()];

            for (int i = 0; i < cachedParams.size(); ++i) {
                CachedParam cachedParam = cachedParams.get(i);
                types[i] = cachedParam.type;

                String name = cachedParam.name;
                boolean found = false;

                if (isCancellable && name.equals("cancelled")) {
                    found = true;
                    sortedArgs[i] = true;
                }

                if (cachedParam.isTypeProvider && i == 0) {
                    found = true;
                    sortedArgs[i] = methodArgs[i];
                }

                for (int n = 0; n < names.length; n++) {
                    if (names[n].equals(name)) {
                        found = true;
                        sortedArgs[i] = methodArgs[n];
                    }
                }

                if (!found)
                    throw new IllegalStateException("Failed to construct event class '" + aClass + "'. No property with following name was found: " + name + "! Names: "+Arrays.toString(names)+". Args:"+Arrays.toString(methodArgs));
            }

            MethodHandle constructor =
                    callSite.callerLookup.findConstructor(aClass, MethodType.methodType(Void.TYPE, types));

            return constructor.invokeWithArguments(sortedArgs);


        }

        throw new IllegalStateException("Cannot determine invocation format, arguments: "+ Arrays.toString(args));
    }

    public static class MyCallSite extends MutableCallSite {

        final MethodHandles.Lookup callerLookup;
        final String name;

        MyCallSite(MethodHandles.Lookup callerLookup, String name, MethodType type) {
            super(type);
            this.callerLookup = callerLookup;
            this.name = name;
        }

        MyCallSite(MethodHandles.Lookup callerLookup, MethodHandle target, String name) {
            super(target);
            this.callerLookup = callerLookup;
            this.name = name;
        }


    }

    static class CachedParam {
        final boolean isTypeProvider;
        final Class<?> type;
        final String name;

        CachedParam(boolean isTypeProvider, Class<?> type, String name) {
            this.isTypeProvider = isTypeProvider;
            this.type = type;
            this.name = name;
        }
    }
}
