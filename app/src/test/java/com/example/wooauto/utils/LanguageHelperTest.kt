package com.example.wooauto.utils

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

/**
 * LanguageHelper 的单元测试
 * 
 * 使用方法：
 * 1. 在 Android Studio 中右键点击类名，选择 "Run 'LanguageHelperTest'"
 * 2. 或在终端使用 ./gradlew test 运行所有测试
 * 
 * 测试覆盖：
 * - 语言设置功能
 * - 区域设置更新
 * - 语言显示名称获取
 */
class LanguageHelperTest {

    @Test
    fun `updateBaseContextLocale 应该正确更新上下文的语言设置`() {
        // 准备
        val context = mockk<Context>(relaxed = true)
        val resources = mockk<Resources>(relaxed = true)
        val configuration = Configuration()
        
        every { context.resources } returns resources
        every { resources.configuration } returns configuration
        every { context.createConfigurationContext(any()) } returns mockk()

        // 执行
        LanguageHelper.updateBaseContextLocale(context, "zh")

        // 验证
        verify { context.createConfigurationContext(any()) }
        assertEquals("zh", configuration.locale.language)
    }

    @Test
    fun `getLanguageDisplayName 应该返回正确的语言显示名称`() {
        // 测试中文
        assertEquals("中文 (Chinese)", LanguageHelper.getLanguageDisplayName("zh"))
        
        // 测试英文
        assertEquals("English", LanguageHelper.getLanguageDisplayName("en"))
        
        // 测试其他语言
        val otherLanguage = "fr"
        val expectedName = Locale(otherLanguage).displayLanguage
        assertEquals(expectedName, LanguageHelper.getLanguageDisplayName(otherLanguage))
    }

    @Test
    fun `getSupportedLanguages 应该返回支持的语言列表`() {
        val languages = LanguageHelper.getSupportedLanguages()
        
        // 验证列表包含英文和中文
        assertTrue(languages.any { it.first == "en" && it.second == "English" })
        assertTrue(languages.any { it.first == "zh" && it.second == "中文 (Chinese)" })
        
        // 验证列表大小
        assertEquals(2, languages.size)
    }
}

private fun assertTrue(condition: Boolean) {
    assertEquals(true, condition)
} 