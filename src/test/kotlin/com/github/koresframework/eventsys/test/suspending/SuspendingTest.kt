package com.github.koresframework.eventsys.test.suspending

import com.github.koresframework.eventsys.event.Event
import com.github.koresframework.eventsys.event.annotation.Listener
import com.github.koresframework.eventsys.event.annotation.Name
import com.github.koresframework.eventsys.impl.DefaultEventManager
import com.github.koresframework.eventsys.result.ListenResult
import com.github.koresframework.eventsys.util.createFactory
import com.koresframework.kores.type.typeOf
import kotlinx.coroutines.delay
import org.junit.Test

class SuspendingTest {

    @Test
    fun suspending() {
        val em = DefaultEventManager()
        val factory = em.eventGenerator.createFactory<MyFactory>()()
        val envt = factory.createMyMessage("Test")
        em.eventListenerRegistry.registerListeners(this, MyListener())

        em.dispatchBlocking(envt, typeOf<MyMessage>())
    }

}

class MyListener {

    @Listener
    suspend fun onMessage(event: MyMessage): Any {
        println("Event: $event")
        delay(100)
        return ListenResult.Value(Unit)
    }

}

interface MyFactory {
    fun createMyMessage(@Name("text") text: String): MyMessage
}

interface MyMessage : Event {
    val text: String
}