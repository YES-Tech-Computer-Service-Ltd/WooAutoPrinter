package com.wooauto.domain.usecases.settings

import com.wooauto.domain.repositories.FakeSettingRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class BusinessSettingsUseCaseTest {
    private lateinit var fakeRepository: FakeSettingRepository
    private lateinit var businessSettingsUseCase: BusinessSettingsUseCase

    @Before
    fun setup() {
        fakeRepository = FakeSettingRepository()
        businessSettingsUseCase = BusinessSettingsUseCase(fakeRepository)
    }

    @Test
    fun `get currency returns USD by default`() = runBlocking {
        val result = businessSettingsUseCase.getCurrency().first()
        assertEquals("USD", result)
    }

    @Test
    fun `set currency updates value correctly`() = runBlocking {
        val newCurrency = "EUR"
        businessSettingsUseCase.setCurrency(newCurrency)
        val result = businessSettingsUseCase.getCurrency().first()
        assertEquals(newCurrency, result)
    }

    @Test
    fun `get language returns en by default`() = runBlocking {
        val result = businessSettingsUseCase.getLanguage().first()
        assertEquals("en", result)
    }

    @Test
    fun `set language updates value correctly`() = runBlocking {
        val newLanguage = "zh"
        businessSettingsUseCase.setLanguage(newLanguage)
        val result = businessSettingsUseCase.getLanguage().first()
        assertEquals(newLanguage, result)
    }
} 