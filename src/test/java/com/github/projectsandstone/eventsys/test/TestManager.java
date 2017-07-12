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

import com.github.jonathanxd.iutils.description.Description;
import com.github.jonathanxd.iutils.description.DescriptionUtil;
import com.github.jonathanxd.iutils.type.AbstractTypeInfo;
import com.github.projectsandstone.eventsys.event.EventListener;
import com.github.projectsandstone.eventsys.event.EventManager;
import com.github.projectsandstone.eventsys.gen.check.CheckHandler;
import com.github.projectsandstone.eventsys.gen.check.SuppressCapableCheckHandler;
import com.github.projectsandstone.eventsys.gen.event.CommonEventGenerator;
import com.github.projectsandstone.eventsys.gen.event.EventGenerator;
import com.github.projectsandstone.eventsys.gen.event.EventGeneratorOptions;
import com.github.projectsandstone.eventsys.gen.event.ExtensionSpecification;
import com.github.projectsandstone.eventsys.gen.event.GenericGenerationMode;
import com.github.projectsandstone.eventsys.impl.CommonEventManager;
import com.github.projectsandstone.eventsys.impl.CommonLogger;
import com.github.projectsandstone.eventsys.logging.LoggerInterface;
import com.github.projectsandstone.eventsys.test.event.MessageEvent;
import com.github.projectsandstone.eventsys.test.event.MyGenericEvent;
import com.github.projectsandstone.eventsys.test.extension.ProvidedExt;
import com.github.projectsandstone.eventsys.test.listener.MyListener;

import org.junit.Assert;
import org.junit.Test;

import java.util.Comparator;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import kotlin.Unit;

public class TestManager {

    @Test
    public void test() throws NoSuchMethodException {

        EventManager manager = new MyManager();

        CheckHandler checkHandler = manager.getEventGenerator().getCheckHandler();

        manager.getEventGenerator().getOptions().set(EventGeneratorOptions.ENABLE_SUPPRESSION, true);
        manager.getEventGenerator().getOptions().set(EventGeneratorOptions.GENERIC_EVENT_GENERATION_MODE,
                GenericGenerationMode.REFLECTION);

        manager.getEventGenerator().registerExtension(MessageEvent.class,
                new ExtensionSpecification(Unit.INSTANCE, null, ProvidedExt.class));


        if (checkHandler instanceof SuppressCapableCheckHandler) {
            Description from = DescriptionUtil.from(MessageEvent.class.getDeclaredMethod("getTest", int.class));

            ((SuppressCapableCheckHandler) checkHandler).addSuppression(from);
        }

        Constant.initialize(manager);

        manager.registerListeners(this, new MyListener());

        MessageEvent messageEvent = Constant.getMyFactoryInstance().createMessageEvent("HELLO WORLD", "[TAG] ");
        KtEvent ktEvent = Constant.getMyFactoryInstance().createKtEvent("ProjectSandstone");

        manager.dispatch(messageEvent, this);
        manager.dispatch(ktEvent, this);

        ktEvent.reset();

        MyGenericEvent<String> a = Constant.getMyFactoryInstance()
                .createMyGenericEvent(new AbstractTypeInfo<MyGenericEvent<String>>() {
                }, "A");
        MyGenericEvent<Integer> b = Constant.getMyFactoryInstance()
                .createMyGenericEvent(new AbstractTypeInfo<MyGenericEvent<Integer>>() {
                }, 1);

        MyGenericEvent<Object> c = Constant.getMyFactoryInstance()
                .createMyGenericEvent(new AbstractTypeInfo<MyGenericEvent<Object>>() {
                }, "Y");

        MyGenericEvent<Object> d = Constant.getMyFactoryInstance()
                .createMyGenericEvent(new AbstractTypeInfo<MyGenericEvent<Object>>() {
                }, 7);

        manager.dispatch(a, this);
        manager.dispatch(b, this);
        manager.dispatch(c, this);
        manager.dispatch(d, this);

        manager.dispatch(c, new AbstractTypeInfo<MyGenericEvent<String>>() {
        }.cast(), this);

        manager.dispatch(d, new AbstractTypeInfo<MyGenericEvent<String>>() {
        }.cast(), this);

        Constant.getMyFactoryInstance().createMyTestEvent("Cup", 100);
        Constant.getMyFactoryInstance().createMyTestEvent2("Cup", 100);

        Assert.assertEquals("[TAG] hello world", messageEvent.getMessage());

        ProvidedExt extension = messageEvent.getExtension(ProvidedExt.class);

        Assert.assertTrue(extension != null);
        Assert.assertEquals("_OK_", extension.getTest());

    }


    private static class MyManager extends CommonEventManager {

        static final ThreadFactory COMMON_THREAD_FACTORY = Executors.defaultThreadFactory();
        static final LoggerInterface COMMON_LOGGER = new CommonLogger();
        private static final Comparator<EventListener<?>> COMMON_SORTER = Comparator.comparing(EventListener::getPriority);
        private static final EventGenerator COMMON_EVENT_GENERATOR = new CommonEventGenerator(COMMON_LOGGER);

        MyManager() {
            super(true, COMMON_SORTER, COMMON_THREAD_FACTORY, COMMON_LOGGER, COMMON_EVENT_GENERATOR);
        }
    }


}
