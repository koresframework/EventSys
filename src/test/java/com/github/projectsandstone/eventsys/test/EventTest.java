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
package com.github.projectsandstone.eventsys.test;

import com.github.jonathanxd.iutils.map.MapUtils;
import com.github.jonathanxd.iutils.type.TypeInfo;
import com.github.jonathanxd.iutils.type.TypeParameterProvider;
import com.github.projectsandstone.eventsys.event.Event;
import com.github.projectsandstone.eventsys.event.annotation.NotNullValue;
import com.github.projectsandstone.eventsys.gen.event.CommonEventGenerator;
import com.github.projectsandstone.eventsys.gen.event.EventGenerator;
import com.github.projectsandstone.eventsys.extension.ExtensionSpecification;
import com.github.projectsandstone.eventsys.impl.CommonLogger;
import com.github.projectsandstone.eventsys.util.EventFactoryHelperKt;

import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;

public class EventTest {

    @Test
    public void impl() {
        EventGenerator generator = new CommonEventGenerator(new CommonLogger());

        Class<LoginEvent> eventClass = generator.createEventClass(TypeInfo.of(LoginEvent.class));
        LoginEvent loginEvent = EventFactoryHelperKt.create(eventClass, MapUtils.mapOf("name", "Player"));
        LoginEvent loginEvent2 = EventFactoryHelperKt.create(eventClass, MapUtils.mapOf("name", "Player2"));

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
        Class<ToStringValueEvent<Integer>> eventClass = generator.createEventClass(
                new TypeParameterProvider<ToStringValueEvent<Integer>>() {
                }.createTypeInfo(),
                Collections.emptyList(),
                Collections.singletonList(new ExtensionSpecification(
                        this,
                        null,
                        IntToString.class
                ))
        );

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
