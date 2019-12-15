/*
 *      EventSys - Event implementation generator written on top of Kores
 *
 *         The MIT License (MIT)
 *
 *      Copyright (c) 2019 ProjectSandstone <https://github.com/ProjectSandstone/EventSys>
 *      Copyright (c) contributors
 *
 *
 *      Permission is hereby granted, free of charge, to any person obtaining a copy
 *      of this software and associated documentation files (the "Software"), to deal
 *      in the Software without restriction, including without limitation the rights
 *      to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *      copies of the Software, and to permit persons to whom the Software is
 *      furnished to do so, subject to the following conditions:
 *
 *      The above copyright notice and this permission notice shall be included in
 *      all copies or substantial portions of the Software.
 *
 *      THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *      IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *      FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *      AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *      LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *      OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *      THE SOFTWARE.
 */
package com.github.koresframework.eventsys.impl

import com.github.koresframework.eventsys.event.EventDispatcher
import com.github.koresframework.eventsys.event.EventListener
import com.github.koresframework.eventsys.event.EventListenerRegistry
import com.github.koresframework.eventsys.event.EventManager
import com.github.koresframework.eventsys.gen.event.CommonEventGenerator
import com.github.koresframework.eventsys.gen.event.EventGenerator
import com.github.koresframework.eventsys.logging.Level
import com.github.koresframework.eventsys.logging.LoggerInterface
import com.github.koresframework.eventsys.logging.MessageType
import java.util.*
import java.util.concurrent.Executors

abstract class AbstractEventManager : EventManager {
}

/**
 * Common Event Manager implementation
 *
 * @param sorter Sort event listeners.
 * @param threadFactory Thread factory for async dispatch
 * @param logger Logger interface for error logging.
 * @param eventGenerator Event generator instance to generate listener methods.
 */
open class CommonEventManager(
        open val eventGenerator: EventGenerator,
        override val eventDispatcher: EventDispatcher,
        open val eventListenerRegistry: EventListenerRegistry
) : AbstractEventManager()

class DefaultEventManager @JvmOverloads constructor(
        override val eventListenerRegistry: EventListenerRegistry = COMMON_EVENT_LISTENER_REGISTRY_FACTORY()
) : CommonEventManager(COMMON_EVENT_GENERATOR,
        CommonEventDispatcher(
                COMMON_THREAD_FACTORY,
                COMMON_EVENT_GENERATOR,
                COMMON_LOGGER,
                eventListenerRegistry
        ), eventListenerRegistry) {

    companion object {
        private val COMMON_SORTER = Comparator.comparing(EventListener<*>::priority)
        private val COMMON_THREAD_FACTORY = Executors.defaultThreadFactory()
        private val COMMON_LOGGER = CommonLogger()
        private val COMMON_EVENT_GENERATOR = CommonEventGenerator(COMMON_LOGGER)
        private val COMMON_EVENT_LISTENER_REGISTRY_FACTORY = {
            PerChannelEventListenerRegistry(
                    COMMON_SORTER,
                    COMMON_LOGGER,
                    COMMON_EVENT_GENERATOR
            )
        }
    }
}

class CommonLogger : LoggerInterface {
    override fun log(messages: List<String>, messageType: MessageType) {
        log(messages.joinToString("\n"), messageType)
    }

    override fun log(messages: List<String>, messageType: MessageType, throwable: Throwable) {
        log(messages.joinToString("\n"), messageType, throwable)
    }

    override fun log(message: String, messageType: MessageType) {
        if (messageType.level == Level.FATAL) {
            throw IllegalStateException("Fatal error occurred: $message")
        } else {
            System.err.println(message)
        }
    }

    override fun log(message: String, messageType: MessageType, throwable: Throwable) {
        System.err.println(message)

        if (messageType.level == Level.FATAL) {
            throw throwable
        } else {
            throwable.printStackTrace()
        }
    }
}
