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
package com.github.koresframework.eventsys.test.channel

import com.github.koresframework.eventsys.event.Event
import com.github.koresframework.eventsys.event.EventManager
import com.github.koresframework.eventsys.event.annotation.Filter
import com.github.koresframework.eventsys.event.annotation.Listener
import com.github.koresframework.eventsys.event.annotation.Name
import com.github.koresframework.eventsys.impl.DefaultEventManager
import com.github.koresframework.eventsys.util.createFactory
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import kotlin.reflect.typeOf

class ChannelTest {
    var calls = 0

    @Before
    fun setup() {
        calls = 0
    }

    @Test
    fun channel() {
        val eventManager = DefaultEventManager()
        eventManager.eventListenerRegistry.registerListeners(this, this)

        val user = User(id = 0, name = "Test", email = "test@test.com")
        val factory = eventManager.eventGenerator.createFactory<EventFactory>().resolve()

        eventManager.dispatch(factory.createUserRegisterEvent(user), this, "user")

        Assert.assertEquals(1, this.calls)

        eventManager.dispatch(factory.createUserRegisterEvent(user), this, "other")

        Assert.assertEquals(1, this.calls)
    }

    interface EventFactory {
        fun createUserRegisterEvent(@Name("user") user: User): UserRegisterEvent
    }

    @Filter(UserRegisterEvent::class)
    @Listener(channel = "user")
    fun onUserRegister(user: User) {
        calls++
    }

    interface UserRegisterEvent : Event {
        val user: User
    }
}

data class User(val id: Int, val name: String, val email: String)