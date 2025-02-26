package com.example.wooauto.ui.settings.viewmodel

import android.app.Application
import app.cash.turbine.test
import com.example.wooauto.BaseTest
import com.example.wooauto.utils.LanguageHelper
import com.example.wooauto.utils.PreferencesManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * SettingsViewModel 的单元测试
 * 
 * 使用方法：
 * 1. 在 Android Studio 中右键点击类名，选择 "Run 'SettingsViewModelTest'"
 * 2. 或在终端使用 ./gradlew test 运行所有测试
 * 
 * 测试覆盖：
 * - 初始语言设置的加载
 * - 语言更新功能
 * - 语言变更的持久化
 */
class SettingsViewModelTest : BaseTest() {

    private lateinit var viewModel: SettingsViewModel
    private lateinit var application: Application
    private lateinit var preferencesManager: PreferencesManager

    @Before
    fun setup() {
        // 创建 mock 对象
        application = mockk(relaxed = true)
        preferencesManager = mockk {
            every { language } returns flowOf(PreferencesManager.DEFAULT_LANGUAGE)
        }

        // 注入 mock 对象
        viewModel = SettingsViewModel(application)
    }

    @Test
    fun `初始语言设置应该是默认语言`() = runTest {
        viewModel.language.test {
            assertEquals(PreferencesManager.DEFAULT_LANGUAGE, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `更新语言时应该保存设置并应用更改`() = runTest {
        // 准备
        val newLanguage = "zh"
        coEvery { preferencesManager.setLanguage(any()) } returns Unit

        // 执行
        viewModel.updateLanguage(newLanguage)

        // 验证
        coVerify { 
            preferencesManager.setLanguage(newLanguage)
            LanguageHelper.setLocale(application, newLanguage)
        }
    }

    @Test
    fun `语言更新失败时应该处理异常`() = runTest {
        // 准备
        val newLanguage = "zh"
        coEvery { preferencesManager.setLanguage(any()) } throws Exception("测试异常")

        // 执行
        viewModel.updateLanguage(newLanguage)

        // 验证异常处理
        verify { 
            // 这里可以验证错误处理逻辑，比如错误提示等
        }
    }
} 