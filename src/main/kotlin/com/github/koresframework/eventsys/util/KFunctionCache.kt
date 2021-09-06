package com.github.koresframework.eventsys.util

import com.koresframework.kores.base.TypeDeclaration
import java.lang.reflect.Type
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass
import kotlin.reflect.full.functions

class KFunctionCache {
    private val fcache = ConcurrentHashMap<Type, List<DeclaredMethod>>()

    fun getMethods(type: Type): List<DeclaredMethod> =
        this.fcache.computeIfAbsent(type) {
            it.allFunctions
        }
}

private val Type.allFunctions: List<DeclaredMethod>
    get() {
        if (this is KClass<*>) {
            this.functions
        }
        TODO()
    }