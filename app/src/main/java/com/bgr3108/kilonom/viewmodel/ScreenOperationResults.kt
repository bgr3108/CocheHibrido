package com.bgr3108.kilonom.viewmodel

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ScreenOperationKind { SAVE, DELETE_ENTRY, DELETE_ITEM, DELETE_RECORD, DELETE_VEHICLE, DELETE_ACTIVE_VEHICLE }
enum class ScreenOperationStatus { RUNNING, SUCCEEDED, FAILED, CANCELLED }

data class ScreenOperationResult(
    val id: Long,
    val origin: String,
    val kind: ScreenOperationKind,
    val targetId: Long? = null,
    val status: ScreenOperationStatus = ScreenOperationStatus.RUNNING,
    val error: String? = null,
    val consumed: Boolean = false
)

/** Results belong to a navigation entry, not to a captured Activity or callback. */
class ScreenOperationResults {
    private val mutableResults = MutableStateFlow<Map<String, ScreenOperationResult>>(emptyMap())
    val results = mutableResults.asStateFlow()
    private var nextId = 0L

    @Synchronized
    fun begin(origin: String, kind: ScreenOperationKind, targetId: Long? = null): ScreenOperationResult? {
        val previous = mutableResults.value[origin]
        if (previous?.status == ScreenOperationStatus.RUNNING) return null
        if (previous?.status == ScreenOperationStatus.SUCCEEDED && !previous.consumed) return null
        // A completed form cannot submit again while its exit is pending (or already consumed).
        if (kind == ScreenOperationKind.SAVE && previous?.kind == kind &&
            previous.status == ScreenOperationStatus.SUCCEEDED) return null
        return ScreenOperationResult(++nextId, origin, kind, targetId = targetId).also {
            mutableResults.value += origin to it
        }
    }

    @Synchronized
    fun finish(operation: ScreenOperationResult, status: ScreenOperationStatus, error: String? = null) {
        val current = mutableResults.value[operation.origin]
        if (current?.id != operation.id || current.status != ScreenOperationStatus.RUNNING) return
        mutableResults.value += operation.origin to operation.copy(status = status, error = error)
    }

    /** Mark before navigating: recreation or a second observer cannot navigate again. */
    @Synchronized
    fun consume(origin: String, id: Long): Boolean {
        val result = mutableResults.value[origin] ?: return false
        if (result.id != id || result.status != ScreenOperationStatus.SUCCEEDED || result.consumed) return false
        mutableResults.value += origin to result.copy(consumed = true)
        return true
    }
}
