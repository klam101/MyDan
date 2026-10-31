// The app's rules on top of the database, ported from apps/frontend/src/data/mockApi.ts.
package com.restaurantorders.app.data

import com.restaurantorders.domain.Menu
import com.restaurantorders.domain.Permission
import com.restaurantorders.domain.Role
import com.restaurantorders.domain.TicketLanguage
import com.restaurantorders.domain.assignableRoles
import com.restaurantorders.domain.can
import com.restaurantorders.domain.canManage
import com.restaurantorders.domain.loadStarterMenu
import com.restaurantorders.printing.PrintMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

/** A problem the person using the tablet can fix; the message is shown as is. */
class ActionException(message: String) : Exception(message)

val AppJson = Json { ignoreUnknownKeys = true }

fun newId(): String = UUID.randomUUID().toString()

data class Staff(
    val id: String,
    val name: String,
    val role: Role,
    val active: Boolean,
    /** Which name the menu buttons show first for this person (backlog B6). */
    val menuLanguage: TicketLanguage = TicketLanguage.EN,
) {
    fun can(permission: Permission) = can(role, permission)
}

private fun StaffEntity.toStaff() = Staff(id, name, Role.valueOf(role), active, TicketLanguage.valueOf(menuLanguage))

/** A manager's PIN letting a cashier void, discount or change a price, kept with the order. */
@Serializable
data class Approval(val permission: Permission, val byName: String, val forName: String, val at: Long, val what: String)

val SettingsEntity.language: TicketLanguage get() = TicketLanguage.valueOf(ticketLanguage)
val SettingsEntity.zone: ZoneId get() = ZoneId.of(timezone)

/** How tickets go to the printer; an unknown value (from a newer app) falls back to text. */
val SettingsEntity.printMode: PrintMode get() = runCatching { PrintMode.valueOf(printerMode) }.getOrDefault(PrintMode.TEXT)

/** Who is using the tablet right now. Locking the screen signs them out. */
class Session {
    private val current = MutableStateFlow<Staff?>(null)
    val staff: StateFlow<Staff?> = current.asStateFlow()

    fun signIn(staff: Staff) {
        current.value = staff
    }

    fun lock() {
        current.value = null
    }

    /** The signed-in person, checked against a permission. */
    fun require(permission: Permission? = null): Staff {
        val staff = current.value ?: throw ActionException("Sign in with your PIN first")
        if (permission != null && !staff.can(permission)) throw ActionException("A ${staff.role.name.lowercase()} can't do that")
        return staff
    }
}

class SettingsRepository(private val dao: SettingsDao, private val session: Session) {
    fun watch(): Flow<SettingsEntity?> = dao.watch()

    suspend fun get(): SettingsEntity = dao.get() ?: throw ActionException("The restaurant isn't set up yet")

    suspend fun save(settings: SettingsEntity) {
        session.require(Permission.SETTINGS)
        dao.save(checked(settings))
    }

    internal suspend fun create(settings: SettingsEntity) = dao.save(checked(settings))

    private fun checked(s: SettingsEntity): SettingsEntity {
        if (s.restaurantName.isBlank()) throw ActionException("The restaurant needs a name")
        if (s.taxRate !in 0.0..0.5) throw ActionException("Tax rate looks wrong; enter it as a percent, e.g. 8.875")
        runCatching { ZoneId.of(s.timezone) }.getOrElse { throw ActionException("Unknown time zone ${s.timezone}") }
        if (s.printerPort !in 1..65535) throw ActionException("Printer port must be a number like 9100")
        return s.copy(restaurantName = s.restaurantName.trim(), printerHost = s.printerHost.trim())
    }
}

class StaffRepository(private val dao: StaffDao, private val session: Session, private val now: () -> Instant) {
    private var wrongInARow = 0
    private val locked = MutableStateFlow<Instant?>(null)

    /** Set while the PIN pad is locked after too many wrong PINs (backlog A3). */
    val lockedUntil: StateFlow<Instant?> = locked.asStateFlow()

    fun watchAll(): Flow<List<Staff>> = dao.watchAll().map { list -> list.map { it.toStaff() } }

    /** Whoever's active PIN this is takes over the tablet. */
    suspend fun signIn(pin: String): Staff = match(pin).also { session.signIn(it) }

    /**
     * A manager or owner lets the signed-in person do one thing their role can't, without taking
     * over the tablet. Returns who approved it.
     */
    suspend fun approve(pin: String, permission: Permission): Staff {
        val approver = match(pin)
        if (!approver.can(permission)) throw ActionException("${approver.name} can't approve that; ask a manager or owner")
        return approver
    }

    /**
     * Who answers for a risky action: the signed-in person if their role allows it, otherwise the
     * manager who approved it with their PIN. Throws when neither may.
     */
    suspend fun authorize(permission: Permission, approvedById: String?): Pair<Staff, Staff?> {
        val actor = session.require()
        if (actor.can(permission)) return actor to null
        val approver = approvedById?.let { dao.get(it) }?.takeIf { it.active }?.toStaff()
        if (approver == null || !approver.can(permission)) throw ActionException("This needs a manager's PIN")
        return actor to approver
    }

    /** Remembers which name the signed-in person wants first on the menu buttons. */
    suspend fun setMenuLanguage(language: TicketLanguage) {
        val me = session.require()
        val current = dao.get(me.id) ?: return
        dao.update(current.copy(menuLanguage = language.name))
        session.signIn(me.copy(menuLanguage = language))
    }

    /** Five wrong PINs in a row lock the pad for 30 seconds, for sign-in and approvals alike. */
    private suspend fun match(pin: String): Staff {
        locked.value?.let { until ->
            val left = Duration.between(now(), until).seconds
            if (left > 0) throw ActionException("Too many wrong PINs. Try again in $left seconds")
            locked.value = null
        }
        val match = dao.all().firstOrNull { it.active && Pins.matches(pin, it.pinHash) }
        if (match == null) {
            wrongInARow++
            if (wrongInARow >= MAX_WRONG_PINS) {
                wrongInARow = 0
                locked.value = now().plus(LOCK_TIME)
                throw ActionException("Too many wrong PINs. Try again in ${LOCK_TIME.seconds} seconds")
            }
            throw ActionException("That PIN doesn't match anyone here")
        }
        wrongInARow = 0
        return match.toStaff()
    }

    companion object {
        const val MAX_WRONG_PINS = 5
        val LOCK_TIME: Duration = Duration.ofSeconds(30)
    }

    suspend fun add(name: String, role: Role, pin: String): Staff {
        val actor = session.require(Permission.STAFF)
        if (role !in assignableRoles(actor.role)) throw ActionException("Your role can't add a ${role.name.lowercase()}")
        return insert(name, role, pin)
    }

    internal suspend fun insert(name: String, role: Role, pin: String): Staff {
        if (name.isBlank()) throw ActionException("Enter their name")
        checkPin(pin, exceptId = null)
        val entity = StaffEntity(newId(), name.trim(), role.name, Pins.hash(pin))
        dao.insert(entity)
        return entity.toStaff()
    }

    suspend fun update(id: String, name: String? = null, role: Role? = null, active: Boolean? = null, pin: String? = null): Staff {
        val actor = session.require(Permission.STAFF)
        val current = dao.get(id) ?: throw ActionException("Staff member not found")
        val currentRole = Role.valueOf(current.role)
        if (!canManage(actor.role, currentRole)) throw ActionException("Your role can't change this person")
        if (role != null && role !in assignableRoles(actor.role)) throw ActionException("Your role can't give that role")
        val losesOwner = currentRole == Role.OWNER && ((role != null && role != Role.OWNER) || active == false)
        if (losesOwner && dao.all().count { it.active && it.role == Role.OWNER.name } == 1) {
            throw ActionException("The restaurant needs at least one active owner")
        }
        if (name != null && name.isBlank()) throw ActionException("Enter their name")
        if (pin != null) checkPin(pin, exceptId = id)
        val updated = current.copy(
            name = name?.trim() ?: current.name,
            role = role?.name ?: current.role,
            active = active ?: current.active,
            pinHash = pin?.let { Pins.hash(it) } ?: current.pinHash,
        )
        dao.update(updated)
        return updated.toStaff()
    }

    private suspend fun checkPin(pin: String, exceptId: String?) {
        if (!Pins.isValid(pin)) throw ActionException("A PIN is 4 digits")
        if (dao.all().any { it.id != exceptId && Pins.matches(pin, it.pinHash) }) {
            throw ActionException("Someone else here already uses that PIN")
        }
    }
}

class MenuRepository(private val dao: MenuDao, private val session: Session) {
    fun watch(): Flow<Menu?> = dao.watch().map { json -> json?.let { AppJson.decodeFromString<Menu>(it) } }

    suspend fun get(): Menu = dao.get()?.let { AppJson.decodeFromString<Menu>(it) } ?: throw ActionException("No menu yet")

    /** Saves a changed menu (managers and owners). */
    suspend fun save(menu: Menu) {
        session.require(Permission.MENU)
        write(menu)
    }

    /** Sold out / back on: every role may do this, unlike other menu changes. */
    suspend fun setAvailable(itemId: String, available: Boolean) {
        session.require(Permission.SOLD_OUT)
        val menu = get()
        write(
            menu.copy(
                categories = menu.categories.map { c ->
                    c.copy(items = c.items.map { if (it.id == itemId) it.copy(available = available) else it })
                },
            ),
        )
    }

    internal suspend fun write(menu: Menu) = dao.save(MenuEntity(json = AppJson.encodeToString(menu)))

    internal suspend fun importStarterMenu() = write(loadStarterMenu { newId() })
}
