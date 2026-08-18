package dev.anonymous.onetaptransfer.ui

import android.os.Bundle
import dev.anonymous.onetaptransfer.R

class WhatsNewDialog : InfoDialogFragment() {
    override fun onCreate(savedInstanceState: Bundle?) {
        if (arguments == null) {
            arguments = Bundle().apply {
                putInt("title_res", R.string.whats_new_title)
                putInt("content_res", R.string.whats_new_content)
            }
        }
        super.onCreate(savedInstanceState)
    }

    companion object {
        fun newInstance(): WhatsNewDialog = WhatsNewDialog()
    }
}
