package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34]) // Let's use SDK 34 for robust stability in Robolectric
class ExampleRobolectricTest {

    private lateinit var context: Context
    private lateinit var scanEngine: ScanEngine

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        scanEngine = ScanEngine(context)
    }

    @Test
    fun testAppName() {
        val appName = context.getString(R.string.app_name)
        assertEquals("Range Scanner", appName)
    }

    @Test
    fun testValidCidr24Expansion() {
        // /24 contains 256 addresses. Excludes network and broadcast -> 254 hosts.
        val ips = scanEngine.expandCidr("192.168.1.0/24")
        assertEquals(254, ips.size)
        assertEquals("192.168.1.1", ips.first())
        assertEquals("192.168.1.254", ips.last())
    }

    @Test
    fun testValidCidr32Expansion() {
        // /32 is a single host
        val ips = scanEngine.expandCidr("192.168.1.100/32")
        assertEquals(1, ips.size)
        assertEquals("192.168.1.100", ips[0])
    }

    @Test
    fun testValidCidr31Expansion() {
        // /31 is point-to-point. Includes both network and broadcast -> 2 hosts.
        val ips = scanEngine.expandCidr("192.168.1.0/31")
        assertEquals(2, ips.size)
        assertEquals("192.168.1.0", ips[0])
        assertEquals("192.168.1.1", ips[1])
    }

    @Test
    fun testValidCidr30Expansion() {
        // /30 is 4 addresses. Excludes network (0) and broadcast (3) -> 2 hosts (1, 2)
        val ips = scanEngine.expandCidr("192.168.1.0/30")
        assertEquals(2, ips.size)
        assertEquals("192.168.1.1", ips[0])
        assertEquals("192.168.1.2", ips[1])
    }

    @Test
    fun testInvalidCidrPrefix() {
        assertThrows(IllegalArgumentException::class.java) {
            scanEngine.expandCidr("192.168.1.1/33")
        }
    }

    @Test
    fun testInvalidCidrIpFormat() {
        assertThrows(IllegalArgumentException::class.java) {
            scanEngine.expandCidr("192.168.1.abc/24")
        }
    }

    @Test
    fun testOversizedCidrRange() {
        // Since we removed all limitations to allow full unlimited CIDR scans,
        // expanding a /15 range should succeed and yield the expected host list count.
        val ips = scanEngine.expandCidr("10.0.0.0/15")
        // A /15 subnet has 131,072 total addresses. Excluding network and broadcast: 131,070 hosts
        assertEquals(131070, ips.size)
    }

    @Test
    fun testParsePortsValid() {
        val ports = scanEngine.parsePorts("80, 443, 8080, 443")
        assertEquals(3, ports.size)
        assertEquals(80, ports[0])
        assertEquals(443, ports[1])
        assertEquals(8080, ports[2])
    }

    @Test
    fun testParsePortsInvalidValue() {
        assertThrows(IllegalArgumentException::class.java) {
            scanEngine.parsePorts("80, abc, 443")
        }
    }

    @Test
    fun testParsePortsOutOfRange() {
        assertThrows(IllegalArgumentException::class.java) {
            scanEngine.parsePorts("0, 80")
        }
        assertThrows(IllegalArgumentException::class.java) {
            scanEngine.parsePorts("80, 65536")
        }
    }

    @Test
    fun testGetNextScanDirectory() {
        val (numStr, dir) = scanEngine.getNextScanDirectory()
        assertNotNull(numStr)
        assertNotNull(dir)
        assertTrue(dir.exists())
        assertTrue(dir.isDirectory)
    }

    @Test
    fun testRoomDatabasePersistence() = kotlinx.coroutines.runBlocking {
        val db = AppDatabase.getDatabase(context)
        val dao = db.scanHistoryDao()
        
        // Clear history first
        dao.clearAll()
        
        // Check empty list
        var historyList = dao.getAllHistory().first()
        assertTrue(historyList.isEmpty())

        // Insert scan history log
        val log = ScanHistoryEntity(
            scanNumber = "05",
            cidr = "192.168.1.0/24",
            ports = "80,443",
            totalTargets = 254,
            liveHosts = 3,
            durationMs = 4500L,
            status = "Completed"
        )
        dao.insert(log)

        // Retrieve and assert
        historyList = dao.getAllHistory().first()
        assertEquals(1, historyList.size)
        val retrieved = historyList[0]
        assertEquals("05", retrieved.scanNumber)
        assertEquals("192.168.1.0/24", retrieved.cidr)
        assertEquals("80,443", retrieved.ports)
        assertEquals(254, retrieved.totalTargets)
        assertEquals(3, retrieved.liveHosts)
        assertEquals(4500L, retrieved.durationMs)
        assertEquals("Completed", retrieved.status)

        // Delete item
        dao.delete(retrieved)
        historyList = dao.getAllHistory().first()
        assertTrue(historyList.isEmpty())
    }

    @Test
    fun testScanViewModelCustomInputs() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val vm = ScanViewModel(app)

        // Default verification
        assertEquals("12", vm.concurrencyInput.value)
        assertEquals("4000", vm.timeoutInput.value)

        // Set custom choice thread/concurrency and timeout
        vm.concurrencyInput.value = "150"
        vm.timeoutInput.value = "2500"

        assertEquals("150", vm.concurrencyInput.value)
        assertEquals("2500", vm.timeoutInput.value)
    }

    @Test
    fun testScanRepositoryTimeoutConfiguration() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val db = AppDatabase.getDatabase(app)
        val engine = ScanEngine(app)
        val repository = ScanRepository(db.scanHistoryDao(), engine)

        // Valid boundary
        repository.setTimeoutSeconds(15)
        assertEquals(15L, repository.getTimeoutSeconds())
        assertEquals(15L, engine.requestTimeoutSeconds)

        // Below minimum constraint (coerced to 1)
        repository.setTimeoutSeconds(0)
        assertEquals(1L, repository.getTimeoutSeconds())
        assertEquals(1L, engine.requestTimeoutSeconds)

        // Above maximum constraint (coerced to 30)
        repository.setTimeoutSeconds(45)
        assertEquals(30L, repository.getTimeoutSeconds())
        assertEquals(30L, engine.requestTimeoutSeconds)
    }
}
