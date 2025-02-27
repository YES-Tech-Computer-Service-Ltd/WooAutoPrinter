package com.wooauto.data.repository

import app.cash.turbine.test
import com.wooauto.data.local.dao.SettingDao
import com.wooauto.data.local.entities.SettingEntity
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.kotlin.*

class SettingRepositoryImplTest : BaseRepositoryTest() {

    @Mock
    private lateinit var settingDao: SettingDao

    private lateinit var repository: SettingRepositoryImpl

    @Before
    override fun setup() {
        super.setup()
        repository = SettingRepositoryImpl(settingDao)
    }

    @Test
    fun `getAllSettings 应返回转换后的领域模型列表`() = runTest {
        // 准备测试数据
        val settingEntities = listOf(createTestSettingEntity())
        whenever(settingDao.getAllSettings()).thenReturn(flowOf(settingEntities))

        // 执行测试
        repository.getAllSettings().test {
            val result = awaitItem()
            assertEquals(1, result.size)
            assertEquals(settingEntities[0].key, result[0].key)
            assertEquals(settingEntities[0].value, result[0].value)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getSettingByKey 应返回转换后的领域模型`() = runTest {
        // 准备测试数据
        val settingEntity = createTestSettingEntity()
        whenever(settingDao.getSettingByKey("test_key")).thenReturn(settingEntity)

        // 执行测试
        val result = repository.getSettingByKey("test_key")
        assertEquals(settingEntity.key, result?.key)
        assertEquals(settingEntity.value, result?.value)
    }

    @Test
    fun `saveSetting 应正确保存设置`() = runTest {
        // 执行测试
        repository.saveSetting("test_key", "test_value", "string")

        // 验证交互
        verify(settingDao).insertOrUpdateSetting(
            argThat { 
                key == "test_key" && 
                value == "test_value" && 
                type == "string"
            }
        )
    }

    @Test
    fun `deleteSetting 应正确删除设置`() = runTest {
        // 准备测试数据
        val settingEntity = createTestSettingEntity()
        whenever(settingDao.getSettingByKey("test_key")).thenReturn(settingEntity)

        // 执行测试
        repository.deleteSetting("test_key")

        // 验证交互
        verify(settingDao).deleteSetting(settingEntity)
    }

    @Test
    fun `deleteSetting 当设置不存在时不应调用删除`() = runTest {
        // 准备测试数据
        whenever(settingDao.getSettingByKey("test_key")).thenReturn(null)

        // 执行测试
        repository.deleteSetting("test_key")

        // 验证交互
        verify(settingDao, never()).deleteSetting(any())
    }

    private fun createTestSettingEntity() = SettingEntity(
        key = "test_key",
        value = "test_value",
        type = "string",
        description = "Test setting description"
    )
} 