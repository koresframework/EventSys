/**
 *      EventImpl - Event implementation generator written on top of CodeAPI
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

import com.github.projectsandstone.eventsys.event.EventListener;
import com.github.projectsandstone.eventsys.event.EventManager;
import com.github.projectsandstone.eventsys.gen.event.CommonEventGenerator;
import com.github.projectsandstone.eventsys.gen.event.EventGenerator;
import com.github.projectsandstone.eventsys.impl.CommonEventManager;
import com.github.projectsandstone.eventsys.logging.LoggerInterface;
import com.github.projectsandstone.eventsys.test.event.MessageEvent;
import com.github.projectsandstone.eventsys.test.listener.MyListener;

import org.junit.Assert;
import org.junit.Test;

import java.util.Comparator;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public class TestManager {

    @Test
    public void test() {
        EventManager manager = new MyManager();

        Constant.initialize(manager);

        manager.registerListeners(this, new MyListener());

        MessageEvent messageEvent = Constant.getMyFactoryInstance().createMessageEvent("HELLO WORLD", "[TAG] ");
        KtEvent ktEvent = Constant.getMyFactoryInstance().createKtEvent("ProjectSandstone");

        manager.dispatch(messageEvent, this);
        manager.dispatch(ktEvent, this);

        ktEvent.reset();

        Assert.assertEquals("[TAG] hello world", messageEvent.getMessage());
    }


    private static class MyManager extends CommonEventManager {

        private static final Comparator<EventListener<?>> COMMON_SORTER = Comparator.comparing(EventListener::getPriority);
        private static final ThreadFactory COMMON_THREAD_FACTORY = Executors.defaultThreadFactory();
        private static final LoggerInterface COMMON_LOGGER = (message, throwable) -> {
            System.err.println(message);
            throwable.printStackTrace();
        };
        private static final EventGenerator COMMON_EVENT_GENERATOR = new CommonEventGenerator();

        MyManager() {
            super(true, COMMON_SORTER, COMMON_THREAD_FACTORY, COMMON_LOGGER, COMMON_EVENT_GENERATOR);
        }
    }
}
