/*
 *      EventSys - Event implementation generator written on top of Kores
 *
 *         The MIT License (MIT)
 *
 *      Copyright (c) 2018 ProjectSandstone <https://github.com/ProjectSandstone/EventSys>
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
package com.github.projectsandstone.eventsys.test.wiki

import com.github.jonathanxd.iutils.type.TypeInfo
import com.github.jonathanxd.iutils.kt.typeInfo
import com.github.projectsandstone.eventsys.event.property.GetterProperty
import com.github.projectsandstone.eventsys.event.property.Property
import com.github.projectsandstone.eventsys.event.property.primitive.IntGetterProperty
import com.github.projectsandstone.eventsys.extension.ExtensionSpecification
import com.github.projectsandstone.eventsys.gen.event.EventClassSpecification
import com.github.projectsandstone.eventsys.gen.event.EventGeneratorOptions
import com.github.projectsandstone.eventsys.impl.DefaultEventManager
import com.github.projectsandstone.eventsys.util.EventListener
import com.github.projectsandstone.eventsys.util.create
import com.github.projectsandstone.eventsys.util.createEventClass
import com.github.projectsandstone.eventsys.util.createFactory
import org.junit.Assert
import org.junit.Test
import java.util.*
import java.util.function.IntSupplier
import java.util.function.Supplier

class EventsWiki {
    @Test
    fun eventsWiki() {
        val manager = DefaultEventManager()

/*
        manager.eventGenerator.registerExtension(TransactionEvent::class.java, ExtensionSpecification(
                residence = Unit,
                extensionClass = ApplyExt::class.java,
                implement = null
        ))
*/
        //manager.eventGenerator.options[EventGeneratorOptions.ASYNC] = true
        val factory = manager.eventGenerator.createFactory<MyFactory>()()
        val eventClass = manager.eventGenerator.createEventClass<BuyEvent>()()

        val event = create(eventClass, mapOf(
                "product" to Product("USB Adapter", 10.0),
                "amount" to 5
        ))

        manager.dispatch(event, this)

        manager.registerListener<BuyEvent>(this, BuyEvent::class.java, EventListener { theEvent, _ ->
            Assert.assertEquals("USB Adapter", theEvent.product.name)
            Assert.assertEquals(10.0, theEvent.product.price, 0.0)
            Assert.assertEquals(5, theEvent.amount)
        })

        val bus = Business("x")
        val c = factory.createBuyEvent(Product("USB Adapter", 10.0), 5, bus)

        Assert.assertEquals(bus, c.getGetterProperty(Business::class.java, "business")?.getValue())

        val evt = factory.createBuyEvent2(Product("USB Adapter", 10.0))

        Assert.assertEquals(10, evt.amount)

        val transac = factory.createTransactionEvent(5.0)

        transac.apply { it * it }

        Assert.assertEquals(5.0 * 5.0, transac.amount, 0.0)
    }

    @Test
    fun staticEventsWiki() {
        val manager = DefaultEventManager()

        manager.eventGenerator.registerEventImplementation<BuyEvent>(
                EventClassSpecification(TypeInfo.of(BuyEvent::class.java), emptyList(), emptyList()),
                BuyEventImpl::class.java
        )
    }

    data class BuyEventImpl(override val product: Product, override val amount: Int) : BuyEvent {
        private val properties = Collections.unmodifiableMap(mapOf(
                "product" to GetterProperty.Impl(Product::class.java, Supplier { this.product }),
                "amount" to IntGetterProperty.Impl(IntSupplier { this.amount })
        ))

        override fun getProperties(): Map<String, Property<*>> =
                this.properties

    }
}