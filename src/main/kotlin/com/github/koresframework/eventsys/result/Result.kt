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

