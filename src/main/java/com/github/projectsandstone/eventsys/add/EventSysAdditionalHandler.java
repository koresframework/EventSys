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
package com.github.projectsandstone.eventsys.add;

import com.github.jonathanxd.codeapi.CodeSource;
import com.github.jonathanxd.codeapi.MutableCodeSource;
import com.github.jonathanxd.codeapi.base.ConstructorDeclaration;
import com.github.jonathanxd.codeapi.base.FieldDeclaration;
import com.github.jonathanxd.codeapi.base.MethodDeclaration;
import com.github.jonathanxd.codeapi.base.TypeSpec;
import com.github.jonathanxd.codeapi.common.MethodTypeSpec;
import com.github.jonathanxd.codeapi.factory.Factories;
import com.github.jonathanxd.codeapi.factory.InvocationFactory;
import com.github.jonathanxd.codeapi.literal.Literals;
import com.github.jonathanxd.codeapi.type.TypeRef;
import com.github.jonathanxd.iutils.collection.Collections3;
import com.github.jonathanxd.iutils.data.TypedData;
import com.github.jonathanxd.iutils.object.TypedKey;
import com.github.jonathanxd.iutils.type.AbstractTypeInfo;
import com.github.jonathanxd.iutils.type.TypeInfo;
import com.github.projectsandstone.eventsys.event.Event;
import com.github.projectsandstone.eventsys.event.property.PropertyHolder;
import com.github.projectsandstone.eventsys.gen.event.EventClassGeneratorKt;
import com.github.projectsandstone.eventsys.gen.event.PropertyInfo;

import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class EventSysAdditionalHandler {

    private static final TypedKey<List<PropertyInfo>> PROP_INFO_KEY = new TypedKey<>("PROP_INFO",
            new AbstractTypeInfo<List<PropertyInfo>>() {
            });

    private static void registerPropertiesIfAbsent(TypedData typedData, Class<?> base) {
        if (!PROP_INFO_KEY.contains(typedData)) {
            PROP_INFO_KEY.set(typedData, EventClassGeneratorKt.getProperties(base, Collections.emptyList(), Collections.emptyList()));
        }
    }

    @NotNull
    public static List<FieldDeclaration> generateAdditionalFields(@NotNull List<FieldDeclaration> fields,
                                                                  @NotNull TypeRef owner,
                                                                  @NotNull Class<?> base,
                                                                  @NotNull TypedData data) {
        registerPropertiesIfAbsent(data, base);
        return EventClassGeneratorKt.getPropertyFields();
    }

    @NotNull
    public static CodeSource generateAdditionalConstructorBody(@NotNull ConstructorDeclaration constructorDeclaration,
                                                               @NotNull TypeRef owner,
                                                               @NotNull Class<?> base,
                                                               @NotNull TypedData data) {
        registerPropertiesIfAbsent(data, base);

        List<PropertyInfo> props = PROP_INFO_KEY.getOrNull(data);

        if (props != null) {
            MutableCodeSource source = MutableCodeSource.create();

            EventClassGeneratorKt.genConstructorPropertiesMap(source, props);

            return source.toImmutable();
        }

        return CodeSource.empty();
    }


    @NotNull
    public static List<MethodTypeSpec> getMethodsToImplement(@NotNull TypeRef owner,
                                                             @NotNull Class<?> base,
                                                             @NotNull TypedData data) {
        registerPropertiesIfAbsent(data, base);
        return Collections3.listOf(
                new MethodTypeSpec(Event.class, "getEventTypeInfo", new TypeSpec(TypeInfo.class)),
                new MethodTypeSpec(PropertyHolder.class, "getProperties", new TypeSpec(Map.class))
        );
    }

    @NotNull
    public static Optional<MethodDeclaration> generateImplementation(@NotNull MethodDeclaration declaration,
                                                                     @NotNull TypeRef owner,
                                                                     @NotNull Class<?> base,
                                                                     @NotNull TypedData data) {
        registerPropertiesIfAbsent(data, base);


        if (declaration.getParameters().isEmpty()) {

            if (declaration.getName().equals("getEventTypeInfo")) {

                Class<?> itf = data.getOptional("interface", TypeInfo.of(Class.class)).orElse(base);

                return Optional.of(declaration)
                        .map(it -> it.builder()
                                .body(CodeSource.fromPart(Factories.returnValue(TypeInfo.class,
                                        InvocationFactory.invokeStatic(
                                                TypeInfo.class,
                                                "of",
                                                new TypeSpec(TypeInfo.class, Collections.singletonList(Class.class)),
                                                Collections.singletonList(Literals.CLASS(itf))
                                        )
                                )))
                                .build());
            } else if (declaration.getName().equals("getProperties")) {
                return Optional.of(declaration)
                        .map(it -> it.builder()
                                .body(CodeSource.fromPart(Factories.returnValue(Map.class,
                                        Factories.accessThisField(EventClassGeneratorKt.getPropertiesFieldType(),
                                                EventClassGeneratorKt.propertiesUnmodName)
                                )))
                                .build());
            }
        }

        return Optional.empty();
    }
}