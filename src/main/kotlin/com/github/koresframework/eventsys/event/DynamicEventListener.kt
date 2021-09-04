package com.github.koresframework.eventsys.event

import com.github.koresframework.eventsys.error.ExceptionListenError
import com.github.koresframework.eventsys.error.ListenError
import com.github.koresframework.eventsys.result.ListenResult

interface DynamicEventListener<in T: Event> : EventListener<T> {
    override suspend fun onEvent(event: T, dispatcher: Any): ListenResult {
        return try {
            val on = on(event, dispatcher)
            if (on is ListenResult) {
                on
            } else {
                ListenResult.Value(on)
            }
        } catch (e: Throwable) {
            ListenResult.Failed(ExceptionListenError(e))
        }
    }

    suspend fun on(event: T, dispatcher: Any): Any
}