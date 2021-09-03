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
package com.github.koresframework.eventsys.test;

import com.github.jonathanxd.iutils.map.MapUtils;
import com.github.jonathanxd.iutils.type.TypeParameterProvider;
import com.koresframework.kores.type.Generic;
import com.koresframework.kores.type.KoresTypes;
import com.github.koresframework.eventsys.event.Event;
import com.github.koresframework.eventsys.event.annotation.NotNullValue;
import com.github.koresframework.eventsys.extension.ExtensionSpecification;
import com.github.koresframework.eventsys.gen.event.CommonEventGenerator;
import com.github.koresframework.eventsys.gen.event.EventGenerator;
import com.github.koresframework.eventsys.impl.CommonLogger;
import com.github.koresframework.eventsys.util.EventFactoryHelperKt;

import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;

public class EventTest {

    @Test
    public void impl() {
        EventGenerator generator = new CommonEventGenerator(new CommonLogger());

        Class<? extends LoginEvent> eventClass = generator.<LoginEvent>createEventClass(
                Generic.type(LoginEvent.class)).invoke();
        LoginEvent loginEvent = EventFactoryHelperKt.create(eventClass,
                MapUtils.mapOf("name", "Player"));
        LoginEvent loginEvent2 = EventFactoryHelperKt.create(eventClass,
                MapUtils.mapOf("name", "Player2"));

        Assert.assertEquals("Player", loginEvent.getName());
        Assert.assertEquals("Player2", loginEvent2.getName());

        try {
            EventFactoryHelperKt.create(eventClass, MapUtils.mapOf("name", null));
            Assert.fail("Login must fail to create with null value for 'name' property.");
        } catch (Exception e) {
            Assert.assertTrue("Exception of null property must be NullPointerException",
                    e instanceof NullPointerException);
        }
    }

    @Test
    public void bridgeAndExt() {
        EventGenerator generator = new CommonEventGenerator(new CommonLogger());

        // A bridge method transform(Object)Object should be generated in event implementation class
        // If it does not happen, casting ToStringValueEvent to TransformEvent
        // and then calling transform on it will cause AbstractMethodError to be thrown
        // Also check warnings may be printed
        // To check this out, disable bridge generation:
        // generator.getOptions().set(EventGeneratorOptions.ENABLE_BRIDGE, Boolean.FALSE);
        Class<? extends ToStringValueEvent<Integer>> eventClass = generator.<ToStringValueEvent<Integer>>createEventClass(
                KoresTypes.getAsGeneric(KoresTypes.getKoresType(
                        new TypeParameterProvider<ToStringValueEvent<Integer>>() {
                        }.getType())),
                Collections.emptyList(),
                Collections.singletonList(new ExtensionSpecification(
                        this,
                        null,
                        IntToString.class
                ))
        ).invoke();

        TransformEvent<String, Integer> transform = EventFactoryHelperKt.create(eventClass,
                Collections.emptyMap());

        Assert.assertEquals("9", transform.transform(9));
    }

    public interface LoginEvent extends Event {
        @NotNullValue
        String getName();

        void setName(String name);
    }

    public interface TransformEvent<T, F> extends Event {
        T transform(F from);
    }

    public interface ToStringValueEvent<F> extends TransformEvent<String, F> {
        @Override
        String transform(F from);
    }

    public static class IntToString {
        private final Event event;

        public IntToString(Event event) {
            this.event = event;
        }

        public String transform(Integer i) {
            return i.toString();
        }
    }
}
