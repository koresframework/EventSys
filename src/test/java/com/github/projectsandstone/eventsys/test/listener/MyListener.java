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
package com.github.projectsandstone.eventsys.test.listener;

import com.github.projectsandstone.eventsys.event.EventPriority;
import com.github.projectsandstone.eventsys.event.annotation.Erased;
import com.github.projectsandstone.eventsys.event.annotation.Listener;
import com.github.projectsandstone.eventsys.event.annotation.Name;
import com.github.projectsandstone.eventsys.test.event.MessageEvent;
import com.github.projectsandstone.eventsys.test.event.MyGenericEvent;

public class MyListener {

    @Listener
    public void listen(MessageEvent messageEvent) {
        messageEvent.transform(String::toLowerCase);
    }

    @Listener
    public void listen(MyGenericEvent<String> event) {
        String obj = event.getObj();

        System.out.println(obj);
    }

    @Listener
    public void listen2(MyGenericEvent<Integer> event) {
        Integer obj = event.getObj();

        System.out.println("i" + obj);
    }

    @Listener
    public void listen2(MyGenericEvent<Integer> event, @Name("obj") Integer i) {
        System.out.println("i: " + i);
    }

    @Listener(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void listen3(MyGenericEvent<Integer> event, @Name("obj") Integer i) {
        System.out.println("i: " + i);
    }

    @Listener
    public void listen4(MyGenericEvent<String> event, @Name("obj") @Erased String s) {
        System.out.println("str: " + s+"m ev: "+event.getClass());
    }
}
