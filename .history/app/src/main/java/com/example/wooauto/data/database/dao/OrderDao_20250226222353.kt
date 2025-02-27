package com.example.wooauto.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.wooauto.data.database.entities.OrderEntity
import kotlinx.coroutines.flow.Flow
import java.util.Date

@Dao
interface OrderDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrder(order: OrderEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrders(orders: List<OrderEntity>)

    @Update
    suspend fun updateOrder(order: OrderEntity)

    @Query("SELECT * FROM orders WHERE id = :orderId")
    suspend fun getOrderById(orderId: Long): OrderEntity?

    @Query("SELECT * FROM orders ORDER BY date_created DESC")
    fun getAllOrdersFlow(): Flow<List<OrderEntity>>

    @Query("SELECT * FROM orders ORDER BY date_created DESC")
    suspend fun getAllOrders(): List<OrderEntity>

    @Query("SELECT * FROM orders WHERE status = :status ORDER BY date_created DESC")
    fun getOrdersByStatusFlow(status: String): Flow<List<OrderEntity>>

    @Query("SELECT * FROM orders WHERE status = :status ORDER BY date_created DESC")
    suspend fun getOrdersByStatus(status: String): List<OrderEntity>

    @Query("SELECT * FROM orders WHERE date_created > :date ORDER BY date_created DESC")
    suspend fun getOrdersAfterDate(date: Date): List<OrderEntity>

    @Query("SELECT * FROM orders WHERE order_method = :method ORDER BY date_created DESC")
    fun getOrdersByMethodFlow(method: String): Flow<List<OrderEntity>>

    @Query("SELECT * FROM orders WHERE delivery_date = :date ORDER BY delivery_time ASC")
    fun getOrdersByDeliveryDateFlow(date: String): Flow<List<OrderEntity>>

    @Query("SELECT * FROM orders WHERE delivery_date >= :startDate AND delivery_date <= :endDate ORDER BY delivery_time ASC")
    fun getOrdersByDeliveryDateRangeFlow(startDate: String, endDate: String): Flow<List<OrderEntity>>

    @Query("SELECT DISTINCT delivery_date FROM orders WHERE delivery_date IS NOT NULL ORDER BY delivery_date ASC")
    fun getDistinctDeliveryDatesFlow(): Flow<List<String>>

    @Query("SELECT * FROM orders WHERE number LIKE '%' || :query || '%' OR customer_name LIKE '%' || :query || '%' OR delivery_date LIKE '%' || :query || '%' ORDER BY date_created DESC")
    fun searchOrdersFlow(query: String): Flow<List<OrderEntity>>

    @Query("SELECT * FROM orders WHERE is_printed = 0 ORDER BY date_created DESC")
    suspend fun getUnprintedOrders(): List<OrderEntity>

    @Query("UPDATE orders SET is_printed = 1 WHERE id = :orderId")
    suspend fun markOrderAsPrinted(orderId: Long)

    @Query("UPDATE orders SET notification_shown = 1 WHERE id = :orderId")
    suspend fun markOrderNotificationShown(orderId: Long)

    @Query("UPDATE orders SET status = :status, last_updated = :timestamp WHERE id = :orderId")
    suspend fun updateOrderStatus(orderId: Long, status: String, timestamp: Date = Date())

    @Query("SELECT id FROM orders WHERE date_created > :date")
    suspend fun getOrderIdsAfterDate(date: Date): List<Long>

    @Query("DELETE FROM orders WHERE id = :orderId")
    suspend fun deleteOrder(orderId: Long)

    @Query("DELETE FROM orders")
    suspend fun deleteAllOrders()

    @Query("UPDATE orders SET delivery_date = :date, delivery_time = :time, order_method = :method, tip = :tip, delivery_fee = :fee WHERE id = :orderId")
    suspend fun updateOrderDeliveryInfo(
        orderId: Long,
        date: String?,
        time: String?,
        method: String?,
        tip: String?,
        fee: String?
    )
}
