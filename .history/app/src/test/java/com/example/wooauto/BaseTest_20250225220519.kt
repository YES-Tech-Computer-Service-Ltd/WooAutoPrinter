package com.example.wooauto

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Rule
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * 单元测试基类
 * 
 * 使用方法：
 * 1. 继承此类来创建你的测试类
 * 2. 使用 @Test 注解标记测试方法
 * 3. 使用 runTest 来执行协程测试
 * 
 * 示例：
 * ```
 * class YourTest : BaseTest() {
 *     @Test
 *     fun testSomething() = runTest {
 *         // 你的测试代码
 *     }
 * }
 * ```
 */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class BaseTest {
    
    /**
     * 用于在测试中立即执行 LiveData 的更新
     */
    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    /**
     * 用于管理协程测试的调度器
     */
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()
}

/**
 * 协程测试规则
 * 用于在测试中替换主线程调度器
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    private val testDispatcher: TestDispatcher = UnconfinedTestDispatcher()
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
} 