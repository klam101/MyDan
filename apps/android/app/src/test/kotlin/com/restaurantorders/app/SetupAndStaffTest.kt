package com.restaurantorders.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.restaurantorders.app.data.ActionException
import com.restaurantorders.app.data.AppDatabase
import com.restaurantorders.app.data.SettingsEntity
import com.restaurantorders.app.printing.PrintStatus
import com.restaurantorders.app.printing.TicketSender
import com.restaurantorders.domain.Permission
import com.restaurantorders.domain.Role
import com.restaurantorders.domain.TicketLanguage
import com.restaurantorders.domain.TicketKind
import com.restaurantorders.domain.TicketLine
import com.restaurantorders.printing.PrinterException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** A printer that records what it was sent, or fails when told to. */
class FakePrinter : TicketSender {
    val sent = mutableListOf<ByteArray>()
    var failWith: String? = null

    override fun send(host: String, port: Int, bytes: ByteArray) {
        failWith?.let { throw PrinterException(it) }
        sent += bytes
    }
}

/** A clock the test moves by hand. */
class TestClock(var now: Instant = Instant.parse("2026-10-28T16:00:00Z")) {
    fun advance(seconds: Long) {
        now = now.plusSeconds(seconds)
    }
}

/** A container on an in-memory database, set up like a new tablet with an owner (PIN 1111). */
fun testApp(printer: FakePrinter = FakePrinter(), clock: TestClock? = null): AppContainer {
    val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    val app = if (clock == null) AppContainer(db, printer) else AppContainer(db, printer, now = { clock.now })
    runBlocking {
        app.setUp(
            SettingsEntity(restaurantName = "Chinatown Kitchen", taxRate = 0.08875, timezone = "America/New_York", printerHost = "10.0.0.9"),
            ownerName = "Alex",
            ownerPin = "1111",
        )
    }
    return app
}

@RunWith(RobolectricTestRunner::class)
class SetupAndStaffTest {
    private val printer = FakePrinter()
    private val clock = TestClock()
    private lateinit var app: AppContainer

    @Before
    fun setUp() {
        app = testApp(printer, clock)
    }

    @After
    fun tearDown() = app.db.close()

    @Test
    fun `setup loads the starter menu and signs the owner in`() = runBlocking {
        assertEquals("Alex", app.session.staff.value?.name)
        assertEquals(182, app.menu.get().categories.sumOf { it.items.size })
        assertEquals(4, app.menu.get().modifierLists.size)
        assertEquals(2, app.menu.get().choiceGroups.size)
    }

    @Test
    fun `staff sign in with their PIN`() = runBlocking {
        app.staff.add("Tony", Role.CASHIER, "3333")
        app.session.lock()
        assertNull(app.session.staff.value)
        assertEquals("Tony", app.staff.signIn("3333").name)
        assertContains(assertFailsWith<ActionException> { app.staff.signIn("9999") }.message!!, "doesn't match")
    }

    @Test
    fun `PINs are 4 digits and unique`() = runBlocking {
        assertContains(assertFailsWith<ActionException> { app.staff.add("Tony", Role.CASHIER, "12") }.message!!, "4 digits")
        assertContains(assertFailsWith<ActionException> { app.staff.add("Tony", Role.CASHIER, "1111") }.message!!, "already uses")
    }

    @Test
    fun `managers manage cashiers only, cashiers manage nobody`() = runBlocking {
        val manager = app.staff.add("Mei", Role.MANAGER, "2222")
        app.staff.add("Tony", Role.CASHIER, "3333")
        app.staff.signIn("2222")
        app.staff.add("Sam", Role.CASHIER, "4444")
        assertFailsWith<ActionException> { app.staff.add("Bo", Role.MANAGER, "5555") }
        assertFailsWith<ActionException> { app.staff.update(manager.id, active = false) }
        app.staff.signIn("3333")
        assertFailsWith<ActionException> { app.staff.add("Bo", Role.CASHIER, "5555") }
        Unit
    }

    @Test
    fun `the last owner can't be removed`() = runBlocking {
        val owner = app.session.staff.value!!
        assertContains(assertFailsWith<ActionException> { app.staff.update(owner.id, role = Role.MANAGER) }.message!!, "owner")
    }

    @Test
    fun `only owners change settings, but everyone marks dishes sold out`() = runBlocking {
        app.staff.add("Tony", Role.CASHIER, "3333")
        app.staff.signIn("3333")
        assertFailsWith<ActionException> { app.settings.save(app.settings.get().copy(restaurantName = "X")) }
        assertFailsWith<ActionException> { app.menu.save(app.menu.get()) }
        val dish = app.menu.get().categories.first().items.first()
        app.menu.setAvailable(dish.id, false)
        assertEquals(false, app.menu.get().categories.first().items.first().available)
    }

    @Test
    fun `tickets are stored, then printed or marked failed`() = runBlocking {
        val ticket = listOf(TicketLine("TEST"))
        val printed = app.printing.print(null, TicketKind.TEST, ticket)
        assertEquals(PrintStatus.PRINTED.name, printed.status)
        assertEquals(1, printer.sent.size)

        printer.failWith = "The printer at 10.0.0.9 didn't answer."
        val failed = app.printing.print(null, TicketKind.TEST, ticket)
        assertEquals(PrintStatus.FAILED.name, failed.status)
        assertEquals("The printer at 10.0.0.9 didn't answer.", failed.error)

        printer.failWith = null
        assertEquals(PrintStatus.PRINTED.name, app.printing.retry(failed.id).status)
        assertEquals(PrintStatus.PRINTED.name, app.db.printJobs().get(failed.id)!!.status)
    }

    @Test
    fun `without a printer IP tickets only show on screen`() = runBlocking {
        app.settings.save(app.settings.get().copy(printerHost = ""))
        assertEquals(PrintStatus.NO_PRINTER.name, app.printing.print(null, TicketKind.TEST, listOf(TicketLine("x"))).status)
        assertEquals(0, printer.sent.size)
    }
    @Test
    fun `five wrong PINs in a row lock the pad for 30 seconds`() = runBlocking {
        repeat(4) { assertContains(assertFailsWith<ActionException> { app.staff.signIn("9999") }.message!!, "doesn't match") }
        assertContains(assertFailsWith<ActionException> { app.staff.signIn("9999") }.message!!, "Try again in 30 seconds")
        // Even the right PIN is refused while locked.
        assertContains(assertFailsWith<ActionException> { app.staff.signIn("1111") }.message!!, "Too many wrong PINs")
        clock.advance(31)
        assertEquals("Alex", app.staff.signIn("1111").name)
    }

    @Test
    fun `a right PIN resets the count of wrong ones`() = runBlocking {
        repeat(4) { assertFailsWith<ActionException> { app.staff.signIn("9999") } }
        app.staff.signIn("1111")
        repeat(4) { assertFailsWith<ActionException> { app.staff.signIn("9999") } }
        assertEquals("Alex", app.staff.signIn("1111").name)
    }

    @Test
    fun `a cashier needs a manager's PIN for risky actions`() = runBlocking {
        val manager = app.staff.add("Mei", Role.MANAGER, "2222")
        val cashier = app.staff.add("Tony", Role.CASHIER, "3333")
        app.staff.signIn("3333")
        assertContains(assertFailsWith<ActionException> { app.staff.authorize(Permission.VOID, null) }.message!!, "manager's PIN")
        assertContains(assertFailsWith<ActionException> { app.staff.approve("3333", Permission.VOID) }.message!!, "can't approve")
        val approver = app.staff.approve("2222", Permission.VOID)
        assertEquals(manager.id, approver.id)
        // The tablet stays signed in as the cashier.
        assertEquals(cashier.id, app.session.staff.value!!.id)
        assertEquals(cashier.id to manager.id, app.staff.authorize(Permission.VOID, approver.id).let { it.first.id to it.second!!.id })
        app.staff.signIn("2222")
        assertNull(app.staff.authorize(Permission.DISCOUNT, null).second)
    }

    @Test
    fun `each person's menu language is remembered`() = runBlocking {
        app.staff.setMenuLanguage(TicketLanguage.ZH)
        app.session.lock()
        assertEquals(TicketLanguage.ZH, app.staff.signIn("1111").menuLanguage)
    }
}
