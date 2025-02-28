package com.wooauto.domain.models

/**
 * Domain Model for a WooCommerce Product.
 *
 * 说明：这个模型用于业务层处理，不依赖于具体的序列化框架，
 * 所有 JSON 映射逻辑将在 Data 层完成后通过 Mapper 转换成此对象。
 */
data class Product(
    val id: Long,                   // WooCommerce 产品 ID
    val name: String,               // 产品名称
    val slug: String,               // 用于 URL 的别名（SEO）
    val permalink: String,          // 产品页面的永久链接
    val dateCreated: String,        // 产品创建日期（字符串格式）
    val status: String,             // 产品状态（如 "publish", "draft" 等）
    val featured: Boolean,          // 是否为特色产品
    val catalogVisibility: String,  // 在目录中的可见性（如 "visible", "hidden"）
    val description: String,        // 产品详细描述
    val shortDescription: String,   // 产品简短描述
    val sku: String,                // 库存单位号（产品 SKU）
    val price: String,              // 当前销售价格
    val regularPrice: String,       // 常规价格
    val salePrice: String?,         // 折扣销售价格
    val onSale: Boolean,            // 是否正在促销
    val purchasable: Boolean,       // 是否可购买
    val totalSales: Int,            // 销售总量
    val stockQuantity: Int?,        // 库存数量（null 表示无限库存或未知）
    val stockStatus: String,        // 库存状态（如 "instock", "outofstock"）
    val backorders: String,         // 后补订单设置（例如 "no", "notify", "yes"）
    val backordersAllowed: Boolean, // 是否允许后补订单
    val backordered: Boolean,       // 是否已后补订单
    val soldIndividually: Boolean, // 是否限制单个订单只可购买一件
    val weight: String,             // 产品重量（可能包含单位信息）
    val dimensions: Dimensions,     // 产品尺寸信息
    val categories: List<Category>, // 产品所属分类列表
    val images: List<String>,       // 产品图片列表
    val attributes: List<ProductAttribute>?
)

/**
 * Domain Model for Dimensions, representing product dimensions.
 */
data class Dimensions(
    val length: String, // 产品长度
    val width: String,  // 产品宽度
    val height: String  // 产品高度
)

/**
 * Domain Model for Category, representing product categories in WooCommerce.
 */
data class Category(
    val id: Long,    // 分类 ID
    val name: String, // 分类名称
    val slug: String  // 分类别名（用于 URL）
)

/**
 * Domain Model for Image, representing product image details.
 */
data class Image(
    val id: Long,              // 图片 ID
    val dateCreated: String,   // 图片创建日期
    val dateModified: String,  // 图片最后修改日期
    val src: String,           // 图片 URL 地址
    val name: String,          // 图片名称
    val alt: String            // 图片替代文本（alt文本）
)

data class ProductAttribute(
    val id: Long,
    val name: String,
    val options: List<String>,
    val variation: Boolean
)
