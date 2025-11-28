package com.example.wooauto.data.mappers

import com.example.wooauto.data.local.entities.StoreEntity
import com.example.wooauto.domain.models.Store

object StoreMapper {
    fun toDomain(entity: StoreEntity): Store {
        return Store(
            id = entity.id,
            name = entity.name,
            siteUrl = entity.siteUrl,
            consumerKey = entity.consumerKey,
            consumerSecret = entity.consumerSecret,
            address = entity.address,
            phone = entity.phone,
            isActive = entity.isActive,
            isDefault = entity.isDefault
        )
    }

    fun toEntity(domain: Store): StoreEntity {
        return StoreEntity(
            id = domain.id,
            name = domain.name,
            siteUrl = domain.siteUrl,
            consumerKey = domain.consumerKey,
            consumerSecret = domain.consumerSecret,
            address = domain.address,
            phone = domain.phone,
            isActive = domain.isActive,
            isDefault = domain.isDefault
        )
    }
}

