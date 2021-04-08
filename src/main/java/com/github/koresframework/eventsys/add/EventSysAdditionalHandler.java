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
package com.github.koresframework.eventsys.add;

import com.github.jonathanxd.iutils.collection.Collections3;
import com.github.jonathanxd.iutils.data.TypedData;
import com.github.jonathanxd.iutils.kt.EitherUtilKt;
import com.github.jonathanxd.iutils.object.TypedKey;
import com.github.jonathanxd.iutils.type.TypeInfo;
import com.github.jonathanxd.iutils.type.TypeParameterProvider;
import com.github.jonathanxd.kores.Instructions;
import com.github.jonathanxd.kores.MutableInstructions;
import com.github.jonathanxd.kores.base.ConstructorDeclaration;
import com.github.jonathanxd.kores.base.FieldDeclaration;
import com.github.jonathanxd.kores.base.MethodDeclaration;
import com.github.jonathanxd.kores.base.TypeDeclaration;
import com.github.jonathanxd.kores.base.TypeSpec;
import com.github.jonathanxd.kores.common.MethodTypeSpec;
import com.github.jonathanxd.kores.factory.Factories;
import com.github.jonathanxd.kores.factory.InvocationFactory;
import com.github.jonathanxd.kores.literal.Literals;
import com.github.jonathanxd.kores.type.KoresTypes;
import com.github.jonathanxd.kores.type.TypeRef;
import com.github.koresframework.eventsys.event.Event;
import com.github.koresframework.eventsys.event.property.PropertyHolder;
import com.github.koresframework.eventsys.gen.event.EventClassGeneratorKt;
import com.github.koresframework.eventsys.gen.event.PropertyInfo;
import com.github.koresframework.eventsys.util.DeclarationCache;

import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class EventSysAdditionalHandler {

    private static final TypedKey<List<PropertyInfo>> PROP_INFO_KEY = new TypedKey<>("PROP_INFO",
            new TypeParameterProvider<List<PropertyInfo>>() {
            }.createTypeInfo());

    private static void registerPropertiesIfAbsent(TypedData typedData, Class<?> base) {
        DeclarationCache cache = new DeclarationCache();
        TypeDeclaration declaration = EitherUtilKt.getRightOrFail(KoresTypes.getKoresType(base)
                .getBindedDefaultResolver()
                .resolveTypeDeclaration());

        if (!PROP_INFO_KEY.contains(typedData)) {
            PROP_INFO_KEY.set(typedData,
                    EventClassGeneratorKt.getProperties(
                            declaration,
                            Collections.emptyList(),
                            Collections.emptyList(),
                            cache
                    )
            );
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
    public static Instructions generateAdditionalConstructorBody(@NotNull ConstructorDeclaration constructorDeclaration,
                                                                 @NotNull TypeRef owner,
                                                                 @NotNull Class<?> base,
                                                                 @NotNull TypedData data) {
        registerPropertiesIfAbsent(data, base);

        List<PropertyInfo> props = PROP_INFO_KEY.getOrNull(data);

        if (props != null) {
            MutableInstructions source = MutableInstructions.create();

            EventClassGeneratorKt.genConstructorPropertiesMap(source, props);

            return source.toImmutable();
        }

        return Instructions.empty();
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
                                .body(Instructions.fromPart(Factories.returnValue(TypeInfo.class,
                                        InvocationFactory.invokeStatic(
                                                TypeInfo.class,
                                                "of",
                                                new TypeSpec(TypeInfo.class,
                                                        Collections.singletonList(Class.class)),
                                                Collections.singletonList(Literals.CLASS(itf))
                                        )
                                )))
                                .build());
            } else if (declaration.getName().equals("getProperties")) {
                return Optional.of(declaration)
                        .map(it -> it.builder()
                                .body(Instructions.fromPart(Factories.returnValue(Map.class,
                                        Factories.accessThisField(
                                                EventClassGeneratorKt.getPropertiesFieldType(),
                                                EventClassGeneratorKt.propertiesUnmodName)
                                )))
                                .build());
            }
        }

        return Optional.empty();
    }
}
