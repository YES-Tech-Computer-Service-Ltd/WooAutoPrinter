package com.example.wooauto.data.local.dao

import androidx.room.*
import com.example.wooauto.data.local.entities.OrderEntity
import kotlinx.coroutines.flow.Flow

/**
 * 订单数据访问对象
 * 定义了对订单表的所有数据库操作
 */
@Dao
interface OrderDao {
    /**
     * 插入订单
     * @param order 要插入的订单实体
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrder(order: OrderEntity)

    /**
     * 获取所有订单
     * @return 订单列表流
     */
    @Query("SELECT * FROM orders ORDER BY dateCreated DESC")
    fun getAllOrders(): Flow<List<OrderEntity>>

    /**
     * 根据ID获取订单
     * @param orderId 订单ID
     * @return 订单实体
     */
    @Query("SELECT * FROM orders WHERE id = :orderId")
    suspend fun getOrderById(orderId: Long): OrderEntity?

    /**
     * 删除订单
     * @param order 要删除的订单实体
     */
    @Delete
    suspend fun deleteOrder(order: OrderEntity)

    /**
     * 更新订单
     * @param order 要更新的订单实体
     */
    @Update
    suspend fun updateOrder(order: OrderEntity)

    @Query("SELECT * FROM orders WHERE status = :status ORDER BY dateCreated DESC")
    fun getOrdersByStatus(status: String): Flow<List<OrderEntity>>

    @Query("SELECT * FROM orders WHERE customerName LIKE :query OR number LIKE :query OR contactInfo LIKE :query")
    fun searchOrders(query: String): Flow<List<OrderEntity>>

    @Query("SELECT * FROM orders WHERE dateCreated >= :timestamp ORDER BY dateCreated DESC")
    fun getOrdersAfterDate(timestamp: Long): Flow<List<OrderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrders(orders: List<OrderEntity>)

    @Query("DELETE FROM orders")
    suspend fun deleteAllOrders()

    @Query("UPDATE orders SET isPrinted = :isPrinted WHERE id = :orderId")
    suspend fun updateOrderPrintStatus(orderId: Long, isPrinted: Boolean)

    @Query("UPDATE orders SET notificationShown = :shown WHERE id = :orderId")
    suspend fun updateOrderNotificationStatus(orderId: Long, shown: Boolean)

    /**
     * 更新订单阅读状态
     * @param orderId 订单ID
     * @param isRead 是否已读
     */
    @Query("UPDATE orders SET isRead = :isRead WHERE id = :orderId")
    suspend fun updateOrderReadStatus(orderId: Long, isRead: Boolean)

    /**
     * 删除指定状态的所有订单
     * @param status 要删除的订单状态
     */
    @Query("DELETE FROM orders WHERE status = :status")
    suspend fun deleteOrdersByStatus(status: String)

    /**
     * 根据ID删除订单
     * @param orderId 要删除的订单ID
     */
    @Query("DELETE FROM orders WHERE id = :orderId")
    suspend fun deleteOrderById(orderId: Long)

    @Query("UPDATE orders SET isRead = 1 WHERE id = :orderId")
    suspend fun markOrderAsRead(orderId: Long)

    @Query("UPDATE orders SET isRead = 1")
    suspend fun markAllOrdersAsRead()

    @Query("SELECT id FROM orders WHERE isRead = 0")
    suspend fun getUnreadOrderIds(): List<Long>

    @Query("SELECT * FROM orders WHERE id IN (:orderIds)")
    suspend fun getOrdersByIds(orderIds: List<Long>): List<OrderEntity>

    /**
     * 获取所有订单ID列表
     * @return 所有订单ID
     */
    @Query("SELECT id FROM orders")
    suspend fun getAllOrderIds(): List<Long>
    
    /**
     * 删除指定ID列表中的订单，用于清理不存在但标记为未读的订单ID
     * @param ids 要删除的订单ID列表
     */
    @Query("DELETE FROM orders WHERE id IN (:ids)")
    suspend fun deleteOrdersByIds(ids: List<Long>)
    
    /**
     * 将指定ID列表的订单标记为已读
     * @param ids 要标记为已读的订单ID列表
     */
    @Query("UPDATE orders SET isRead = 1 WHERE id IN (:ids)")
    suspend fun markOrdersAsRead(ids: List<Long>)
} 