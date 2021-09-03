package com.github.koresframework.eventsys.util

import com.koresframework.kores.type.concreteType
import com.koresframework.kores.type.typeOf
import java.lang.reflect.Type
import kotlin.coroutines.Continuation

val Type.isContinuation get() = this.concreteType.`is`(typeOf<Continuation<*>>().concreteType)