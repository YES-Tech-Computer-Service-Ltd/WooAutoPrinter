package com.wooauto.domain.usecases.settings

import com.wooauto.domain.repositories.FakeSettingRepository
import com.example.wooauto.domain.usecases.settings.NotificationSettingsUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class NotificationSettingsUseCaseTest {
    private lateinit var fakeRepository: FakeSettingRepository
    private lateinit var notificationSettingsUseCase: NotificationSettingsUseCase

    @Before
    fun setup() {
        fakeRepository = FakeSettingRepository()
        notificationSettingsUseCase = NotificationSettingsUseCase(fakeRepository)
    }

    @Test
    fun `get notification enabled returns false by default`() = runBlocking {
        val result = notificationSettingsUseCase.getNotificationEnabled().first()
        assertEquals(false, result)
    }

    @Test
    fun `set notification enabled updates value correctly`() = runBlocking {
        notificationSettingsUseCase.setNotificationEnabled(true)
        val result = notificationSettingsUseCase.getNotificationEnabled().first()
        assertEquals(true, result)
    }
} 