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

import com.github.jonathanxd.iutils.description.Description;
import com.github.jonathanxd.iutils.description.DescriptionUtil;
import com.github.jonathanxd.iutils.type.TypeParameterProvider;
import com.github.koresframework.eventsys.dispatcher.EventDispatcherKt;
import com.github.koresframework.eventsys.event.EventDispatcher;
import com.github.koresframework.eventsys.event.EventListener;
import com.github.koresframework.eventsys.event.EventListenerRegistry;
import com.github.koresframework.eventsys.extension.ExtensionSpecification;
import com.github.koresframework.eventsys.gen.check.CheckHandler;
import com.github.koresframework.eventsys.gen.check.SuppressCapableCheckHandler;
import com.github.koresframework.eventsys.gen.event.CommonEventGenerator;
import com.github.koresframework.eventsys.gen.event.EventGenerator;
import com.github.koresframework.eventsys.gen.event.EventGeneratorOptions;
import com.github.koresframework.eventsys.gen.event.LazyGenerationMode;
import com.github.koresframework.eventsys.impl.CommonEventDispatcher;
import com.github.koresframework.eventsys.impl.CommonEventManager;
import com.github.koresframework.eventsys.impl.CommonLogger;
import com.github.koresframework.eventsys.impl.PerChannelEventListenerRegistry;
import com.github.koresframework.eventsys.logging.LoggerInterface;
import com.github.koresframework.eventsys.test.event.MessageEvent;
import com.github.koresframework.eventsys.test.event.MyGenericEvent;
import com.github.koresframework.eventsys.test.extension.ProvidedExt;
import com.github.koresframework.eventsys.test.listener.MyListener;
import com.koresframework.kores.type.Generic;
import kotlin.Unit;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public class TestManager {

    @Test
    public void test() throws NoSuchMethodException, InterruptedException {
        MyManager manager = new MyManager();

        CheckHandler checkHandler = manager.getEventGenerator().getCheckHandler();

        manager.getEventGenerator().getOptions().set(EventGeneratorOptions.ENABLE_SUPPRESSION, true);
        manager.getEventGenerator().getOptions().set(EventGeneratorOptions.LAZY_EVENT_GENERATION_MODE,
                LazyGenerationMode.BOOTSTRAP);

        manager.getEventGenerator().registerExtension(MessageEvent.class,
                new ExtensionSpecification(Unit.INSTANCE, null, ProvidedExt.class));

        Class<? extends MyGenericEvent> eventClass = manager.getEventGenerator().<MyGenericEvent>createEventClass(
                Generic.type(MyGenericEvent.class).of(String.class),
                Collections.emptyList()).invoke();

        if (checkHandler instanceof SuppressCapableCheckHandler) {
            Description from = DescriptionUtil.from(MessageEvent.class.getDeclaredMethod("getTest", int.class));

            ((SuppressCapableCheckHandler) checkHandler).addSuppression(from);
        }

        Constant.initialize(manager);

        manager.getEventListenerRegistry().registerListeners(this, new MyListener());

        MessageEvent messageEvent = Constant.getMyFactoryInstance().createMessageEvent("HELLO WORLD", "[TAG] ");
        KtEvent ktEvent = Constant.getMyFactoryInstance().createKtEvent("ProjectSandstone");

        KtBridgeStringTest x = Constant.getMyFactoryInstance().createKtBridgeTestEvent("x");
        Assertions.assertEquals("x", x.getValue());
        Assertions.assertEquals("x", ((KtBridgeTest) x).getValue());

        manager.dispatchBlocking(messageEvent, this);
        manager.dispatchBlocking(ktEvent, this);

        ktEvent.reset();

        MyGenericEvent<String> a = Constant.getMyFactoryInstance()
                .createMyGenericEvent(new TypeParameterProvider<MyGenericEvent<String>>() {
                }.getType(), "A");
        MyGenericEvent<Integer> b = Constant.getMyFactoryInstance()
                .createMyGenericEvent(new TypeParameterProvider<MyGenericEvent<Integer>>() {
                }.getType(), 1);

        MyGenericEvent<Object> c = Constant.getMyFactoryInstance()
                .createMyGenericEvent(new TypeParameterProvider<MyGenericEvent<Object>>() {
                }.getType(), "Y");

        MyGenericEvent<Object> d = Constant.getMyFactoryInstance()
                .createMyGenericEvent(new TypeParameterProvider<MyGenericEvent<Object>>() {
                }.getType(), 7);

        manager.dispatchBlocking(a, this);
        manager.dispatchBlocking(b, this);
        manager.dispatchBlocking(c, this);
        manager.dispatchBlocking(d, this);

        manager.dispatchBlocking(c, new TypeParameterProvider<MyGenericEvent<String>>() {
        }.getType(), this);

        manager.dispatchBlocking(d, new TypeParameterProvider<MyGenericEvent<String>>() {
        }.getType(), this); // WRONG

        Constant.getMyFactoryInstance().createMyTestEvent("Cup", 100);
        Constant.getMyFactoryInstance().createMyTestEvent2("Cup", 100);

        Assertions.assertEquals("[TAG] hello world", messageEvent.getMessage());

        ProvidedExt extension = messageEvent.getExtension(ProvidedExt.class);

        Assertions.assertNotNull(extension);
        Assertions.assertEquals("_OK_", extension.getTest());

    }


    private static class MyManager extends CommonEventManager {

        static final ThreadFactory COMMON_THREAD_FACTORY = Executors.defaultThreadFactory();
        static final LoggerInterface COMMON_LOGGER = new CommonLogger();
        private static final Comparator<EventListener<?>> COMMON_SORTER = Comparator.comparing(EventListener::getPriority);
        private static final EventGenerator COMMON_EVENT_GENERATOR = new CommonEventGenerator(COMMON_LOGGER);
        private static final EventListenerRegistry COMMON_EVENT_LISTENER_REGISTRY =
                new PerChannelEventListenerRegistry(COMMON_SORTER, COMMON_LOGGER, COMMON_EVENT_GENERATOR);
        private static final EventDispatcher COMMON_DISPATCHER = new CommonEventDispatcher(
                COMMON_THREAD_FACTORY,
                COMMON_EVENT_GENERATOR,
                COMMON_LOGGER,
                EventDispatcherKt.EVENT_CONTEXT,
                COMMON_EVENT_LISTENER_REGISTRY
        );

        MyManager() {
            super(COMMON_EVENT_GENERATOR, COMMON_DISPATCHER, COMMON_EVENT_LISTENER_REGISTRY);
        }
    }


}
