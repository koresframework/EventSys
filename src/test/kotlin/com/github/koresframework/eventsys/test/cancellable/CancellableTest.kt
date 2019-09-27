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
package com.github.koresframework.eventsys.test.cancellable

import com.github.koresframework.eventsys.event.Cancellable
import com.github.koresframework.eventsys.event.Event
import com.github.koresframework.eventsys.event.EventPriority
import com.github.koresframework.eventsys.event.annotation.CancelAffected
import com.github.koresframework.eventsys.event.annotation.Listener
import com.github.koresframework.eventsys.event.annotation.Name
import com.github.koresframework.eventsys.impl.DefaultEventManager
import com.github.koresframework.eventsys.result.ListenResult
import com.github.koresframework.eventsys.util.createFactory
import org.junit.Assert
import org.junit.Before
import org.junit.Test

class CancellableTest {
    var call = 0
    var call2 = 0
    var call3 = 0
    var call4 = 0

    @Before
    fun setup() {
        call = 0
        call2 = 0
        call3 = 0
        call4 = 0
    }

    @Test
    fun cancellable() {
        val eventManager = DefaultEventManager()
        eventManager.registerListeners(this, this)

        val user = User(id = 0, name = "Test", email = "test@test.com")
        val factory = eventManager.eventGenerator.createFactory<EventFactory>().resolve()

        eventManager.dispatch(factory.createUserRegisterEvent(user), this)

        Assert.assertEquals(1, this.call)
        Assert.assertEquals(0, this.call2)
        Assert.assertEquals(1, this.call3)
        Assert.assertEquals(0, this.call4)
    }


    @Test
    fun cancellableAsync() {
        val eventManager = DefaultEventManager()
        eventManager.registerListeners(this, this)

        val user = User(id = 0, name = "Test", email = "test@test.com")
        val factory = eventManager.eventGenerator.createFactory<EventFactory>().resolve()

        val dispatchResult = eventManager.dispatchAsync(factory.createUserRegisterEvent(user), this)

        val results = dispatchResult.toCompletable().join()

        val distinctBy = results.distinctBy { it.eventListenerContainer.eventListener }

        Assert.assertEquals(1, this.call)
        Assert.assertEquals(1, this.call2)
        Assert.assertEquals(1, this.call3)
        Assert.assertEquals(1, this.call4)
        Assert.assertEquals(4, distinctBy.size)
        Assert.assertTrue(distinctBy.all { it.result is ListenResult.Value })
        Assert.assertEquals(listOf(true, true, true, true), distinctBy.map { (it.result as ListenResult.Value).value }.toList())
    }

    interface EventFactory {
        fun createUserRegisterEvent(@Name("user") user: User): UserRegisterEvent
    }

    @Listener(priority = EventPriority.FIRST)
    fun onUserRegister(userRegisterEvent: UserRegisterEvent): Boolean {
        call++
        userRegisterEvent.isCancelled = true
        return true
    }

    @Listener
    @CancelAffected
    fun onUserRegister2(userRegisterEvent: UserRegisterEvent): Boolean {
        call2++
        return true
    }

    @Listener(priority = EventPriority.HIGH)
    fun onUserRegister3(userRegisterEvent: UserRegisterEvent): Boolean {
        call3++
        userRegisterEvent.isCancelled = false
        return true
    }

    @Listener(priority = EventPriority.LAST)
    @CancelAffected
    fun onUserRegister4(userRegisterEvent: UserRegisterEvent): Boolean {
        call4++
        return true
    }

    interface UserRegisterEvent : Event, Cancellable {
        val user: User
    }
}

data class User(val id: Int, val name: String, val email: String)