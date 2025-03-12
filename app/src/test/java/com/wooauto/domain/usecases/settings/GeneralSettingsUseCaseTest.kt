package com.wooauto.domain.usecases.settings

import com.wooauto.domain.repositories.FakeSettingRepository
import com.example.wooauto.domain.usecases.settings.GeneralSettingsUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class GeneralSettingsUseCaseTest {
    private lateinit var fakeRepository: FakeSettingRepository
    private lateinit var generalSettingsUseCase: GeneralSettingsUseCase

    @Before
    fun setup() {
        fakeRepository = FakeSettingRepository()
        generalSettingsUseCase = GeneralSettingsUseCase(fakeRepository)
    }

    @Test
    fun `get currency returns USD by default`() = runBlocking {
        val result = generalSettingsUseCase.getCurrency().first()
        assertEquals("USD", result)
    }

    @Test
    fun `set currency updates value correctly`() = runBlocking {
        val newCurrency = "EUR"
        generalSettingsUseCase.setCurrency(newCurrency)
        val result = generalSettingsUseCase.getCurrency().first()
        assertEquals(newCurrency, result)
    }

    @Test
    fun `get language returns en by default`() = runBlocking {
        val result = generalSettingsUseCase.getLanguage().first()
        assertEquals("en", result)
    }

    @Test
    fun `set language updates value correctly`() = runBlocking {
        val newLanguage = "zh"
        generalSettingsUseCase.setLanguage(newLanguage)
        val result = generalSettingsUseCase.getLanguage().first()
        assertEquals(newLanguage, result)
    }
} 