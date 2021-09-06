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
package com.github.koresframework.eventsys.test.channel

import com.github.koresframework.eventsys.event.Event
import com.github.koresframework.eventsys.event.annotation.Filter
import com.github.koresframework.eventsys.event.annotation.Listener
import com.github.koresframework.eventsys.event.annotation.Name
import com.github.koresframework.eventsys.impl.DefaultEventManager
import com.github.koresframework.eventsys.util.createFactory
import com.koresframework.kores.type.typeOf
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

object ChannelTest3Context {
    val calls = AtomicInteger()
    val broadCalls = AtomicInteger()

    @Filter(UserRegisterEvent::class)
    @Listener(channel = "user")
    fun onUserRegister(user: User) {
        calls.incrementAndGet()
    }

    @Filter(UserRegisterEvent::class)
    @Listener(channel = "user,broadcast")
    fun onUserRegistered(user: User) {
        broadCalls.incrementAndGet()
    }

    interface EventFactory {
        fun createUserRegisterEvent(@Name("user") user: User): UserRegisterEvent
    }

    interface UserRegisterEvent : Event {
        val user: User
    }
}

class ChannelTest3 : FunSpec({

    beforeEach { ChannelTest3Context.calls.set(0) }

    test("proper channel dispatch") {
        val eventManager = DefaultEventManager()
        eventManager.eventListenerRegistry.registerFunctionListener(
            this,
            typeOf<ChannelTest3Context>(),
            ChannelTest3Context,
            ChannelTest3Context::onUserRegister,
        )

        eventManager.eventListenerRegistry.registerFunctionListener(
            this,
            typeOf<ChannelTest3Context>(),
            ChannelTest3Context,
            ChannelTest3Context::onUserRegistered,
        )

        val user = User(id = 0, name = "Test", email = "test@test.com")
        val factory = eventManager.eventGenerator.createFactory<ChannelTest3Context.EventFactory>().resolve()

        eventManager.dispatch(factory.createUserRegisterEvent(user), this, "user").await()

        ChannelTest3Context.calls.get().shouldBe(1)
        ChannelTest3Context.broadCalls.get().shouldBe(1)

        eventManager.dispatch(factory.createUserRegisterEvent(user), this, "other").await()

        ChannelTest3Context.calls.get().shouldBe(1)
        ChannelTest3Context.broadCalls.get().shouldBe(1)

        eventManager.dispatch(factory.createUserRegisterEvent(user), this, "user,broadcast").await()

        ChannelTest3Context.calls.get().shouldBe(2)
        ChannelTest3Context.broadCalls.get().shouldBe(2)

        eventManager.dispatch(factory.createUserRegisterEvent(user), this, "!other").await()

        ChannelTest3Context.calls.get().shouldBe(3)
        ChannelTest3Context.broadCalls.get().shouldBe(3)
    }
})