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
package com.github.koresframework.eventsys.test.distributed.channel;

import com.github.jonathanxd.iutils.collection.Collections3;
import com.github.koresframework.eventsys.channel.ChannelSet;
import com.github.koresframework.eventsys.context.EnvironmentContext;
import com.github.koresframework.eventsys.dispatcher.EventDispatcherKt;
import com.github.koresframework.eventsys.event.EventListener;
import com.github.koresframework.eventsys.event.*;
import com.github.koresframework.eventsys.event.annotation.Listener;
import com.github.koresframework.eventsys.gen.event.CommonEventGenerator;
import com.github.koresframework.eventsys.gen.event.EventGenerator;
import com.github.koresframework.eventsys.impl.*;
import com.github.koresframework.eventsys.logging.LoggerInterface;
import com.github.koresframework.eventsys.result.DispatchResult;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.Executors;

public class DistributedChannelTest {

    private LoggerInterface loggerInterface;
    private EventGenerator eg;

    private int withdrawCalled = 0;
    private int depositCalled = 0;

    @BeforeEach
    public void setup() {
        this.loggerInterface = new CommonLogger();
        this.eg = new CommonEventGenerator(loggerInterface);
        this.withdrawCalled = 0;
        this.depositCalled = 0;
    }

    @Test
    public void distributedTest() {
        DistributedEventFactory factory = eg.<DistributedEventFactory>createFactory(DistributedEventFactory.class).resolve();

        CommonEventManager withdrawChannelEventManager = this.createManagerForChannels(Collections3.setOf("withdraw"));
        CommonEventManager depositChannelEventManager = this.createManagerForChannels(Collections3.setOf("deposit"));

        DistributedRegistry distributedRegistry
                = new DistributedRegistry(Arrays.asList(withdrawChannelEventManager, depositChannelEventManager));

        DistributedEventManager manager =
                new DistributedEventManager(new DistributedEventDispatcher(Arrays.asList(
                        withdrawChannelEventManager,
                        depositChannelEventManager
                )));

        distributedRegistry.registerListeners(this, this);

        manager.dispatchBlocking(factory.bankAccountMoneyChangeEvent(-5), this, "withdraw");

        Assertions.assertEquals(1, this.withdrawCalled);
        Assertions.assertEquals(0, this.depositCalled);

        manager.dispatchBlocking(factory.bankAccountMoneyChangeEvent(500), this, "deposit");
        Assertions.assertEquals(1, this.withdrawCalled);
        Assertions.assertEquals(1, this.depositCalled);

        manager.dispatchBlocking(factory.bankAccountMoneyChangeEvent(500), this, ChannelSet.Expression.ALL);

        Assertions.assertEquals(2, this.withdrawCalled);
        Assertions.assertEquals(2, this.depositCalled);
    }

    @Listener(channel = "withdraw")
    public void withdraw(BankAccountMoneyChangeEvent event) {
        this.withdrawCalled++;
    }

    @Listener(channel = "deposit")
    public void deposit(BankAccountMoneyChangeEvent event) {
        this.depositCalled++;
    }

    private CommonEventManager createManagerForChannels(Set<String> channels) {
        ChannelEventListenerRegistry registry = new CommonChannelEventListenerRegistry(
                ChannelSet.include(channels),
                Comparator.comparing(EventListener::getPriority),
                loggerInterface,
                eg
        );

        EventDispatcher ed = new CommonChannelEventDispatcher(eg,
                Executors.newSingleThreadExecutor(),
                loggerInterface,
                EventDispatcherKt.EVENT_CONTEXT,
                registry
        );

        return new CommonEventManager(eg,
                ed,
                registry
        );
    }

    static class DistributedEventManager extends AbstractEventManager {
        private final DistributedEventDispatcher dispatcher;

        DistributedEventManager(DistributedEventDispatcher dispatcher) {
            this.dispatcher = dispatcher;
        }

        @NotNull
        @Override
        public EventDispatcher getEventDispatcher() {
            return this.dispatcher;
        }

    }

    static class DistributedEventDispatcher implements BlockingEventDispatcher {
        private final List<EventManager> eventManagers;

        DistributedEventDispatcher(List<EventManager> eventManagers) {
            this.eventManagers = eventManagers;
        }

        @NotNull
        @Override
        public <T extends Event> DispatchResult<T> dispatchBlocking(@NotNull T event,
                                                            @NotNull Type eventType,
                                                            @NotNull Object dispatcher,
                                                            @NotNull String channel,
                                                            boolean isAsync,
                                                            @NotNull EnvironmentContext ctx) {
            DispatchResult<T> result = new DispatchResult<>(EventDispatcherKt.EVENT_CONTEXT, Collections.emptyList());
            for (EventManager eventManager : this.eventManagers) {
                DispatchResult<T> currentResult =
                        EventManagerKt.dispatchBlocking(eventManager.getEventDispatcher(), event, eventType, dispatcher, channel, isAsync, ctx);

                result = result.combine(currentResult); // TODO: Mutable list
            }

            return result;
        }

    }
}
