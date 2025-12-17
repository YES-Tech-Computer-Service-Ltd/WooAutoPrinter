package com.example.wooauto.domain.models

/**
 * Selected store location (multi-store).
 *
 * - slug: used for filtering orders via `exwoofood_location={slug}`
 * - name/address: only for UI display
 */
data class StoreLocationSelection(
    val slug: String,
    val name: String,
    val address: String? = null
)


