package com.example.wooauto.data.mappers

import com.example.wooauto.data.local.entities.OrderEntity
import com.example.wooauto.data.remote.models.OrderResponse
import org.junit.Assert.*
import org.junit.Test

class OrderMapperTest {

    private fun createMockEntity(
        id: Long = 1,
        note: String = "",
        shippingAddress: String = ""
    ): OrderEntity {
        return OrderEntity(
            id = id,
            number = id.toInt(),
            status = "processing",
            dateCreated = System.currentTimeMillis(),
            dateModified = System.currentTimeMillis(),
            total = "10.00",
            totalTax = "0.00",
            customerName = "Test Customer",
            customerNote = note,
            contactInfo = "",
            lineItems = emptyList(),
            billingAddress = "Billing Address 123",
            shippingAddress = shippingAddress,
            paymentMethod = "cod",
            paymentMethodTitle = "Cash on Delivery",
            isPrinted = false,
            notificationShown = false,
            lastUpdated = System.currentTimeMillis()
        )
    }

    @Test
    fun `mapEntityToDomain identifies delivery order correctly from metadata`() {
        val note = """
            Customer Note
            exwfood_order_method: delivery
        """.trimIndent()
        
        val entity = createMockEntity(note = note, shippingAddress = "") // Empty shipping to test fallback
        val domainOrder = OrderMapper.mapEntityToDomain(entity)
        val info = domainOrder.woofoodInfo

        assertNotNull(info)
        assertEquals("delivery", info?.orderMethod)
        assertTrue(info?.isDelivery == true)
        assertEquals("Billing Address 123", info?.deliveryAddress) // Should fallback to billing
    }

    @Test
    fun `mapEntityToDomain identifies dine-in order correctly from metadata`() {
        val note = """
            Table 5
            exwfood_order_method: dinein
        """.trimIndent()
        
        val entity = createMockEntity(note = note, shippingAddress = "Shipping Address 456") // Shipping exists but should be ignored
        val domainOrder = OrderMapper.mapEntityToDomain(entity)
        val info = domainOrder.woofoodInfo

        assertNotNull(info)
        assertEquals("dinein", info?.orderMethod)
        assertFalse(info?.isDelivery == true)
        assertNull(info?.deliveryAddress)
    }

    @Test
    fun `mapEntityToDomain identifies takeaway order correctly from metadata`() {
        val note = """
            exwfood_order_method: takeaway
        """.trimIndent()
        
        val entity = createMockEntity(note = note)
        val domainOrder = OrderMapper.mapEntityToDomain(entity)
        val info = domainOrder.woofoodInfo

        assertNotNull(info)
        assertEquals("takeaway", info?.orderMethod)
        assertFalse(info?.isDelivery == true)
        assertNull(info?.deliveryAddress)
    }
    
    @Test
    fun `mapEntityToDomain falls back to legacy logic for delivery`() {
        val note = "Please deliver to my door"
        val entity = createMockEntity(note = note, shippingAddress = "Legacy Shipping Addr")
        val domainOrder = OrderMapper.mapEntityToDomain(entity)
        val info = domainOrder.woofoodInfo

        assertNotNull(info)
        // Legacy logic might set method to "delivery" or null based on keywords
        assertEquals("delivery", info?.orderMethod) 
        assertTrue(info?.isDelivery == true)
        assertEquals("Legacy Shipping Addr", info?.deliveryAddress)
    }
}

