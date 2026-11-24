package io.github.saberbird.cidget.action

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import io.github.saberbird.cidget.R

/**
 * Invisible trampoline for the `COPY` and `SHARE` cells (WIDGET.md §4). It has no UI: it copies the URL
 * (with a toast only on API 32 and below, newer Android shows its own confirmation) or opens the share
 * sheet, then finishes at once. Translucent, no history, not in recents, no animation (see the theme).
 */
class ActionActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = intent.getStringExtra(EXTRA_TEXT).orEmpty()
        when (intent.getStringExtra(EXTRA_KIND)) {
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

    companion object {
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

        /** Intent that shares [text]. */
        fun share(context: Context, appWidgetId: Int, branch: String, text: String): Intent =
            intent(context, KIND_SHARE, appWidgetId, branch, text)
    }
}
