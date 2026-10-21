package com.bgr3108.kilonom.viewmodel

import org.junit.Assert.*
import org.junit.Test

class ScreenOperationResultsTest {
    @Test fun completionIsRetainedUntilConsumedExactlyOnceByItsOrigin() {
        val store = ScreenOperationResults()
        val operation = requireNotNull(store.begin("form-1", ScreenOperationKind.SAVE))
        store.finish(operation, ScreenOperationStatus.SUCCEEDED)
        assertFalse(store.consume("form-2", operation.id))
        assertFalse(store.consume("form-1", operation.id + 1))
        assertTrue(store.consume("form-1", operation.id))
        assertFalse(store.consume("form-1", operation.id))
        store.finish(operation, ScreenOperationStatus.SUCCEEDED)
        assertFalse(store.consume("form-1", operation.id))
    }

    @Test fun successfulFormCannotSubmitAgainBeforeOrAfterNavigation() {
        val store = ScreenOperationResults()
        val operation = requireNotNull(store.begin("form", ScreenOperationKind.SAVE))
        assertNull(store.begin("form", ScreenOperationKind.SAVE))
        store.finish(operation, ScreenOperationStatus.SUCCEEDED)
        assertNull(store.begin("form", ScreenOperationKind.SAVE))
        store.consume("form", operation.id)
        assertNull(store.begin("form", ScreenOperationKind.SAVE))
        assertNotNull(store.begin("new-form-entry", ScreenOperationKind.SAVE))
    }

    @Test fun errorsSurviveNewObserversAndExplicitRetryReplacesThem() {
        val store = ScreenOperationResults()
        val operation = requireNotNull(store.begin("form", ScreenOperationKind.SAVE))
        store.finish(operation, ScreenOperationStatus.FAILED, "No se pudo guardar")
        assertEquals("No se pudo guardar", store.results.value.getValue("form").error)
        assertFalse(store.consume("form", operation.id))
        val retry = requireNotNull(store.begin("form", ScreenOperationKind.SAVE))
        assertNotEquals(operation.id, retry.id)
        assertNull(store.results.value.getValue("form").error)
    }

    @Test fun cancellationIsNotASuccessAndAllowsRetry() {
        val store = ScreenOperationResults()
        val operation = requireNotNull(store.begin("form", ScreenOperationKind.SAVE))
        store.finish(operation, ScreenOperationStatus.CANCELLED)
        assertFalse(store.consume("form", operation.id))
        assertNull(store.results.value.getValue("form").error)
        assertNotNull(store.begin("form", ScreenOperationKind.SAVE))
    }

    @Test fun oldAttemptCannotOverwriteRetryOrAnotherEntry() {
        val store = ScreenOperationResults()
        val old = requireNotNull(store.begin("form", ScreenOperationKind.SAVE))
        store.finish(old, ScreenOperationStatus.FAILED)
        val retry = requireNotNull(store.begin("form", ScreenOperationKind.SAVE))
        val other = requireNotNull(store.begin("other", ScreenOperationKind.DELETE_ENTRY))
        store.finish(old, ScreenOperationStatus.SUCCEEDED)
        assertEquals(retry, store.results.value["form"])
        assertEquals(other, store.results.value["other"])
    }

    @Test fun listCanDeleteAnotherRecordAfterConsumingCompletion() {
        val store = ScreenOperationResults()
        val first = requireNotNull(store.begin("list", ScreenOperationKind.DELETE_ENTRY, targetId = 1))
        store.finish(first, ScreenOperationStatus.SUCCEEDED)
        assertEquals(1L, store.results.value.getValue("list").targetId)
        store.consume("list", first.id)
        assertEquals(2L, store.begin("list", ScreenOperationKind.DELETE_ENTRY, targetId = 2)?.targetId)
    }
}
