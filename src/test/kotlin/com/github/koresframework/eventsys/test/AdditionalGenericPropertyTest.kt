package com.github.koresframework.eventsys.test

import com.github.koresframework.eventsys.event.Event
import com.github.koresframework.eventsys.impl.DefaultEventManager
import com.github.koresframework.eventsys.util.createFactory
import org.junit.Test

class AdditionalGenericPropertyTest {
    @Test
    fun hyperTest() {
        val em = DefaultEventManager()
        val factory = em.eventGenerator.createFactory<TeeFactory>()

        val element = "Heyo"
        val event = factory.resolver().createTee(element, TManager())
        em.dispatch(event, this)

    }

    class TManager : TeeManager<String, TManager>

}

interface TeeManager<E, M: TeeManager<E, M>> {

}

interface Tee<E, M: TeeManager<E, M>> : Event {
    val value: E
}

interface TeeFactory {
    fun <E, M : TeeManager<E, M>> createTee(value: E, k: M): Tee<E, M>
}
