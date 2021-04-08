/*
 *      EventSys - Event implementation generator written on top of Kores
 *
 *         The MIT License (MIT)
 *
 *      Copyright (c) 2021 ProjectSandstone <https://github.com/ProjectSandstone/EventSys>
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
package com.github.koresframework.eventsys.event

import com.github.koresframework.eventsys.gen.event.EventClassGenerator

/**
 * A type of [Event] that can be cancelled.
 *
 * If an [Event] is cancelled, all changes must be reverted or cancelled.
 *
 * By default, `EventSys` does not cancel events when [isCancelled] is set to `true`,
 * it only does when [EventListener.cancelAffected] is set to `true`.
 *
 * When event is cancelled, the first [Cancel affected listener][EventListener.cancelAffected] will switch
 * the cancel state to `true`, meaning that subsequent [Cancel affected listeners][EventListener.cancelAffected]
 * will be ignored, even if `isCancelled` is set to `true` again. If cancellation state could be changed
 * between listeners, use [EventListener.priority] to set priorities.
 *
 * [Cancellable] events does not work with async dispatch, they could be dispatched, but will never be ignored,
 * even if the listener is [EventListener.cancelAffected].
 *
 * [EventClassGenerator] always generates the [isCancelled] property for [Cancellable] events with `false` as
 * default value.
 */
interface Cancellable {

    /**
     * Is the event cancelled
     */
    var isCancelled: Boolean
}