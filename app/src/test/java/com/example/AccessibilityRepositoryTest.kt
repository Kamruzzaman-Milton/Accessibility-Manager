package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.AccessibilityRepository
import com.example.model.AccessibilityServiceItem
import com.example.shizuku.ShizukuManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AccessibilityRepositoryTest {

    private lateinit var context: Context
    private lateinit var repository: AccessibilityRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        val shizukuManager = ShizukuManager(context)
        repository = AccessibilityRepository(context, shizukuManager)
    }

    @Test
    fun testParseEnabledServices() {
        val emptyList = repository.parseEnabledServices("")
        assertTrue(emptyList.isEmpty())

        val singleList = repository.parseEnabledServices("com.example.app/com.example.app.Service")
        assertEquals(1, singleList.size)
        assertEquals("com.example.app/com.example.app.Service", singleList[0])

        val multiList = repository.parseEnabledServices(
            "com.app1/.Service1:com.app2/com.app2.Service2::com.app3/com.app3.Service3:"
        )
        assertEquals(3, multiList.size)
        assertEquals("com.app1/.Service1", multiList[0])
        assertEquals("com.app2/com.app2.Service2", multiList[1])
        assertEquals("com.app3/com.app3.Service3", multiList[2])
    }

    @Test
    fun testMatchesService() {
        val targetPkg = "com.touch.gesture"
        val targetCls = "com.touch.gesture.TouchAccessibilityService"

        // Exact match
        assertTrue(
            repository.matchesService(
                "com.touch.gesture/com.touch.gesture.TouchAccessibilityService",
                targetPkg,
                targetCls
            )
        )

        // Short form match
        assertTrue(
            repository.matchesService(
                "com.touch.gesture/.TouchAccessibilityService",
                targetPkg,
                targetCls
            )
        )

        // Different service in same package
        assertFalse(
            repository.matchesService(
                "com.touch.gesture/.OtherService",
                targetPkg,
                targetCls
            )
        )

        // Different package
        assertFalse(
            repository.matchesService(
                "com.other.app/.TouchAccessibilityService",
                targetPkg,
                targetCls
            )
        )
    }

    @Test
    fun testMultipleServicesInSamePackageHandledSeparately() {
        val pkg = "com.automation.tool"
        val service1 = "com.automation.tool.MonitorService"
        val service2 = "com.automation.tool.ActionService"

        val enabledList = "com.other.app/.OtherService:com.automation.tool/.MonitorService"
        val parsed = repository.parseEnabledServices(enabledList)

        // service1 should be enabled
        val isService1Enabled = parsed.any { repository.matchesService(it, pkg, service1) }
        assertTrue(isService1Enabled)

        // service2 should NOT be enabled
        val isService2Enabled = parsed.any { repository.matchesService(it, pkg, service2) }
        assertFalse(isService2Enabled)
    }

    @Test
    fun testPreservesOtherEnabledServicesWhenToggling() {
        val serviceA = "com.app.a/.ServiceA"
        val serviceB = "com.app.b/.ServiceB"
        val serviceC = "com.app.c/.ServiceC"

        val initial = listOf(serviceA, serviceB)

        // Adding service C preserves A and B
        val targetServiceC = AccessibilityServiceItem(
            appName = "App C",
            packageName = "com.app.c",
            serviceClassName = "com.app.c.ServiceC",
            serviceSimpleName = "ServiceC",
            serviceLabel = "Service C",
            isEnabled = false
        )

        val withC = (initial.filterNot {
            repository.matchesService(it, targetServiceC.packageName, targetServiceC.serviceClassName)
        } + targetServiceC.componentFlattened)

        assertEquals(3, withC.size)
        assertTrue(withC.contains(serviceA))
        assertTrue(withC.contains(serviceB))
        assertTrue(withC.contains("com.app.c/com.app.c.ServiceC"))

        // Removing service A preserves B and C
        val withoutA = withC.filterNot {
            repository.matchesService(it, "com.app.a", "com.app.a.ServiceA")
        }
        assertEquals(2, withoutA.size)
        assertFalse(withoutA.contains(serviceA))
        assertTrue(withoutA.contains(serviceB))
        assertTrue(withoutA.contains("com.app.c/com.app.c.ServiceC"))
    }

    @Test
    fun testGroupTogglingPreservesExternalServices() {
        val externalService = "com.external.app/.KeepAliveService"
        val member1 = "com.group.app1/.Service1"
        val member2 = "com.group.app2/.Service2"

        val initialList = mutableListOf(externalService, member1)

        // Turn group off: member1 and member2 should be removed, externalService preserved
        val groupMembers = listOf(member1, member2)
        val afterDisable = initialList.filterNot { entry ->
            groupMembers.any { member -> entry.startsWith(member.substringBefore('/')) }
        }

        assertEquals(1, afterDisable.size)
        assertEquals(externalService, afterDisable[0])

        // Turn group on: member1 and member2 added, externalService preserved
        val afterEnable = afterDisable.toMutableList()
        groupMembers.forEach { m ->
            if (!afterEnable.contains(m)) afterEnable.add(m)
        }

        assertEquals(3, afterEnable.size)
        assertTrue(afterEnable.contains(externalService))
        assertTrue(afterEnable.contains(member1))
        assertTrue(afterEnable.contains(member2))
    }
}
