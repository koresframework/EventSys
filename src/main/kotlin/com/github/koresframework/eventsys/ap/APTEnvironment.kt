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

import com.koresframework.kores.type.KoresTypeResolver
import com.koresframework.kores.type.defaultResolver
import com.koresframework.kores.util.GenericResolver
import com.koresframework.kores.util.MixedResolver
import com.github.koresframework.eventsys.gen.GenerationEnvironment
import com.github.koresframework.eventsys.util.DeclarationCache
import java.lang.reflect.Type
import javax.lang.model.util.Elements

class APTEnvironment(val elements: Elements) : GenerationEnvironment {
    override val declarationCache: DeclarationCache = DeclarationCache()
    override val genericResolver: GenericResolver = MixedResolver(this.elements)

    override fun getTypeResolver(type: Type): KoresTypeResolver<*> = type.defaultResolver
}