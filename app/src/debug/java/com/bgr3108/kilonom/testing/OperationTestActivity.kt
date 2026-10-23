package com.bgr3108.kilonom.testing

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable

/** Isolated Compose host for lifecycle tests. Never opens AppContainer or personal storage. */
class OperationTestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState ?: restoredState)
        setContent { content?.invoke(this) }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        onStateSaved?.invoke(Bundle(outState))
    }

    companion object {
        var content: (@Composable (OperationTestActivity) -> Unit)? = null
        var restoredState: Bundle? = null
        var onStateSaved: ((Bundle) -> Unit)? = null
    }
}
