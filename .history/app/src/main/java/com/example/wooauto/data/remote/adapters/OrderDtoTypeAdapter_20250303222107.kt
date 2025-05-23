package com.example.wooauto.data.remote.adapters

import android.util.Log
import com.example.wooauto.data.remote.dto.AddressDto
import com.example.wooauto.data.remote.dto.CustomerDto
import com.example.wooauto.data.remote.dto.LineItemDto
import com.example.wooauto.data.remote.dto.OrderDto
import com.example.wooauto.data.remote.dto.MetaDataDto
import com.example.wooauto.data.remote.dto.ImageDto
import com.example.wooauto.data.remote.metadata.MetadataProcessorRegistry
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter

/**
 * OrderDto的类型适配器
 * 专门处理OrderDto的JSON解析，防止类型转换错误
 */
class OrderDtoTypeAdapter : TypeAdapter<OrderDto>() {
    // 创建元数据处理器注册表
    private val metadataRegistry by lazy { MetadataProcessorRegistry.getInstance() }
    
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
        var parentId: Long = 0
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
        var customerNote: String? = null

        try {
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "id" -> {
                        id = when (reader.peek()) {
                            JsonToken.NULL -> {
                                reader.nextNull()
                                0L
                            }
                            JsonToken.STRING -> {
                                val idStr = reader.nextString()
                                try {
                                    if (idStr.isBlank()) 0L else idStr.toLong()
                                } catch (e: NumberFormatException) {
                                    Log.e("OrderDtoTypeAdapter", "解析订单ID失败: $idStr", e)
                                    0L
                                }
                            }
                            else -> reader.nextLong()
                        }
                    }
                    "parent_id" -> {
                        parentId = when (reader.peek()) {
                            JsonToken.NULL -> {
                                reader.nextNull()
                                0L
                            }
                            JsonToken.STRING -> {
                                val parentIdStr = reader.nextString()
                                try {
                                    if (parentIdStr.isBlank()) 0L else parentIdStr.toLong()
                                } catch (e: NumberFormatException) {
                                    Log.e("OrderDtoTypeAdapter", "解析父订单ID失败: $parentIdStr", e)
                                    0L
                                }
                            }
                            else -> reader.nextLong()
                        }
                    }
                    "customer_id" -> {
                        when (reader.peek()) {
                            JsonToken.NULL -> reader.nextNull()
                            JsonToken.STRING -> {
                                try {
                                    val customerIdStr = reader.nextString()
                                    if (customerIdStr.isNotBlank()) {
                                        customerIdStr.toLong()
                                    }
                                } catch (e: NumberFormatException) {
                                    Log.e("OrderDtoTypeAdapter", "解析客户ID失败", e)
                                }
                            }
                            else -> reader.skipValue()
                        }
                    }
                    "number" -> number = if (reader.peek() == JsonToken.NULL) { reader.nextNull(); "" } else reader.nextString()
                    "status" -> status = if (reader.peek() == JsonToken.NULL) { reader.nextNull(); "" } else reader.nextString()
                    "date_created" -> dateCreated = if (reader.peek() == JsonToken.NULL) { reader.nextNull(); "" } else reader.nextString()
                    "date_modified" -> dateModified = if (reader.peek() == JsonToken.NULL) { reader.nextNull(); "" } else reader.nextString()
                    "total" -> total = if (reader.peek() == JsonToken.NULL) { reader.nextNull(); "0.00" } else reader.nextString()
                    "customer" -> {
                        if (reader.peek() == JsonToken.NULL) {
                            reader.nextNull()
                            customer = null
                        } else {
                            try {
                                customer = readCustomer(reader)
                            } catch (e: Exception) {
                                Log.e("OrderDtoTypeAdapter", "解析客户数据失败: ${e.message}", e)
                                reader.skipValue()
                                customer = null
                            }
                        }
                    }
                    "billing" -> {
                        try {
                            billingAddress = readAddress(reader)
                        } catch (e: Exception) {
                            Log.e("OrderDtoTypeAdapter", "解析账单地址失败: ${e.message}", e)
                            reader.skipValue()
                            billingAddress = AddressDto("", "", "", "", "", "", "", "", "", "")
                        }
                    }
                    "shipping" -> {
                        if (reader.peek() == JsonToken.NULL) {
                            reader.nextNull()
                            shippingAddress = null
                        } else {
                            try {
                                shippingAddress = readAddress(reader)
                            } catch (e: Exception) {
                                Log.e("OrderDtoTypeAdapter", "解析送货地址失败: ${e.message}", e)
                                reader.skipValue()
                                shippingAddress = null
                            }
                        }
                    }
                    "line_items" -> {
                        try {
                            lineItems = readLineItems(reader)
                        } catch (e: Exception) {
                            Log.e("OrderDtoTypeAdapter", "解析订单项失败: ${e.message}", e)
                            reader.skipValue()
                            lineItems = emptyList()
                        }
                    }
                    "payment_method" -> paymentMethod = if (reader.peek() == JsonToken.NULL) { reader.nextNull(); null } else reader.nextString()
                    "payment_method_title" -> paymentMethodTitle = if (reader.peek() == JsonToken.NULL) { reader.nextNull(); null } else reader.nextString()
                    "customer_note" -> customerNote = if (reader.peek() == JsonToken.NULL) { reader.nextNull(); null } else reader.nextString()
                    else -> reader.skipValue() // 跳过未知字段
                }
            }
            reader.endObject()
        } catch (e: Exception) {
            Log.e("OrderDtoTypeAdapter", "解析OrderDto时出错: ${e.message}", e)
        }

        return OrderDto(
            id = id,
            parentId = parentId,
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
            customerNote = customerNote
        )
    }

    private fun readCustomer(reader: JsonReader): CustomerDto {
        var id: Long = 0
        var firstName: String? = null
        var lastName: String? = null
        var email: String? = null
        var phone: String? = null

        try {
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "id" -> {
                        id = when (reader.peek()) {
                            JsonToken.NULL -> {
                                reader.nextNull()
                                0L
                            }
                            JsonToken.STRING -> {
                                val idStr = reader.nextString()
                                try {
                                    if (idStr.isBlank()) 0L else idStr.toLong()
                                } catch (e: NumberFormatException) {
                                    Log.e("OrderDtoTypeAdapter", "解析客户ID失败: $idStr", e)
                                    0L
                                }
                            }
                            else -> reader.nextLong()
                        }
                    }
                    "first_name" -> firstName = if (reader.peek() == JsonToken.NULL) { reader.nextNull(); null } else reader.nextString()
                    "last_name" -> lastName = if (reader.peek() == JsonToken.NULL) { reader.nextNull(); null } else reader.nextString()
                    "email" -> email = if (reader.peek() == JsonToken.NULL) { reader.nextNull(); null } else reader.nextString()
                    "phone" -> phone = if (reader.peek() == JsonToken.NULL) { reader.nextNull(); null } else reader.nextString()
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
        } catch (e: Exception) {
            Log.e("OrderDtoTypeAdapter", "解析CustomerDto时出错: ${e.message}", e)
        }

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

        try {
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
        } catch (e: Exception) {
            Log.e("OrderDtoTypeAdapter", "解析AddressDto时出错: ${e.message}", e)
        }

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

        try {
            reader.beginArray()
            while (reader.hasNext()) {
                var id: Long = 0
                var name: String = ""
                var productId: Long = 0
                var quantity: Int = 0
                var subtotal: String = ""
                var total: String = ""
                var price: Double = 0.0
                var metaData = emptyList<MetaDataDto>()
                var image: ImageDto? = null

                try {
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
                            "meta_data" -> {
                                if (reader.peek() != JsonToken.NULL) {
                                    try {
                                        // 使用元数据处理器注册表读取元数据
                                        metaData = metadataRegistry.readMetadata(reader)
                                        Log.d("OrderDtoTypeAdapter", "成功解析元数据，数量: ${metaData.size}")
                                    } catch (e: Exception) {
                                        Log.e("OrderDtoTypeAdapter", "解析元数据时出错: ${e.message}", e)
                                        reader.skipValue() // 跳过无法解析的元数据
                                    }
                                } else {
                                    reader.nextNull()
                                }
                            }
                            "image" -> {
                                if (reader.peek() == JsonToken.NULL) {
                                    reader.nextNull()
                                    image = null
                                } else {
                                    image = readImage(reader)
                                }
                            }
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
                            price = price,
                            metaData = metaData,
                            image = image
                        )
                    )
                } catch (e: Exception) {
                    Log.e("OrderDtoTypeAdapter", "解析单个LineItemDto时出错: ${e.message}", e)
                    reader.skipValue() // 跳过无法解析的行项目
                }
            }
            reader.endArray()
        } catch (e: Exception) {
            Log.e("OrderDtoTypeAdapter", "解析LineItemDto列表时出错: ${e.message}", e)
        }

        return lineItems
    }
    
    private fun readImage(reader: JsonReader): ImageDto {
        var id: Long? = null
        var src: String? = null
        
        try {
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "id" -> id = if (reader.peek() == JsonToken.NULL) { reader.nextNull(); null } else reader.nextLong()
                    "src" -> src = if (reader.peek() == JsonToken.NULL) { reader.nextNull(); null } else reader.nextString()
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
        } catch (e: Exception) {
            Log.e("OrderDtoTypeAdapter", "解析ImageDto时出错: ${e.message}", e)
        }
        
        return ImageDto(id, src)
    }
} 