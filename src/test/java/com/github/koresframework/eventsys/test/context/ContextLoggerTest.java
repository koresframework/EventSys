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
package com.github.koresframework.eventsys.test.context;

import com.github.jonathanxd.iutils.object.TypedKey;
import com.github.jonathanxd.iutils.type.TypeInfo;
import com.github.koresframework.eventsys.context.EnvironmentContext;
import com.github.koresframework.eventsys.event.EventListenerRegistry;
import com.github.koresframework.eventsys.event.EventManager;
import com.github.koresframework.eventsys.event.annotation.Listener;
import com.github.koresframework.eventsys.gen.event.CommonEventGenerator;
import com.github.koresframework.eventsys.gen.event.EventGenerator;
import com.github.koresframework.eventsys.impl.CommonLogger;
import com.github.koresframework.eventsys.impl.DefaultEventManager;
import com.github.koresframework.eventsys.impl.SharedSetChannelEventListenerRegistry;
import com.github.koresframework.eventsys.logging.LoggerInterface;
import com.github.koresframework.eventsys.logging.MessageType;
import com.github.koresframework.eventsys.result.DispatchResult;
import com.github.koresframework.eventsys.sorter.EventPrioritySorter;

import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.ExecutionException;

public class ContextLoggerTest {

    private static final TypedKey<String> ORIGIN = new TypedKey<>("ORIGIN", TypeInfo.of(String.class));
    private int dispatch;
    private String origin;

    @Before
    public void setup() {
        this.dispatch = 0;
        this.origin = null;
    }

    @Test
    public void testLoggerContext() {
        LoggerInterface loggerInterface = new LI();
        EventGenerator eventGenerator = new CommonEventGenerator(loggerInterface);

        EventListenerRegistry eventListenerRegistry =
                new SharedSetChannelEventListenerRegistry(
                        EventPrioritySorter.anySorter(),
                        loggerInterface,
                        eventGenerator
                );

        EventManager eventManager = new DefaultEventManager(eventListenerRegistry);

        eventListenerRegistry.registerListeners(this, this);

        EnvironmentContext ctx = new EnvironmentContext();
        ORIGIN.set(ctx, "main");
        try {
            MyEventFactoryForCtx factory = eventGenerator.<MyEventFactoryForCtx>createFactory(MyEventFactoryForCtx.class, ctx)
                    .getResolver()
                    .invoke();

            MessageSendEvent messageSendEvent =
                    factory.messageSendEvent("0.0.0.0", "Hello");


            DispatchResult<MessageSendEvent> dispatch = eventManager.dispatchBlocking(messageSendEvent, MessageSendEvent.class, this);
            dispatch.awaitBlocking();

            Assert.assertEquals(1, this.dispatch);
            Assert.assertNull(this.origin);

            dispatch = eventManager.dispatchBlocking(messageSendEvent, MessageSendEvent.class, this, ctx);
            dispatch.awaitBlocking();
        } catch (IllegalStateException e) {
            Assert.assertEquals("Fatal error occurred: Factory class must be an interface.", e.getMessage());
        }
        Assert.assertEquals(0, this.dispatch);
        Assert.assertEquals("main", this.origin);
    }

    @Listener
    public void listen(MessageSendEvent e) {
        ++dispatch;
        throw new IllegalArgumentException("Fail");
    }


    public class LI implements LoggerInterface {

        private final LoggerInterface backedLogger = new CommonLogger();

        @Override
        public void log(@NotNull String message,
                        @NotNull MessageType messageType,
                        @NotNull EnvironmentContext ctx) {
            ContextLoggerTest.this.origin = ORIGIN.getOrElse(ctx, null);
            this.backedLogger.log(message, messageType, ctx);
        }

        @Override
        public void log(@NotNull String message,
                        @NotNull MessageType messageType,
                        @NotNull Throwable throwable,
                        @NotNull EnvironmentContext ctx) {
            ContextLoggerTest.this.origin = ORIGIN.getOrElse(ctx, null);
            this.backedLogger.log(message, messageType, throwable, ctx);
        }

        @Override
        public void log(@NotNull List<String> messages,
                        @NotNull MessageType messageType,
                        @NotNull EnvironmentContext ctx) {
            ContextLoggerTest.this.origin = ORIGIN.getOrElse(ctx, null);
            this.backedLogger.log(messages, messageType, ctx);
        }

        @Override
        public void log(@NotNull List<String> messages,
                        @NotNull MessageType messageType,
                        @NotNull Throwable throwable,
                        @NotNull EnvironmentContext ctx) {
            ContextLoggerTest.this.origin = ORIGIN.getOrElse(ctx, null);
            this.backedLogger.log(messages, messageType, throwable, ctx);
        }
    }
}
