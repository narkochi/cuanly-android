package com.narkochi.cuanly

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "products")
data class Product(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val sellPrice: Long,
    val costPrice: Long,
    val stock: Int,
    val lowStockThreshold: Int = 5
)

@Entity(tableName = "orders")
data class BusinessOrder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val customer: String,
    val productName: String,
    val quantity: Int,
    val total: Long,
    val status: String = "Diproses",
    val paymentStatus: String = "Belum dibayar",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "transactions")
data class BusinessTransaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val amount: Long,
    val type: String, // Pemasukan / Pengeluaran
    val category: String = "Umum",
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface CuanlyDao {
    @Query("SELECT * FROM products ORDER BY name")
    fun observeProducts(): Flow<List<Product>>

    @Insert
    suspend fun addProduct(product: Product)

    @Query("UPDATE products SET stock = :stock WHERE id = :id")
    suspend fun updateStock(id: Long, stock: Int)

    @Query("SELECT * FROM orders ORDER BY createdAt DESC")
    fun observeOrders(): Flow<List<BusinessOrder>>

    @Insert
    suspend fun addOrder(order: BusinessOrder)

    @Query("UPDATE orders SET status = :status WHERE id = :id")
    suspend fun updateOrderStatus(id: Long, status: String)

    @Query("SELECT * FROM transactions ORDER BY createdAt DESC")
    fun observeTransactions(): Flow<List<BusinessTransaction>>

    @Insert
    suspend fun addTransaction(transaction: BusinessTransaction)

    @Query("SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE type = 'Pemasukan'")
    fun observeIncome(): Flow<Long>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE type = 'Pengeluaran'")
    fun observeExpenses(): Flow<Long>

    @Query("SELECT COUNT(*) FROM orders WHERE status != 'Selesai' AND status != 'Dibatalkan'")
    fun observeActiveOrders(): Flow<Int>

    @Query("SELECT COUNT(*) FROM products WHERE stock <= lowStockThreshold")
    fun observeLowStockCount(): Flow<Int>
}

@Database(
    entities = [Product::class, BusinessOrder::class, BusinessTransaction::class],
    version = 1,
    exportSchema = false
)
abstract class CuanlyDatabase : RoomDatabase() {
    abstract fun dao(): CuanlyDao

    companion object {
        @Volatile private var INSTANCE: CuanlyDatabase? = null

        fun get(context: Context): CuanlyDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    CuanlyDatabase::class.java,
                    "cuanly.db"
                ).build().also { INSTANCE = it }
            }
    }
}
