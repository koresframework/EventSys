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
package com.github.projectsandstone.eventsys.test;

import com.github.projectsandstone.eventsys.event.Event;
import com.github.projectsandstone.eventsys.event.annotation.LazyGeneration;
import com.github.projectsandstone.eventsys.event.annotation.Name;
import com.github.projectsandstone.eventsys.event.annotation.NotNullValue;
import com.github.projectsandstone.eventsys.extension.ExtensionSpecification;
import com.github.projectsandstone.eventsys.gen.event.CommonEventGenerator;
import com.github.projectsandstone.eventsys.gen.event.EventGenerator;
import com.github.projectsandstone.eventsys.impl.CommonLogger;

import org.junit.Assert;
import org.junit.Test;

public class FactoryTest {

    @Test
    public void factoryTest() {
        EventGenerator generator = new CommonEventGenerator(new CommonLogger());

        MyFactory factory = generator.createFactory(MyFactory.class);

        RegistryEvent registryEvent = factory.createRegistryEvent("123456");

        Assert.assertEquals("123456", registryEvent.getPassword());
    }

    @Test
    public void lazyFactoryTest() {
        EventGenerator generator = new CommonEventGenerator(new CommonLogger());

        MyFactory factory = generator.createFactory(MyFactory.class);

        RegistryEvent registryEvent = factory.createRegistryEventLazy("123456");

        Assert.assertEquals("123456", registryEvent.getPassword());

        generator.registerExtension(RegistryEvent.class, new ExtensionSpecification(
                this,
                Base.class,
                Ext.class
        ));

        registryEvent = factory.createRegistryEventLazy("123456");
        Assert.assertTrue(registryEvent instanceof Base);
        Base base = (Base) registryEvent;
        Assert.assertEquals("Password: 123456", base.printPassword());
    }

    public interface MyFactory {
        RegistryEvent createRegistryEvent(@Name("password") String password);

        @LazyGeneration
        RegistryEvent createRegistryEventLazy(@Name("password") String password);
    }

    public interface RegistryEvent extends Event {
        @NotNullValue
        String getPassword();
    }

    public interface Base {
        String printPassword();
    }

    public static class Ext implements Base {
        private final RegistryEvent registryEvent;

        public Ext(RegistryEvent registryEvent) {
            this.registryEvent = registryEvent;
        }

        @Override
        public String printPassword() {
            return "Password: "+this.registryEvent.getPassword();
        }
    }
}
