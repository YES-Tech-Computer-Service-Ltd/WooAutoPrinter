package com.example.wooauto.data.local.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room 数据库迁移集合
 * - MIGRATION_8_9: 为模板配置表增加样式（加粗/大号）字段，避免数据丢失
 */
object AppMigrations {
    val MIGRATION_8_9: Migration = object : Migration(8, 9) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // 为 template_configs 表新增样式相关列，保持与实体默认值一致
            // 商店信息样式
            database.execSQL("ALTER TABLE template_configs ADD COLUMN styleStoreNameBold INTEGER NOT NULL DEFAULT 1")
            database.execSQL("ALTER TABLE template_configs ADD COLUMN styleStoreNameLarge INTEGER NOT NULL DEFAULT 1")
            database.execSQL("ALTER TABLE template_configs ADD COLUMN styleStoreAddressBold INTEGER NOT NULL DEFAULT 0")
            database.execSQL("ALTER TABLE template_configs ADD COLUMN styleStoreAddressLarge INTEGER NOT NULL DEFAULT 0")
            database.execSQL("ALTER TABLE template_configs ADD COLUMN styleStorePhoneBold INTEGER NOT NULL DEFAULT 0")
            database.execSQL("ALTER TABLE template_configs ADD COLUMN styleStorePhoneLarge INTEGER NOT NULL DEFAULT 0")

            // 订单信息样式
            database.execSQL("ALTER TABLE template_configs ADD COLUMN styleOrderNumberBold INTEGER NOT NULL DEFAULT 0")
            database.execSQL("ALTER TABLE template_configs ADD COLUMN styleOrderNumberLarge INTEGER NOT NULL DEFAULT 0")
            database.execSQL("ALTER TABLE template_configs ADD COLUMN styleOrderDateBold INTEGER NOT NULL DEFAULT 0")
            database.execSQL("ALTER TABLE template_configs ADD COLUMN styleOrderDateLarge INTEGER NOT NULL DEFAULT 0")

            // 客户信息样式
            database.execSQL("ALTER TABLE template_configs ADD COLUMN styleCustomerNameBold INTEGER NOT NULL DEFAULT 0")
            database.execSQL("ALTER TABLE template_configs ADD COLUMN styleCustomerNameLarge INTEGER NOT NULL DEFAULT 0")
            database.execSQL("ALTER TABLE template_configs ADD COLUMN styleCustomerPhoneBold INTEGER NOT NULL DEFAULT 0")
            database.execSQL("ALTER TABLE template_configs ADD COLUMN styleCustomerPhoneLarge INTEGER NOT NULL DEFAULT 0")

            // 订单内容样式
            database.execSQL("ALTER TABLE template_configs ADD COLUMN styleItemNameBold INTEGER NOT NULL DEFAULT 1")
            database.execSQL("ALTER TABLE template_configs ADD COLUMN styleItemNameLarge INTEGER NOT NULL DEFAULT 1")
            database.execSQL("ALTER TABLE template_configs ADD COLUMN styleQtyPriceBold INTEGER NOT NULL DEFAULT 1")
            database.execSQL("ALTER TABLE template_configs ADD COLUMN styleQtyPriceLarge INTEGER NOT NULL DEFAULT 1")

            // 支付信息样式
            database.execSQL("ALTER TABLE template_configs ADD COLUMN stylePaymentMethodBold INTEGER NOT NULL DEFAULT 0")
            database.execSQL("ALTER TABLE template_configs ADD COLUMN stylePaymentMethodLarge INTEGER NOT NULL DEFAULT 0")

            // 页脚样式
            database.execSQL("ALTER TABLE template_configs ADD COLUMN styleFooterBold INTEGER NOT NULL DEFAULT 0")
            database.execSQL("ALTER TABLE template_configs ADD COLUMN styleFooterLarge INTEGER NOT NULL DEFAULT 0")
        }
    }
}


