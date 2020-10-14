package com.github.koresframework.eventsys.test

import com.github.koresframework.eventsys.impl.DefaultEventManager
import com.github.koresframework.eventsys.test.factory.HyperEventFactory
import com.github.koresframework.eventsys.test.hyper.HyperManager
import com.github.koresframework.eventsys.util.createFactory
import org.junit.Test

class HyperTest {

    @Test
    fun hyperTest() {
        val em = DefaultEventManager()
        val factory = em.eventGenerator.createFactory<HyperEventFactory>()

        val element = "Heyo"
        val manager = HyperMng()
        val event = factory.resolver().createHyperEvent(element, manager)
        em.dispatch(event, this)

    }

    class HyperMng : HyperManager<String, HyperMng> {

    }
}