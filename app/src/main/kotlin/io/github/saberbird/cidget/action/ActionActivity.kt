package io.github.saberbird.cidget.action

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import io.github.saberbird.cidget.R
import io.github.saberbird.cidget.graph
import io.github.saberbird.cidget.settings.clipboardText
import io.github.saberbird.cidget.settings.message
import io.github.saberbird.cidget.settings.saveFromClipboard
import kotlinx.coroutines.launch

/**
 * Invisible trampoline for the `COPY` and `SHARE` cells and for pasting a token (WIDGET.md §4). It has no UI:
 * it copies the URL (with a toast only on API 32 and below, newer Android shows its own confirmation), opens
 * the share sheet, or reads the clipboard once the window has focus (Android lets only a focused activity
 * read it) and saves the token, then finishes at once. Translucent, no history, not in recents, no animation (see the theme).
 */
class ActionActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = intent.getStringExtra(EXTRA_TEXT).orEmpty()
        val kind = intent.getStringExtra(EXTRA_KIND)
        if (kind == KIND_PASTE) {
            // Finishes in onWindowFocusChanged; the timeout covers a window that never gets focus.
            window.decorView.postDelayed({ if (!isFinishing) finish() }, PASTE_TIMEOUT_MS)
            return
        }
        when (kind) {
            KIND_COPY -> {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("ciDGET", text))
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
                    Toast.makeText(this, R.string.action_copied, Toast.LENGTH_SHORT).show()
                }
            }
            KIND_SHARE -> {
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                startActivity(Intent.createChooser(send, null))
            }
        }
        finish()
    }

    private var pasted = false

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus || pasted || intent.getStringExtra(EXTRA_KIND) != KIND_PASTE) return
        pasted = true
        val raw = clipboardText(this)
        val app = applicationContext
        val graph = app.graph
        graph.background.launch {
            val message = saveFromClipboard(app, graph, raw).message(app)
            Handler(Looper.getMainLooper()).post { Toast.makeText(app, message, Toast.LENGTH_LONG).show() }
        }
        finish()
    }

    companion object {
        private const val PASTE_TIMEOUT_MS = 2000L
        private const val KIND_PASTE = "paste"
        private const val EXTRA_KIND = "kind"
        private const val EXTRA_TEXT = "text"
        private const val KIND_COPY = "copy"
        private const val KIND_SHARE = "share"

        private fun intent(context: Context, kind: String, appWidgetId: Int, branch: String, text: String): Intent =
            Intent(context, ActionActivity::class.java)
                .putExtra(EXTRA_KIND, kind)
                .putExtra(EXTRA_TEXT, text)
                // A unique data URI per widget, cell and branch keeps the PendingIntents apart.
                .setData(Uri.Builder().scheme("cidget").authority("action").appendPath(kind)
                    .appendPath(appWidgetId.toString()).appendPath(branch).build())

        /** Intent that copies [url]. */
        fun copy(context: Context, appWidgetId: Int, branch: String, url: String): Intent =
            intent(context, KIND_COPY, appWidgetId, branch, url)

        /** Intent that pastes a token from the clipboard. */
        fun paste(context: Context, appWidgetId: Int): Intent =
            intent(context, KIND_PASTE, appWidgetId, "", "")

        /** Intent that shares [text]. */
        fun share(context: Context, appWidgetId: Int, branch: String, text: String): Intent =
            intent(context, KIND_SHARE, appWidgetId, branch, text)
    }
}
