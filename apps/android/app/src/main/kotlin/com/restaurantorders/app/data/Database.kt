// What the tablet stores, in SQLite through Room. One restaurant per tablet.
package com.restaurantorders.app.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** The restaurant's settings: one row, id 1. */
@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey val id: Int = 1,
    val restaurantName: String,
    val phone: String = "",
    val address: String = "",
    /** Sales tax as a decimal, e.g. 0.08875. */
    val taxRate: Double,
    /** IANA time zone; the business day and order numbers follow it. */
    val timezone: String,
    /** EN, ZH or BOTH: which dish names print on tickets. */
    val ticketLanguage: String = "EN",
    /** The kitchen printer's IP address; empty means tickets only show on screen. */
    val printerHost: String = "",
    val printerPort: Int = 9100,
    /** Print Chinese characters (GB18030). Turn on only after a test ticket shows they work. */
    val printerChinese: Boolean = false,
)

@Entity(tableName = "staff")
data class StaffEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** OWNER, MANAGER or CASHIER. */
    val role: String,
    /** "salt:hash" of the 4-digit PIN; see Pins. */
    val pinHash: String,
    val active: Boolean = true,
)

/**
 * The whole menu as one JSON document (categories, dishes, options, modifier lists). A single
 * restaurant's menu is small, and orders keep their own copies of names and prices.
 */
@Entity(tableName = "menu")
data class MenuEntity(@PrimaryKey val id: Int = 1, val json: String)

@Entity(
    tableName = "orders",
    indices = [Index(value = ["businessDate", "number"], unique = true)],
)
data class OrderEntity(
    @PrimaryKey val id: String,
    /** Short number on the ticket; starts again at 1 each business day. */
    val number: Int,
    /** YYYY-MM-DD in the restaurant's time zone. */
    val businessDate: String,
    val type: String,
    val status: String,
    val customerName: String?,
    val customerPhone: String?,
    /** Epoch milliseconds, or null for "as soon as ready". */
    val pickupAt: Long?,
    val note: String?,
    /** JSON list of OrderLine: names and prices as they were when ordered. */
    val linesJson: String,
    val subtotalCents: Int,
    val taxCents: Int,
    val totalCents: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val readyAt: Long? = null,
    val createdById: String,
    val createdByName: String,
    /** Goes up by one every time the order is changed. */
    val version: Int = 1,
)

@Entity(tableName = "print_jobs", indices = [Index("orderId")])
data class PrintJobEntity(
    @PrimaryKey val id: String,
    /** Null for test tickets. */
    val orderId: String?,
    /** NEW, CHANGED, CANCELLED, REPRINT or TEST. */
    val kind: String,
    /** QUEUED, PRINTED, FAILED or NO_PRINTER. */
    val status: String,
    val createdAt: Long,
    /** JSON list of TicketLine: the ticket exactly as sent. */
    val ticketJson: String,
    /** Why it didn't print, in words for the cashier. */
    val error: String? = null,
)

@Dao
interface SettingsDao {
    @Query("SELECT * FROM settings WHERE id = 1")
    fun watch(): Flow<SettingsEntity?>

    @Query("SELECT * FROM settings WHERE id = 1")
    suspend fun get(): SettingsEntity?

    @Upsert
    suspend fun save(settings: SettingsEntity)
}

@Dao
interface StaffDao {
    @Query("SELECT * FROM staff ORDER BY active DESC, name")
    fun watchAll(): Flow<List<StaffEntity>>

    @Query("SELECT * FROM staff")
    suspend fun all(): List<StaffEntity>

    @Query("SELECT * FROM staff WHERE id = :id")
    suspend fun get(id: String): StaffEntity?

    @Insert
    suspend fun insert(staff: StaffEntity)

    @Update
    suspend fun update(staff: StaffEntity)
}

@Dao
interface MenuDao {
    @Query("SELECT json FROM menu WHERE id = 1")
    fun watch(): Flow<String?>

    @Query("SELECT json FROM menu WHERE id = 1")
    suspend fun get(): String?

    @Upsert
    suspend fun save(menu: MenuEntity)
}

@Dao
interface OrderDao {
    @Query("SELECT * FROM orders WHERE businessDate = :date ORDER BY number DESC")
    fun watchDay(date: String): Flow<List<OrderEntity>>

    @Query("SELECT * FROM orders WHERE id = :id")
    fun watch(id: String): Flow<OrderEntity?>

    @Query("SELECT * FROM orders WHERE id = :id")
    suspend fun get(id: String): OrderEntity?

    @Query("SELECT COALESCE(MAX(number), 0) + 1 FROM orders WHERE businessDate = :date")
    suspend fun nextNumber(date: String): Int

    @Insert
    suspend fun insert(order: OrderEntity)

    @Update
    suspend fun update(order: OrderEntity)

    @Query("SELECT * FROM orders ORDER BY createdAt")
    suspend fun all(): List<OrderEntity>
}

@Dao
interface PrintJobDao {
    @Query("SELECT * FROM print_jobs WHERE orderId = :orderId ORDER BY createdAt")
    fun watchForOrder(orderId: String): Flow<List<PrintJobEntity>>

    @Query("SELECT * FROM print_jobs WHERE id = :id")
    suspend fun get(id: String): PrintJobEntity?

    @Query("SELECT * FROM print_jobs WHERE status = 'QUEUED' ORDER BY createdAt")
    suspend fun queued(): List<PrintJobEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(job: PrintJobEntity)

    @Query("UPDATE print_jobs SET status = :status, error = :error WHERE id = :id")
    suspend fun setStatus(id: String, status: String, error: String?)
}

@Database(
    entities = [SettingsEntity::class, StaffEntity::class, MenuEntity::class, OrderEntity::class, PrintJobEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun settings(): SettingsDao
    abstract fun staff(): StaffDao
    abstract fun menu(): MenuDao
    abstract fun orders(): OrderDao
    abstract fun printJobs(): PrintJobDao
}
