package com.github.koresframework.eventsys.impl

import com.github.koresframework.eventsys.event.EventListener
import com.github.koresframework.eventsys.event.EventPriority

class EventListenerContainerComparator(val other: Comparator<EventListener<*>>) : Comparator<EventListenerContainer<*>> {
    override fun compare(f: EventListenerContainer<*>, s: EventListenerContainer<*>): Int =
        Comparator.comparing<EventListenerContainer<*>, EventListener<*>>({ it.eventListener }, other)
            .thenComparing { o1, o2 -> o1.isSuperTypeOf(o2.eventType).compareTo(o2.isSuperTypeOf(o1.eventType)) }
            .thenComparing { o1, o2 -> o1.hashCode().compareTo(o2.hashCode()) }
            .thenComparing { o1, o2 -> if (o1.equals(o2)) 0 else -1 }
            .compare(f, s)
}