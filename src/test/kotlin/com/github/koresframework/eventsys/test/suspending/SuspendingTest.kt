package com.github.koresframework.eventsys.test.suspending

import com.github.koresframework.eventsys.event.Event
import com.github.koresframework.eventsys.event.annotation.Listener
import com.github.koresframework.eventsys.event.annotation.Name
import com.github.koresframework.eventsys.impl.DefaultEventManager
import com.github.koresframework.eventsys.result.ListenResult
import com.github.koresframework.eventsys.util.createFactory
import com.koresframework.kores.type.typeOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class SuspendingTest {

    @Test
    fun suspending() {
        val em = DefaultEventManager()
        val factory = em.eventGenerator.createFactory<MyFactory>()()
        val envt = factory.createMyMessage("Test")
        em.eventListenerRegistry.registerListeners(this, MyListener())

        val r = em.dispatchAsyncBlocking(envt, typeOf<MyMessage>())
        runBlocking {
            r.await()
        }

        val maximum = max.get()
        Assert.assertEquals(2, maximum)
    }

}

val max = AtomicInteger()
val current = AtomicInteger()

fun inc() {
    current.incrementAndGet()
    max.updateAndGet {
        it.coerceAtLeast(current.get())
    }
}

fun dec() {
    current.decrementAndGet()
}

class MyListener {

    @Listener
    suspend fun onMessage(event: MyMessage): Any {
        println("Event: $event")
        inc()
        delay(1200)
        dec()
        println("Event: $event -> End")
        return ListenResult.Value(Unit)
    }

    @Listener
    suspend fun onMessage2(event: MyMessage): Any {
        println("Event 2: $event")
        inc()
        delay(1500)
        dec()
        println("Event 2: $event -> End")
        return ListenResult.Value(Unit)
    }

}

interface MyFactory {
    fun createMyMessage(@Name("text") text: String): MyMessage
}

interface MyMessage : Event {
    val text: String
}