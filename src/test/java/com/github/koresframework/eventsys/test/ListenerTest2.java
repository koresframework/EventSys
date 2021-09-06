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
import com.github.jonathanxd.iutils.opt.OptObject;
import com.github.koresframework.eventsys.event.Event;
import com.github.koresframework.eventsys.event.annotation.Filter;
import com.github.koresframework.eventsys.event.annotation.Listener;
import com.github.koresframework.eventsys.event.annotation.Name;
import com.github.koresframework.eventsys.event.annotation.NotNullValue;
import com.github.koresframework.eventsys.gen.event.EventGenerator;
import com.github.koresframework.eventsys.impl.CommonEventManager;
import com.github.koresframework.eventsys.impl.DefaultEventManager;
import com.github.koresframework.eventsys.util.EventFactoryHelperKt;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collections;

public class ListenerTest2 {

    private String ip = null;
    private boolean dispatched = false;

    @Test
    public void common() throws Throwable {
        CommonEventManager manager = new DefaultEventManager();
        EventGenerator generator = manager.getEventGenerator();

        ConnectEvent event =
                EventFactoryHelperKt.<ConnectEvent>create(
                        generator.createEventClass(ConnectEvent.class).invoke(),
                        MapUtils.mapOf("ip", "127.0.0.1"));

        manager.getEventListenerRegistry().registerListeners(this, this);
        manager.dispatchBlocking(event, this);
        Assertions.assertEquals("127.0.0.1", this.ip);

        DisconnectEvent event2 =
                EventFactoryHelperKt.<DisconnectEvent>create(
                        generator.createEventClass(DisconnectEvent.class).invoke(),
                        MapUtils.mapOf("ip", "0.0.0.0"));

        this.ip = null;

        manager.dispatchBlocking(event2, this);

        Assertions.assertEquals("0.0.0.0", this.ip);

        EmptyEvent event3 =
                EventFactoryHelperKt.<EmptyEvent>create(
                        generator.createEventClass(EmptyEvent.class).invoke(),
                        Collections.emptyMap());

        this.ip = null;

        manager.dispatchBlocking(event3, this);

        Assertions.assertTrue(this.dispatched);
    }

    @Filter
    @Listener
    public void myListener(@Name("ip") String ip) {
        this.ip = ip;
    }

    @Filter
    @Listener
    public void myListener2(@Name("ip") OptObject<String> ip) {
        if (!ip.isPresent())
            this.dispatched = true;
    }

    public interface DisconnectEvent extends Event {
        @NotNullValue
        String getIp();
    }

    public interface ConnectEvent extends Event {
        @NotNullValue
        String getIp();
    }

    public interface EmptyEvent extends Event {

    }
}
