/*
 *      EventSys - Event implementation generator written on top of Kores
 *
 *         The MIT License (MIT)
 *
 *      Copyright (c) 2019 ProjectSandstone <https://github.com/ProjectSandstone/EventSys>
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
package com.github.koresframework.eventsys.test.factory;

import com.github.jonathanxd.iutils.type.TypeInfo;
import com.github.jonathanxd.kores.type.GenericType;
import com.github.koresframework.eventsys.event.annotation.Extension;
import com.github.koresframework.eventsys.event.annotation.LazyGeneration;
import com.github.koresframework.eventsys.event.annotation.Name;
import com.github.koresframework.eventsys.event.annotation.TypeParam;
import com.github.koresframework.eventsys.test.KtBridgeStringTest;
import com.github.koresframework.eventsys.test.KtEvent;
import com.github.koresframework.eventsys.test.event.BridgeTestEvent;
import com.github.koresframework.eventsys.test.event.MessageEvent;
import com.github.koresframework.eventsys.test.event.MyGenericEvent;
import com.github.koresframework.eventsys.test.event.MyTestEvent;
import com.github.koresframework.eventsys.test.extension.MyExtension;
import com.github.koresframework.eventsys.test.extension.PrefixHolder;

import java.lang.reflect.Type;

public interface MyFactory {

    @Extension(extensionClass = MyExtension.class, implement = PrefixHolder.class)
    MessageEvent createMessageEvent(@Name("message") String message, @Name("prefix") String prefix);

    KtEvent createKtEvent(@Name("name") String name);

    @LazyGeneration
    <T> MyGenericEvent<T> createMyGenericEvent(@TypeParam Type type,
                                               @Name("obj") T obj);

    <T> MyGenericEvent<T> createMyGenericEventGeneric(@Name("obj") T obj);

    MyTestEvent createMyTestEvent(@Name("name") String name, @Name("amount") int amount);

    @LazyGeneration
    MyTestEvent createMyTestEvent2(@Name("name") String name, @Name("amount") int amount);

    BridgeTestEvent createBridgeTestEvent(@Name("obj") String obj);

    KtBridgeStringTest createKtBridgeTestEvent(@Name("value") String value);
}
