interface OrdersRepository {
    suspend fun getOrders(): List<Order>
    suspend fun getOrderDetails(id: Long): Order?
    suspend fun updateOrderStatus(id: Long, status: String): Boolean
    suspend fun printOrder(id: Long, template: TemplateType): Boolean
    
    // 添加未读订单相关方法
    suspend fun getUnreadOrders(): List<Order>
    suspend fun markOrderAsRead(orderId: Long): Boolean
    suspend fun markAllOrdersAsRead(): Boolean
}

class OrdersRepositoryImpl @Inject constructor(
    private val wooCommerceService: WooCommerceService,
    private val orderDao: OrderDao,
    private val readStatusDao: ReadStatusDao // 假设你有一个存储读取状态的DAO
) : OrdersRepository {
    
    // 实现获取未读订单的方法
    override suspend fun getUnreadOrders(): List<Order> = withContext(Dispatchers.IO) {
        try {
            // 获取未读订单
            val unreadIds = readStatusDao.getUnreadOrderIds()
            // 根据ID获取具体订单信息
            return@withContext orderDao.getOrdersByIds(unreadIds)
        } catch (e: Exception) {
            Log.e("OrdersRepository", "获取未读订单时发生错误", e)
            return@withContext emptyList()
        }
    }
    
    // 实现标记订单为已读的方法
    override suspend fun markOrderAsRead(orderId: Long): Boolean = withContext(Dispatchers.IO) {
        try {
            // 标记订单为已读
            readStatusDao.markOrderAsRead(orderId)
            return@withContext true
        } catch (e: Exception) {
            Log.e("OrdersRepository", "标记订单已读时发生错误", e)
            return@withContext false
        }
    }
    
    // 实现标记所有订单为已读的方法
    override suspend fun markAllOrdersAsRead(): Boolean = withContext(Dispatchers.IO) {
        try {
            // 标记所有订单为已读
            readStatusDao.markAllOrdersAsRead()
            return@withContext true
        } catch (e: Exception) {
            Log.e("OrdersRepository", "标记所有订单已读时发生错误", e)
            return@withContext false
        }
    }
    
    // 其他实现方法...
} 