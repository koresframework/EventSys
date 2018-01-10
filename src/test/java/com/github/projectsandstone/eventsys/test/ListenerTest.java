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
import com.github.projectsandstone.eventsys.event.Event;
import com.github.projectsandstone.eventsys.event.EventManager;
import com.github.projectsandstone.eventsys.event.annotation.Listener;
import com.github.projectsandstone.eventsys.event.annotation.Name;
import com.github.projectsandstone.eventsys.event.annotation.NotNullValue;
import com.github.projectsandstone.eventsys.gen.event.EventGenerator;
import com.github.projectsandstone.eventsys.impl.DefaultEventManager;
import com.github.projectsandstone.eventsys.util.EventFactoryHelperKt;

import org.junit.Assert;
import org.junit.Test;

public class ListenerTest {

    private boolean dispatched = false;
    private String name = null;

    @Test
    public void common() throws Throwable {
        EventManager manager = new DefaultEventManager();
        EventGenerator generator = manager.getEventGenerator();

        LoginEvent event =
                EventFactoryHelperKt.create(generator.createEventClass(TypeInfo.of(LoginEvent.class)),
                        MapUtils.mapOf("name", "Test"));

        manager.registerListeners(this, this);
        manager.dispatch(event, this);
        Assert.assertEquals("Test", this.name);
        Assert.assertTrue(this.dispatched);
    }

    @Listener
    public void myListener(LoginEvent event) {
        Assert.assertFalse(this.dispatched);
        this.dispatched = true;
    }

    @Listener
    public void myListener(LoginEvent event, @Name("name") String name) {
        Assert.assertNull(this.name);
        this.name = name;
    }

    public interface LoginEvent extends Event {
        @NotNullValue
        String getName();

        void setName(String name);
    }

}
