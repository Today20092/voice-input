package org.futo.voiceinput.proof

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts

/** A real Android result receiver for the controlled experimental host. */
class SharedResultCallerActivity : ComponentActivity() {
    companion object {
        internal var onResult: ((ActivityResult) -> Unit)? = null
    }
    private val launchRecognition = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        onResult?.invoke(it)
        finish()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null && onResult != null) {
            launchRecognition.launch(Intent(this, SharedResultProofActivity::class.java))
        } else if (onResult == null) finish()
    }
}
