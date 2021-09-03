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

import com.github.koresframework.eventsys.channel.ChannelSet;
import com.github.koresframework.eventsys.context.EnvironmentContext;
import com.github.koresframework.eventsys.event.ChannelEventListenerRegistry;
import com.github.koresframework.eventsys.event.Event;
import com.github.koresframework.eventsys.event.EventListener;
import com.github.koresframework.eventsys.event.EventListenerRegistry;
import com.github.koresframework.eventsys.event.ListenerRegistryResults;
import com.github.koresframework.eventsys.impl.CommonEventManager;
import com.github.koresframework.eventsys.impl.EventListenerContainer;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import kotlin.Pair;

public class DistributedRegistry implements ChannelEventListenerRegistry {
    private final List<ChannelEventListenerRegistry> channelEventListenerRegistries;

    public DistributedRegistry(List<CommonEventManager> eventManagers) {
        this.channelEventListenerRegistries = eventManagers.stream()
                .map(it -> (ChannelEventListenerRegistry) it.getEventListenerRegistry())
                .collect(Collectors.toList());
    }


    @Override
    public <T extends Event> ListenerRegistryResults registerListener(@NotNull Object owner,
                                                                      @NotNull Type eventType,
                                                                      @NotNull EventListener<? super T> eventListener) {
        return new ListenerRegistryResults(
                this.channelEventListenerRegistries.stream()
                        .flatMap(it -> it.registerListener(owner, eventType, eventListener)
                                .getResults().stream())
                        .collect(Collectors.toList())
        );
    }

    @Override
    public ListenerRegistryResults registerListeners(@NotNull Object owner,
                                                     @NotNull Object listener,
                                                     @NotNull EnvironmentContext ctx) {
        return new ListenerRegistryResults(
                this.channelEventListenerRegistries.stream()
                        .flatMap(it -> it.registerListeners(owner, listener, ctx)
                                .getResults().stream())
                        .collect(Collectors.toList())
        );
    }

    @Override
    public ListenerRegistryResults registerMethodListener(@NotNull Object owner,
                                                          @NotNull Type eventClass,
                                                          @Nullable Object instance,
                                                          @NotNull Method method,
                                                          @NotNull EnvironmentContext ctx) {
        return new ListenerRegistryResults(
                this.channelEventListenerRegistries.stream()
                        .flatMap(it -> it.registerMethodListener(owner, eventClass, instance, method, ctx)
                                .getResults().stream())
                        .collect(Collectors.toList())
        );
    }

    @NotNull
    @Override
    public <T extends Event> Set<Pair<Type, EventListener<T>>> getListeners(@NotNull Type eventType) {
        return this.channelEventListenerRegistries.stream()
                .flatMap(it -> it.<T>getListeners(eventType).stream())
                .collect(Collectors.toSet());

    }

    @NotNull
    @Override
    public <T extends Event> Set<EventListenerContainer<?>> getListenersContainers(@NotNull Type eventType) {
        return this.channelEventListenerRegistries.stream()
                .flatMap(it -> it.<T>getListenersContainers(eventType).stream())
                .collect(Collectors.toSet());
    }

    @NotNull
    @Override
    public <T extends Event> Iterable<EventListenerContainer<?>> getListenersContainers(@NotNull T event,
                                                                                        @NotNull Type eventType,
                                                                                        @NotNull String channel) {
        return this.channelEventListenerRegistries.stream()
                .flatMap(it -> it.<T>getListenersContainers(eventType).stream())
                .collect(Collectors.toSet());
    }

    @NotNull
    @Override
    public Set<EventListenerContainer<?>> getListenersContainers() {
        return this.channelEventListenerRegistries.stream()
                .flatMap(it -> it.getListenersContainers().stream())
                .collect(Collectors.toSet());
    }

    @NotNull
    @Override
    public Set<Pair<Type, EventListener<?>>> getListenersAsPair() {
        return this.channelEventListenerRegistries.stream()
                .flatMap(it -> it.getListenersAsPair().stream())
                .collect(Collectors.toSet());
    }

    @NotNull
    @Override
    public ChannelSet getChannels() {
        boolean anyAll = this.channelEventListenerRegistries.stream()
                .anyMatch(it -> ChannelSet.isAll(it.getChannels()));

        return anyAll
                ? ChannelSet.ALL
                : ChannelSet.include(this.channelEventListenerRegistries.stream()
                .flatMap(it -> it.getChannels().toSet().stream())
                .collect(Collectors.joining()));
    }

    @NotNull
    @Override
    public ListenerRegistryResults registerListeners(@NotNull Object owner, @NotNull Object listener) {
        return ChannelEventListenerRegistry.super.registerListeners(owner, listener);
    }

    @NotNull
    @Override
    public ListenerRegistryResults registerMethodListener(@NotNull Object owner, @NotNull Type eventClass, @NotNull Object instance, @NotNull Method method) {
        return ChannelEventListenerRegistry.super.registerMethodListener(owner, eventClass, instance, method);
    }
}
