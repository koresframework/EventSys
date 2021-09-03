package com.github.koresframework.eventsys.dispatcher

import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors
import kotlin.coroutines.CoroutineContext

@JvmField
val EVENT_DISPATCHER = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2).asCoroutineDispatcher()

@JvmField
val EVENT_CONTEXT: CoroutineContext = EVENT_DISPATCHER