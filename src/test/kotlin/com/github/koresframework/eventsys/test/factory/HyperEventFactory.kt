package com.github.koresframework.eventsys.test.factory

import com.github.koresframework.eventsys.test.hyper.HyperEvent
import com.github.koresframework.eventsys.test.hyper.HyperManager

interface HyperEventFactory {

    fun <E, M: HyperManager<E, M>> createHyperEvent(element: E, manager: M): HyperEvent<E, M>

}