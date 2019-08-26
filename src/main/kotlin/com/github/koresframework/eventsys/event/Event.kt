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
package com.github.koresframework.eventsys.event

import com.github.jonathanxd.iutils.type.TypeInfo
import com.github.koresframework.eventsys.event.annotation.Name
import com.github.koresframework.eventsys.event.property.PropertyHolder
import com.github.koresframework.eventsys.gen.event.EventGenerator
import java.lang.reflect.Type

/**
 * [Event] base class.
 *
 * There are two ways to implement a event, first is creating your own interface that inherit [Event],
 * and creating abstract property methods (getters and setters) and calling [EventGenerator.createEventClass].
 * The second is: Implementing event in an concrete class, naming constructor parameters with [Name]
 * annotation and mapping properties to getter and setter (if the property is mutable).
 *
 * The second way is not recommended.
 *
 * An [Event] may have additional properties and/or extensions, events with additional
 * properties and/or extensions have different class from events without these additional
 * properties - different additional properties generates different event classes - this
 * happens because [EventGenerator] create fields for additional properties and JVM doesn't
 * have capability to add fields, methods and implementations to existing classes
 * (this is the nature of static VMs).
 *
 * For generic events, the type information must be provided in the construction of event instance, in factories,
 * a parameter of [com.github.koresframework.eventsys.event.annotation.TypeParam] type should be added, this parameter
 * receives the event type to construct and pass to event constructor. Reified events can be also created through
 * [com.github.koresframework.eventsys.gen.event.EventClassGenerator], but they introduce a little overhead for
 * the first time that event class is generated.
 *
 * Example of factory interface of generic event:
 *
 * ```
 * public <T extends Action> UserActionEvent<T> createUserActionEvent(
 *         @TypeParam TypeInfo<UserActionEvent<T>> type,
 *         @Name("action") T action);
 * ```
 */
interface Event : PropertyHolder {

    /**
     * Type information of event type.
     */
    val eventType: Type
        get() = this::class.java

    /**
     * Gets the extension of [type] if available.
     */
    fun <T> getExtension(type: Class<T>): T? = null

}