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
    @Query("SELECT * FROM orders")
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


    @Query("SELECT * FROM orders WHERE status = :status")
    fun getOrdersByStatus(status: String): Flow<List<OrderEntity>>
} 