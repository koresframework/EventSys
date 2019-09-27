package com.github.koresframework.eventsys.error

import java.lang.reflect.Type

/**
 * Listening error
 */
interface ListenError

/**
 * Exception occurred during listening process.
 */
class ExceptionListenError(val exception: Throwable) : ListenError

/**
 * A required property was not found for the listener be invoke.
 */
class PropertyNotFoundError(val name: String, val type: Type) : ListenError

/**
 * Event type could not be found.
 */
object MissingEventTypeError : ListenError

/**
 * Generic dispatch failure.
 */
object CouldNotDispatchError : ListenError

/**
 * Event was cancelled, thus, listener wasn't called.
 */
class EventCancelledError : ListenError