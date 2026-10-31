package com.restaurantorders.app

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.restaurantorders.app.data.AppDatabase
import com.restaurantorders.app.data.AppJson
import com.restaurantorders.app.data.MIGRATIONS
import com.restaurantorders.domain.Menu
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import kotlin.test.assertEquals

/**
 * An app update must keep the restaurant's data. Builds a database exactly as an older version
 * made it (from the schema saved in app/schemas), fills it, then opens it with today's app:
 * Room runs the migrations and refuses to open if the result doesn't match the current schema.
 */
@RunWith(RobolectricTestRunner::class)
class DatabaseMigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "migration-test.db"

    /** Creates the tables of schema `version` the way Room did, and returns the open database. */
    private fun createOld(version: Int): SQLiteDatabase {
        val schema = Json.parseToJsonElement(File("schemas/com.restaurantorders.app.data.AppDatabase/$version.json").readText())
            .jsonObject.getValue("database").jsonObject
        context.deleteDatabase(name)
        val db = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name).apply { parentFile?.mkdirs() }, null)
        for (entity in schema.getValue("entities").jsonArray.map { it.jsonObject }) {
            val table = entity.getValue("tableName").jsonPrimitive.content
            db.execSQL(entity.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
            entity["indices"]?.jsonArray?.forEach { db.execSQL(it.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table)) }
        }
        schema.getValue("setupQueries").jsonArray.forEach { db.execSQL(it.jsonPrimitive.content) }
        db.version = version
        return db
    }

    @Test
    fun version1DataSurvivesTheUpgrade() {
        createOld(1).apply {
            execSQL(
                "INSERT INTO settings (id, restaurantName, phone, address, taxRate, timezone, ticketLanguage, printerHost, printerPort, printerChinese) " +
                    "VALUES (1, 'ChinaTown', '', '', 0.08875, 'America/New_York', 'BOTH', '192.168.1.50', 9100, 0)",
            )
            execSQL("INSERT INTO staff (id, name, role, pinHash, active) VALUES ('s1', 'Cheung', 'OWNER', 'salt:hash', 1)")
            execSQL("INSERT INTO menu (id, json) VALUES (1, '{\"categories\":[],\"modifierLists\":[]}')")
            execSQL(
                "INSERT INTO orders (id, number, businessDate, type, status, customerName, customerPhone, pickupAt, note, linesJson, " +
                    "subtotalCents, taxCents, totalCents, createdAt, updatedAt, readyAt, createdById, createdByName, version) " +
                    "VALUES ('o1', 7, '2026-10-28', 'WALK_IN', 'OPEN', NULL, NULL, NULL, NULL, '[]', 1000, 89, 1089, 1, 1, NULL, 's1', 'Cheung', 1)",
            )
            close()
        }

        val db = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(*MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        runBlocking {
            val settings = db.settings().get()!!
            assertEquals("ChinaTown", settings.restaurantName)
            assertEquals("192.168.1.50", settings.printerHost)
            assertEquals(2000, settings.openItemLimitCents)
            assertEquals("TEXT", settings.printerMode)
            assertEquals("EN", db.staff().get("s1")!!.menuLanguage)
            val order = db.orders().get("o1")!!
            assertEquals(1089, order.totalCents)
            assertEquals("[]", order.approvalsJson)
            assertEquals(null, order.discountJson)
            // The old menu document still reads with today's model (new fields take defaults).
            assertEquals(0, AppJson.decodeFromString<Menu>(db.menu().get()!!).categories.size)
        }
        db.close()
    }
}
