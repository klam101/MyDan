package com.restaurantorders.app.data

import com.restaurantorders.domain.Permission
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant

private val Pretty = Json(AppJson) { prettyPrint = true }

/**
 * Everything on this tablet as one JSON file: settings, staff (names and roles, never PINs),
 * the menu and every order. A backup, and the way to move the data to the cloud version later.
 */
suspend fun exportJson(db: AppDatabase, session: Session, now: Instant): String {
    session.require(Permission.SETTINGS)
    val settings = db.settings().get()
    val menu = db.menu().get()
    val json = buildJsonObject {
        put("exportedAt", now.toString())
        put("app", "Restaurant Orders for Android")
        settings?.let { s ->
            put(
                "settings",
                buildJsonObject {
                    put("restaurantName", s.restaurantName)
                    put("phone", s.phone)
                    put("address", s.address)
                    put("taxRate", s.taxRate)
                    put("timezone", s.timezone)
                    put("ticketLanguage", s.ticketLanguage)
                    put("printerHost", s.printerHost)
                    put("printerPort", s.printerPort)
                },
            )
        }
        put(
            "staff",
            buildJsonArray {
                db.staff().all().forEach { s -> add(buildJsonObject { put("name", s.name); put("role", s.role); put("active", s.active) }) }
            },
        )
        menu?.let { put("menu", AppJson.parseToJsonElement(it)) }
        put(
            "orders",
            buildJsonArray {
                db.orders().all().forEach { o ->
                    add(
                        buildJsonObject {
                            put("number", o.number)
                            put("businessDate", o.businessDate)
                            put("type", o.type)
                            put("status", o.status)
                            put("customerName", o.customerName)
                            put("customerPhone", o.customerPhone)
                            put("note", o.note)
                            put("lines", AppJson.parseToJsonElement(o.linesJson))
                            put("subtotalCents", o.subtotalCents)
                            put("taxCents", o.taxCents)
                            put("totalCents", o.totalCents)
                            put("createdAt", Instant.ofEpochMilli(o.createdAt).toString())
                            put("createdBy", o.createdByName)
                            put("version", o.version)
                        },
                    )
                }
            },
        )
    }
    return Pretty.encodeToString(json)
}
