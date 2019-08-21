package com.github.projectsandstone.eventsys.test.channel

import com.github.projectsandstone.eventsys.event.Event
import com.github.projectsandstone.eventsys.event.EventManager
import com.github.projectsandstone.eventsys.event.annotation.Filter
import com.github.projectsandstone.eventsys.event.annotation.Listener
import com.github.projectsandstone.eventsys.event.annotation.Name
import com.github.projectsandstone.eventsys.impl.DefaultEventManager
import com.github.projectsandstone.eventsys.util.createFactory
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
        eventManager.registerListeners(this, this)

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