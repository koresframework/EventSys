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
package com.github.koresframework.eventsys.result

import com.github.koresframework.eventsys.error.ListenError
import com.github.koresframework.eventsys.impl.EventListenerContainer
import java.lang.reflect.Type
import java.util.concurrent.CompletableFuture

/**
 * A data class containing all listen execution results.
 */
data class DispatchResult<T>(val listenExecutionResults: List<CompletableFuture<ListenExecutionResult<T>>>) {

    /**
     * Subscribe all listener execution result completable future.
     */
    fun subscribeAll(onComplete: (ListenExecutionResult<T>, Throwable) -> Unit) =
            this.listenExecutionResults.forEach {
                it.whenComplete { result, throwable ->
                    onComplete(result, throwable)
                }
            }

    /**
     * Transform into a [CompletableFuture] that completes when all [listenExecutionResults] completes.
     */
    fun toCompletable() = CompletableFuture.allOf(*this.listenExecutionResults.toTypedArray())
            .thenApply {
                listenExecutionResults.map { it.join() }
            }

    /**
     * Returns a new [DispatchResult] which combines results of this data object and results of [other].
     */
    fun combine(other: DispatchResult<T>): DispatchResult<T> =
            DispatchResult(this.listenExecutionResults + other.listenExecutionResults)
}

/**
 * A data class containing all information about listener execution.
 *
 * @property eventListenerContainer Event listener contains.
 * @property event Dispatched event instance.
 * @property eventType Type provided during dispatch process (or inferred).
 * @property dispatcher The dispatcher of event.
 * @property channel Channel that event was dispatched to.
 * @property result Result of event dispatch.
 */
data class ListenExecutionResult<T>(
        val eventListenerContainer: EventListenerContainer<*>,
        val event: T,
        val eventType: Type,
        val dispatcher: Any,
        val channel: String,
        val result: ListenResult
)

/**
 * Result of listen function.
 */
sealed class ListenResult {
    /**
     * Result in a [value].
     */
    data class Value(val value: Any) : ListenResult()

    /**
     * Result in [error].
     */
    data class Failed(val error: ListenError) : ListenResult()
}

/**
 * Creates a success result with a meaningless value.
 */
fun success() = ListenResult.Value(Unit)

/**
 * Creates a success result with meaningful [value].
 */
fun <T : Any> success(value: T) = ListenResult.Value(value)

/**
 * Creates a failure result with [error].
 */
fun failed(error: ListenError) = ListenResult.Failed(error)

