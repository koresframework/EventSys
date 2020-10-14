package com.github.koresframework.eventsys.test.hyper

import com.github.koresframework.eventsys.event.Event
import com.github.koresframework.eventsys.event.property.Property

interface HyperEvent<E, M: HyperManager<E, M>> : Event {
    val element: E
    val manager: M
}

interface HyperManager<E, M: HyperManager<E, M>>

class HyperEventImpl<E, M: HyperManager<E, M>>(override val element: E, override val manager: M) : HyperEvent<E, M> {

    override fun getProperties(): Map<String, Property<*>> {
        TODO("Not yet implemented")
    }

}