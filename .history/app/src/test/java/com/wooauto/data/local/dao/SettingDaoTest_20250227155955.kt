package com.wooauto.data.local.dao

import app.cash.turbine.test
import com.wooauto.data.local.entities.SettingEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class SettingDaoTest : BaseDaoTest() {
    
    private lateinit var settingDao: SettingDao

    override fun setup() {
        super.setup()
        settingDao = database.settingDao()
    }

    @Test
    fun `插入设置后应能正确查询`() = runTest {
        // 准备测试数据
        val setting = createTestSetting()
        
        // 执行插入
        settingDao.insertOrUpdateSetting(setting)
        
        // 验证查询结果
        val result = settingDao.getSettingByKey(setting.key)
        assertEquals(setting, result)
    }

    @Test
    fun `更新设置后应反映新值`() = runTest {
        // 准备并插入初始数据
        val setting = createTestSetting()
        settingDao.insertOrUpdateSetting(setting)
        
        // 更新数据
        val updatedSetting = setting.copy(
            value = "new_value",
            description = "Updated description"
        )
        settingDao.updateSetting(updatedSetting)
        
        // 验证更新结果
        val result = settingDao.getSettingByKey(setting.key)
        assertEquals("new_value", result?.value)
        assertEquals("Updated description", result?.description)
    }

    @Test
    fun `删除设置后应返回null`() = runTest {
        // 准备并插入数据
        val setting = createTestSetting()
        settingDao.insertOrUpdateSetting(setting)
        
        // 删除数据
        settingDao.deleteSetting(setting)
        
        // 验证删除结果
        val result = settingDao.getSettingByKey(setting.key)
        assertNull(result)
    }

    @Test
    fun `getAllSettings应返回所有设置`() = runTest {
        // 准备多个测试数据
        val settings = listOf(
            createTestSetting("key1"),
            createTestSetting("key2"),
            createTestSetting("key3")
        )
        
        // 插入所有数据
        settings.forEach { settingDao.insertOrUpdateSetting(it) }
        
        // 验证查询结果
        settingDao.getAllSettings().test {
            val result = awaitItem()
            assertEquals(3, result.size)
            assertEquals(settings.toSet(), result.toSet())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `插入重复键的设置应覆盖原有数据`() = runTest {
        // 准备两个键相同的设置
        val originalSetting = createTestSetting("test_key", "old_value")
        val newSetting = createTestSetting("test_key", "new_value")
        
        // 先插入原始设置
        settingDao.insertOrUpdateSetting(originalSetting)
        
        // 再插入新设置
        settingDao.insertOrUpdateSetting(newSetting)
        
        // 验证结果是否为新设置
        val result = settingDao.getSettingByKey("test_key")
        assertEquals("new_value", result?.value)
    }

    private fun createTestSetting(
        key: String = "test_key",
        value: String = "test_value"
    ) = SettingEntity(
        key = key,
        value = value,
        type = "string",
        description = "Test setting description"
    )
} 