package com.example.wooauto.data.remote.adapters

import com.example.wooauto.data.remote.dto.AddressDto
import com.example.wooauto.data.remote.dto.CustomerDto
import com.example.wooauto.data.remote.dto.LineItemDto
import com.example.wooauto.data.remote.dto.OrderDto
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter

/**
 * OrderDto的类型适配器
 * 专门处理OrderDto的JSON解析，防止类型转换错误
 */
class OrderDtoTypeAdapter : TypeAdapter<OrderDto>() {
    override fun write(out: JsonWriter, value: OrderDto?) {
        // 实现序列化逻辑（如果需要）
        // 当前实现中主要关注反序列化
    }

    override fun read(reader: JsonReader): OrderDto? {
        if (reader.peek() == JsonToken.NULL) {
            reader.nextNull()
            return null
        }

        var id: Long = 0
        var number: String = ""
        var status: String = ""
        var dateCreated: String = ""
        var dateModified: String = ""
        var total: String = ""
        var customer: CustomerDto? = null
        var billingAddress: AddressDto = AddressDto("", "", "", "", "", "", "", "", "", "")
        var shippingAddress: AddressDto? = null
        var lineItems: List<LineItemDto> = emptyList()
        var paymentMethod: String? = null
        var paymentMethodTitle: String? = null

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "id" -> id = reader.nextLong()
                "number" -> number = reader.nextString()
                "status" -> status = reader.nextString()
                "date_created" -> dateCreated = reader.nextString()
                "date_modified" -> dateModified = reader.nextString()
                "total" -> total = reader.nextString()
                "customer" -> {
                    if (reader.peek() == JsonToken.NULL) {
                        reader.nextNull()
                        customer = null
                    } else {
                        customer = readCustomer(reader)
                    }
                }
                "billing" -> billingAddress = readAddress(reader)
                "shipping" -> shippingAddress = readAddress(reader)
                "line_items" -> lineItems = readLineItems(reader)
                "payment_method" -> paymentMethod = if (reader.peek() == JsonToken.NULL) { reader.nextNull(); null } else reader.nextString()
                "payment_method_title" -> paymentMethodTitle = if (reader.peek() == JsonToken.NULL) { reader.nextNull(); null } else reader.nextString()
                else -> reader.skipValue() // 跳过未知字段
            }
        }
        reader.endObject()

        return OrderDto(
            id = id,
            number = number,
            status = status,
            dateCreated = dateCreated,
            dateModified = dateModified,
            total = total,
            customer = customer,
            billingAddress = billingAddress,
            shippingAddress = shippingAddress,
            lineItems = lineItems,
            paymentMethod = paymentMethod,
            paymentMethodTitle = paymentMethodTitle,
            customerNote = null
        )
    }

    private fun readCustomer(reader: JsonReader): CustomerDto {
        var id: Long? = null
        var firstName: String? = null
        var lastName: String? = null
        var email: String? = null
        var phone: String? = null

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "id" -> id = if (reader.peek() == JsonToken.NULL) { reader.nextNull(); null } else reader.nextLong()
                "first_name" -> firstName = if (reader.peek() == JsonToken.NULL) { reader.nextNull(); null } else reader.nextString()
                "last_name" -> lastName = if (reader.peek() == JsonToken.NULL) { reader.nextNull(); null } else reader.nextString()
                "email" -> email = if (reader.peek() == JsonToken.NULL) { reader.nextNull(); null } else reader.nextString()
                "phone" -> phone = if (reader.peek() == JsonToken.NULL) { reader.nextNull(); null } else reader.nextString()
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        return CustomerDto(
            id = id,
            firstName = firstName,
            lastName = lastName,
            email = email,
            phone = phone
        )
    }

    private fun readAddress(reader: JsonReader): AddressDto {
        var firstName: String? = null
        var lastName: String? = null
        var company: String? = null
        var address1: String? = null
        var address2: String? = null
        var city: String? = null
        var state: String? = null
        var postcode: String? = null
        var country: String? = null
        var phone: String? = null

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "first_name" -> firstName = if (reader.peek() == JsonToken.NULL) { reader.nextNull(); null } else reader.nextString()
                "last_name" -> lastName = if (reader.peek() == JsonToken.NULL) { reader.nextNull(); null } else reader.nextString()
                "company" -> company = if (reader.peek() == JsonToken.NULL) { reader.nextNull(); null } else reader.nextString()
                "address_1" -> address1 = if (reader.peek() == JsonToken.NULL) { reader.nextNull(); null } else reader.nextString()
                "address_2" -> address2 = if (reader.peek() == JsonToken.NULL) { reader.nextNull(); null } else reader.nextString()
                "city" -> city = if (reader.peek() == JsonToken.NULL) { reader.nextNull(); null } else reader.nextString()
                "state" -> state = if (reader.peek() == JsonToken.NULL) { reader.nextNull(); null } else reader.nextString()
                "postcode" -> postcode = if (reader.peek() == JsonToken.NULL) { reader.nextNull(); null } else reader.nextString()
                "country" -> country = if (reader.peek() == JsonToken.NULL) { reader.nextNull(); null } else reader.nextString()
                "phone" -> phone = if (reader.peek() == JsonToken.NULL) { reader.nextNull(); null } else reader.nextString()
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        return AddressDto(
            firstName = firstName,
            lastName = lastName,
            company = company,
            address_1 = address1,
            address_2 = address2,
            city = city,
            state = state,
            postcode = postcode,
            country = country,
            phone = phone
        )
    }

    private fun readLineItems(reader: JsonReader): List<LineItemDto> {
        val lineItems = mutableListOf<LineItemDto>()

        reader.beginArray()
        while (reader.hasNext()) {
            var id: Long = 0
            var name: String = ""
            var productId: Long = 0
            var quantity: Int = 0
            var subtotal: String = ""
            var total: String = ""
            var price: Double = 0.0

            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "id" -> id = reader.nextLong()
                    "name" -> name = reader.nextString()
                    "product_id" -> productId = reader.nextLong()
                    "quantity" -> quantity = reader.nextInt()
                    "subtotal" -> subtotal = reader.nextString()
                    "total" -> total = reader.nextString()
                    "price" -> price = reader.nextDouble()
                    else -> reader.skipValue()
                }
            }
            reader.endObject()

            lineItems.add(
                LineItemDto(
                    id = id,
                    name = name,
                    productId = productId,
                    quantity = quantity,
                    subtotal = subtotal,
                    total = total,
                    price = price
                )
            )
        }
        reader.endArray()

        return lineItems
    }
} 