package io.github.romulomga.corekit.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserSwitchSinkTest {
    private val recorded = RecordingSink()
    private val switches = mutableListOf<Boolean>()

    @Test
    fun `switched off at launch nothing goes out`() {
        val sink = UserSwitchSink(recorded, enabled = false) { switches += it }

        sink.log("first_open")
        sink.screen("home")

        assertTrue(recorded.events.isEmpty())
        assertTrue(recorded.screens.isEmpty())
        assertTrue(switches.isEmpty())
    }

    @Test
    fun `switched off and back on the SDK is told each time and events resume`() {
        val sink = UserSwitchSink(recorded, enabled = true) { switches += it }

        sink.setCollectionEnabled(false)
        sink.log("while_off")
        sink.setCollectionEnabled(true)
        sink.log("back_on")

        assertEquals(listOf(false, true), switches)
        assertEquals(listOf("back_on"), recorded.events)
    }

    @Test
    fun `the same position twice tells the SDK once`() {
        val sink = UserSwitchSink(recorded, enabled = true) { switches += it }

        sink.setCollectionEnabled(true)
        sink.setCollectionEnabled(false)
        sink.setCollectionEnabled(false)

        assertEquals(listOf(false), switches)
        assertFalse(sink.isEnabled)
    }

    @Test
    fun `an error travels without its message`() {
        val sink = UserSwitchSink(recorded, enabled = true)

        sink.recordError(IllegalStateException("Maria da Silva, CPF 123"), context = "save")

        val error = recorded.errors.single()
        assertEquals("java.lang.IllegalStateException", error.message)
        assertTrue(error.stackTrace.isNotEmpty())
        assertEquals(null, error.cause)
    }

    @Test
    fun `the composite passes the switch to every sink`() {
        val other = RecordingSink()
        val composite = CompositeAnalyticsSink(listOf(UserSwitchSink(recorded, true) { switches += it }, other))

        composite.setCollectionEnabled(false)

        assertEquals(listOf(false), switches)
        assertEquals(listOf(false), other.switches)
    }

    @Test
    fun `the user and the crash trail go out while the switch is on`() {
        val sink = UserSwitchSink(recorded, enabled = true)

        sink.setUserId("user-7f3a")
        sink.setCrashKey("plan", "pro")
        sink.addBreadcrumb("abriu o editor")
        sink.setUserId(null)

        assertEquals(listOf("user-7f3a", null), recorded.userIds)
        assertEquals(mapOf("plan" to "pro"), recorded.crashKeys)
        assertEquals(listOf("abriu o editor"), recorded.breadcrumbs)
    }

    /** A sessão restaurada com a chave desligada: o id chega quando ela volta a ligar, e nada mais é reenviado. */
    @Test
    fun `an id given while switched off reaches the service when it comes back on`() {
        val sink = UserSwitchSink(recorded, enabled = false)

        sink.setUserId("user-9b21")
        sink.setCrashKey("plan", "free")

        assertTrue(recorded.userIds.isEmpty())

        sink.setCollectionEnabled(true)

        assertEquals(listOf<String?>("user-9b21"), recorded.userIds)
        assertTrue(recorded.crashKeys.isEmpty())
    }

    @Test
    fun `the composite hands the user to every sink`() {
        val other = RecordingSink()

        CompositeAnalyticsSink(listOf(recorded, other)).setUserId("user-7f3a")

        assertEquals(listOf<String?>("user-7f3a"), recorded.userIds)
        assertEquals(listOf<String?>("user-7f3a"), other.userIds)
    }
}

private class RecordingSink : AnalyticsSink {
    val events = mutableListOf<String>()
    val screens = mutableListOf<String>()
    val errors = mutableListOf<Throwable>()
    val switches = mutableListOf<Boolean>()

    override fun log(name: String, parameters: Map<String, Any>) {
        events += name
    }

    override fun screen(name: String) {
        screens += name
    }

    override fun setUserProperty(name: String, value: String?): Unit = Unit

    override fun recordError(error: Throwable, context: String?) {
        errors += error
    }

    override fun logPurchase(purchase: PurchaseRevenue): Unit = Unit

    override fun setCollectionEnabled(enabled: Boolean) {
        switches += enabled
    }

    val userIds = mutableListOf<String?>()
    val crashKeys = mutableMapOf<String, String>()
    val breadcrumbs = mutableListOf<String>()

    override fun setUserId(id: String?) {
        userIds += id
    }

    override fun setCrashKey(key: String, value: String) {
        crashKeys[key] = value
    }

    override fun addBreadcrumb(message: String) {
        breadcrumbs += message
    }
}
