package com.example.wooauto.domain.usecases.settings

import com.example.wooauto.domain.repositories.FakeSettingRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class PrinterSettingsUseCaseTest {
    private lateinit var fakeRepository: FakeSettingRepository
    private lateinit var printerSettingsUseCase: PrinterSettingsUseCase

    @Before
    fun setup() {
        fakeRepository = FakeSettingRepository()
        printerSettingsUseCase = PrinterSettingsUseCase(fakeRepository)
    }

    @Test
    fun `get printer type returns empty by default`() = runBlocking {
        val result = printerSettingsUseCase.getPrinterType().first()
        assertEquals("", result)
    }

    @Test
    fun `set printer type updates value correctly`() = runBlocking {
        val newType = "EPSON"
        printerSettingsUseCase.setPrinterType(newType)
        val result = printerSettingsUseCase.getPrinterType().first()
        assertEquals(newType, result)
    }

    @Test
    fun `get printer connection returns false by default`() = runBlocking {
        val result = printerSettingsUseCase.getPrinterConnection().first()
        assertEquals(false, result)
    }

    @Test
    fun `set printer connection updates value correctly`() = runBlocking {
        printerSettingsUseCase.setPrinterConnection(true)
        val result = printerSettingsUseCase.getPrinterConnection().first()
        assertEquals(true, result)
    }
} 