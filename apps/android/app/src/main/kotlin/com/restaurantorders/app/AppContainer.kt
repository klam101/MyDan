package com.restaurantorders.app

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import com.restaurantorders.app.data.AppDatabase
import com.restaurantorders.app.data.MenuEditor
import com.restaurantorders.app.data.MenuRepository
import com.restaurantorders.app.data.exportJson
import com.restaurantorders.app.data.OrderRepository
import com.restaurantorders.app.data.Session
import com.restaurantorders.app.data.SettingsEntity
import com.restaurantorders.app.data.SettingsRepository
import com.restaurantorders.app.data.Staff
import com.restaurantorders.app.data.StaffRepository
import com.restaurantorders.app.printing.PrintQueue
import com.restaurantorders.app.printing.TicketSender
import com.restaurantorders.app.printing.networkSender
import com.restaurantorders.domain.Role
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.time.Instant

/** Everything the screens use, made once when the app starts. Tests build one on an in-memory database. */
class AppContainer(
    val db: AppDatabase,
    sender: TicketSender = networkSender,
    val now: () -> Instant = Instant::now,
) {
    val session = Session()
    val settings = SettingsRepository(db.settings(), session)
    val staff = StaffRepository(db.staff(), session)
    val menu = MenuRepository(db.menu(), session)
    val menuEditor = MenuEditor(menu, session)
    val printing = PrintQueue(db.printJobs(), db.settings(), sender, now = now)

    /** Lives as long as the app; work started here isn't cancelled when a screen closes. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val orders = OrderRepository(db, menu, printing, session, appScope, now)

    /** Everything on the tablet as JSON (owners only). */
    suspend fun export(): String = exportJson(db, session, now())

    /** First run: the restaurant, its owner and the starter menu. Signs the owner in. */
    suspend fun setUp(settings: SettingsEntity, ownerName: String, ownerPin: String): Staff {
        val owner = db.withTransaction {
            this.settings.create(settings)
            menu.importStarterMenu()
            staff.insert(ownerName, Role.OWNER, ownerPin)
        }
        session.signIn(owner)
        return owner
    }

    companion object {
        fun create(context: Context) = AppContainer(
            Room.databaseBuilder(context, AppDatabase::class.java, "orders.db").build(),
        )
    }
}
