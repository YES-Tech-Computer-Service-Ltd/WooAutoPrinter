package com.example.wooauto.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.wooauto.data.local.entity.ReadStatus

/**
 * 订单阅读状态数据访问对象
 */
@Dao
interface ReadStatusDao {
    /**
     * 获取所有未读订单ID
     */
    @Query("SELECT orderId FROM read_status WHERE isRead = 0")
    suspend fun getUnreadOrderIds(): List<Long>
    
    /**
     * 标记订单为已读
     */
    @Query("UPDATE read_status SET isRead = 1 WHERE orderId = :orderId")
    suspend fun markOrderAsRead(orderId: Long)
    
    /**
     * 标记所有订单为已读
     */
    @Query("UPDATE read_status SET isRead = 1")
    suspend fun markAllOrdersAsRead()
    
    /**
     * 插入或更新订单阅读状态
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateReadStatus(readStatus: ReadStatus)
    
    /**
     * 批量插入或更新订单阅读状态
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateReadStatuses(readStatuses: List<ReadStatus>)
} 