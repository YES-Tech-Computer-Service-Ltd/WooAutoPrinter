package com.wooauto.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.mockito.MockitoAnnotations

@OptIn(ExperimentalCoroutinesApi::class)
abstract class BaseRepositoryTest {
    
    protected val testDispatcher: TestDispatcher = UnconfinedTestDispatcher()
    private var closeable: AutoCloseable? = null

    @Before
    open fun setup() {
        closeable = MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(testDispatcher)
    }

    @After
    open fun tearDown() {
        Dispatchers.resetMain()
        closeable?.close()
    }
} 