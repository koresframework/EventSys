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
package com.github.koresframework.eventsys.ap

import com.koresframework.kores.extra.UnifiedAnnotation
import com.koresframework.kores.extra.UnifiedAnnotationData
import com.koresframework.kores.type.KoresType
import com.koresframework.kores.type.koresType
import com.github.koresframework.eventsys.event.annotation.Extension

interface FactoryUnification : UnifiedAnnotation {
    fun value(): String
    fun methodName(): String
    fun extensions(): List<ExtensionUnification>
    fun inheritProperties(): Boolean
    fun omitTypeParam(): Boolean
    fun lazy(): Boolean
}

interface FactoriesUnification : UnifiedAnnotation {
    fun value(): List<FactoryUnification>
}

interface ExtensionUnification : UnifiedAnnotation {
    fun implement(): KoresType
    fun extensionClass(): KoresType
}

interface FactorySettingsUnification : UnifiedAnnotation {
    fun value(): String
    fun compileTimeGenerator(): Boolean
    fun extensions(): List<EventExtensionUnification>
}

interface EventExtensionUnification : UnifiedAnnotation {
    fun events(): List<String>
    fun extensions(): List<ExtensionUnification>
}