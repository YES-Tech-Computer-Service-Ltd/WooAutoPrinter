package com.example.wooauto.domain.usecases.settings

import com.example.wooauto.domain.repositories.FakeSettingRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ApiSettingsUseCaseTest {
    private lateinit var fakeRepository: FakeSettingRepository
    private lateinit var apiSettingsUseCase: ApiSettingsUseCase

    @Before
    fun setup() {
        fakeRepository = FakeSettingRepository()
        apiSettingsUseCase = ApiSettingsUseCase(fakeRepository)
    }

    @Test
    fun `get API URL returns default value`() = runBlocking {
        val result = apiSettingsUseCase.getApiUrl().first()
        assertEquals("https://default.api.com", result)
    }

    @Test
    fun `set API URL updates value correctly`() = runBlocking {
        val newUrl = "https://example.com"
        apiSettingsUseCase.setApiUrl(newUrl)
        val result = apiSettingsUseCase.getApiUrl().first()
        assertEquals(newUrl, result)
    }

    @Test
    fun `get Consumer Key returns empty by default`() = runBlocking {
        val result = apiSettingsUseCase.getConsumerKey().first()
        assertEquals("", result)
    }

    @Test
    fun `set Consumer Key updates value correctly`() = runBlocking {
        val newKey = "ck_test123"
        apiSettingsUseCase.setConsumerKey(newKey)
        val result = apiSettingsUseCase.getConsumerKey().first()
        assertEquals(newKey, result)
    }

    @Test
    fun `get Consumer Secret returns empty by default`() = runBlocking {
        val result = apiSettingsUseCase.getConsumerSecret().first()
        assertEquals("", result)
    }

    @Test
    fun `set Consumer Secret updates value correctly`() = runBlocking {
        val newSecret = "cs_test123"
        apiSettingsUseCase.setConsumerSecret(newSecret)
        val result = apiSettingsUseCase.getConsumerSecret().first()
        assertEquals(newSecret, result)
    }
} 