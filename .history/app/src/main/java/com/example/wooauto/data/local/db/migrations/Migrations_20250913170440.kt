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

    val MIGRATION_9_10: Migration = object : Migration(9, 10) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // 通过重建表将 template_configs 精确对齐到实体定义，解决历史 v9 schema 差异导致的 identity hash 不一致
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `template_configs_new` (
                    `templateId` TEXT NOT NULL,
                    `templateType` TEXT NOT NULL,
                    `templateName` TEXT NOT NULL,
                    `showStoreInfo` INTEGER NOT NULL DEFAULT 1,
                    `showStoreName` INTEGER NOT NULL DEFAULT 1,
                    `showStoreAddress` INTEGER NOT NULL DEFAULT 1,
                    `showStorePhone` INTEGER NOT NULL DEFAULT 1,
                    `styleStoreNameBold` INTEGER NOT NULL DEFAULT 1,
                    `styleStoreNameLarge` INTEGER NOT NULL DEFAULT 1,
                    `styleStoreAddressBold` INTEGER NOT NULL DEFAULT 0,
                    `styleStoreAddressLarge` INTEGER NOT NULL DEFAULT 0,
                    `styleStorePhoneBold` INTEGER NOT NULL DEFAULT 0,
                    `styleStorePhoneLarge` INTEGER NOT NULL DEFAULT 0,
                    `showOrderInfo` INTEGER NOT NULL DEFAULT 1,
                    `showOrderNumber` INTEGER NOT NULL DEFAULT 1,
                    `showOrderDate` INTEGER NOT NULL DEFAULT 1,
                    `styleOrderNumberBold` INTEGER NOT NULL DEFAULT 0,
                    `styleOrderNumberLarge` INTEGER NOT NULL DEFAULT 0,
                    `styleOrderDateBold` INTEGER NOT NULL DEFAULT 0,
                    `styleOrderDateLarge` INTEGER NOT NULL DEFAULT 0,
                    `showCustomerInfo` INTEGER NOT NULL DEFAULT 1,
                    `showCustomerName` INTEGER NOT NULL DEFAULT 1,
                    `showCustomerPhone` INTEGER NOT NULL DEFAULT 1,
                    `showDeliveryInfo` INTEGER NOT NULL DEFAULT 0,
                    `styleCustomerNameBold` INTEGER NOT NULL DEFAULT 0,
                    `styleCustomerNameLarge` INTEGER NOT NULL DEFAULT 0,
                    `styleCustomerPhoneBold` INTEGER NOT NULL DEFAULT 0,
                    `styleCustomerPhoneLarge` INTEGER NOT NULL DEFAULT 0,
                    `showOrderContent` INTEGER NOT NULL DEFAULT 1,
                    `showItemDetails` INTEGER NOT NULL DEFAULT 1,
                    `showItemPrices` INTEGER NOT NULL DEFAULT 1,
                    `showOrderNotes` INTEGER NOT NULL DEFAULT 1,
                    `showTotals` INTEGER NOT NULL DEFAULT 1,
                    `styleItemNameBold` INTEGER NOT NULL DEFAULT 1,
                    `styleItemNameLarge` INTEGER NOT NULL DEFAULT 1,
                    `styleQtyPriceBold` INTEGER NOT NULL DEFAULT 1,
                    `styleQtyPriceLarge` INTEGER NOT NULL DEFAULT 1,
                    `showPaymentInfo` INTEGER NOT NULL DEFAULT 1,
                    `stylePaymentMethodBold` INTEGER NOT NULL DEFAULT 0,
                    `stylePaymentMethodLarge` INTEGER NOT NULL DEFAULT 0,
                    `showFooter` INTEGER NOT NULL DEFAULT 1,
                    `styleFooterBold` INTEGER NOT NULL DEFAULT 0,
                    `styleFooterLarge` INTEGER NOT NULL DEFAULT 0,
                    `footerText` TEXT NOT NULL DEFAULT 'Thank you for your order!',
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`templateId`)
                )
                """
            )

            // 收集 source 表已有的列，以保留用户在 v9 的已有值；缺失列则使用默认值
            val existingColumns = mutableSetOf<String>()
            val cursor = database.query("PRAGMA table_info(`template_configs`)")
            try {
                val nameIndex = cursor.getColumnIndex("name")
                while (cursor.moveToNext()) {
                    val colName = cursor.getString(nameIndex)
                    if (colName != null) existingColumns.add(colName)
                }
            } finally {
                cursor.close()
            }

            fun has(col: String) = existingColumns.contains(col)
            fun v(col: String, defaultExpr: String): String = if (has(col)) "`$col`" else defaultExpr

            val insertSql = buildString {
                append(
                    """
                    INSERT INTO `template_configs_new` (
                        `templateId`, `templateType`, `templateName`,
                        `showStoreInfo`, `showStoreName`, `showStoreAddress`, `showStorePhone`,
                        `styleStoreNameBold`, `styleStoreNameLarge`, `styleStoreAddressBold`, `styleStoreAddressLarge`, `styleStorePhoneBold`, `styleStorePhoneLarge`,
                        `showOrderInfo`, `showOrderNumber`, `showOrderDate`,
                        `styleOrderNumberBold`, `styleOrderNumberLarge`, `styleOrderDateBold`, `styleOrderDateLarge`,
                        `showCustomerInfo`, `showCustomerName`, `showCustomerPhone`, `showDeliveryInfo`,
                        `styleCustomerNameBold`, `styleCustomerNameLarge`, `styleCustomerPhoneBold`, `styleCustomerPhoneLarge`,
                        `showOrderContent`, `showItemDetails`, `showItemPrices`, `showOrderNotes`, `showTotals`,
                        `styleItemNameBold`, `styleItemNameLarge`, `styleQtyPriceBold`, `styleQtyPriceLarge`,
                        `showPaymentInfo`, `stylePaymentMethodBold`, `stylePaymentMethodLarge`,
                        `showFooter`, `styleFooterBold`, `styleFooterLarge`,
                        `footerText`, `createdAt`, `updatedAt`
                    )
                    SELECT 
                        `templateId`, `templateType`, `templateName`,
                        `showStoreInfo`, `showStoreName`, `showStoreAddress`, `showStorePhone`,
                """.trimIndent()
                )

                append(
                    "${v("styleStoreNameBold", "1")}, ${v("styleStoreNameLarge", "1")}, ${v("styleStoreAddressBold", "0")}, ${v("styleStoreAddressLarge", "0")}, ${v("styleStorePhoneBold", "0")}, ${v("styleStorePhoneLarge", "0")},\n"
                )

                append(
                    "`showOrderInfo`, `showOrderNumber`, `showOrderDate`,\n"
                )

                append(
                    "${v("styleOrderNumberBold", "0")}, ${v("styleOrderNumberLarge", "0")}, ${v("styleOrderDateBold", "0")}, ${v("styleOrderDateLarge", "0")},\n"
                )

                append(
                    "`showCustomerInfo`, `showCustomerName`, `showCustomerPhone`, ${v("showDeliveryInfo", "0")},\n"
                )

                append(
                    "${v("styleCustomerNameBold", "0")}, ${v("styleCustomerNameLarge", "0")}, ${v("styleCustomerPhoneBold", "0")}, ${v("styleCustomerPhoneLarge", "0")},\n"
                )

                append(
                    "`showOrderContent`, `showItemDetails`, `showItemPrices`, `showOrderNotes`, `showTotals`,\n"
                )

                append(
                    "${v("styleItemNameBold", "1")}, ${v("styleItemNameLarge", "1")}, ${v("styleQtyPriceBold", "1")}, ${v("styleQtyPriceLarge", "1")},\n"
                )

                append(
                    "`showPaymentInfo`, ${v("stylePaymentMethodBold", "0")}, ${v("stylePaymentMethodLarge", "0")},\n"
                )

                append(
                    "`showFooter`, ${v("styleFooterBold", "0")}, ${v("styleFooterLarge", "0")},\n"
                )

                append(
                    "`footerText`, `createdAt`, `updatedAt`\n"
                )

                append(
                    "FROM `template_configs`"
                )
            }

            database.execSQL(insertSql)

            database.execSQL("DROP TABLE `template_configs`")
            database.execSQL("ALTER TABLE `template_configs_new` RENAME TO `template_configs`")
        }
    }
}


