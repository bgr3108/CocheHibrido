package com.bgr3108.kilonom.viewmodel

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bgr3108.kilonom.data.VehicleCatalogUpdateChecker
import com.bgr3108.kilonom.data.VehicleCatalogUpdateResult
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class VehicleCatalogUpdateViewModelInstrumentedTest {

    @Test
    fun openingTheCatalogScreenState_readsOnlyTheLocalVersionWithoutCheckingRemotely() {
        val checker = FakeChecker()
        val viewModel = InstrumentationRegistry.getInstrumentation().runOnMainSyncAndReturn {
            VehicleCatalogUpdateViewModel(checker)
        }

        require(checker.activeVersionRead.await(2, TimeUnit.SECONDS))
        require(waitUntil { viewModel.uiState.value.activeCatalogVersion == 1 })

        assertEquals(1, viewModel.uiState.value.activeCatalogVersion)
        assertEquals(VehicleCatalogUpdateStatus.Idle, viewModel.uiState.value.status)
        assertEquals(0, checker.checkCalls)
    }

    private class FakeChecker : VehicleCatalogUpdateChecker {
        val activeVersionRead = CountDownLatch(1)
        var checkCalls = 0
            private set

        override fun activeCatalogVersion(): Int {
            activeVersionRead.countDown()
            return 1
        }

        override suspend fun checkForUpdate(): VehicleCatalogUpdateResult {
            checkCalls++
            return VehicleCatalogUpdateResult.UpToDate(1)
        }
    }
}

private fun waitUntil(condition: () -> Boolean): Boolean {
    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
    while (System.nanoTime() < deadline) {
        if (condition()) return true
        Thread.sleep(10)
    }
    return condition()
}

private fun <T : Any> android.app.Instrumentation.runOnMainSyncAndReturn(
    action: () -> T
): T {
    val result = AtomicReference<T>()
    runOnMainSync { result.set(action()) }
    return requireNotNull(result.get())
}
