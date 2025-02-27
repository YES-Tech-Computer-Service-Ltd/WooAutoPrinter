package com.wooauto.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.wooauto.data.local.db.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
abstract class BaseDaoTest {
    
    protected lateinit var database: AppDatabase
    protected val testDispatcher: TestDispatcher = UnconfinedTestDispatcher()

    @Before
    open fun setup() {
        Dispatchers.setMain(testDispatcher)
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    open fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }
} 