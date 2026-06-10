package dev.anonymous.onetaptransfer.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.Html
import android.text.Spannable
import android.text.method.LinkMovementMethod
import android.text.style.URLSpan
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.net.toUri
import androidx.fragment.app.DialogFragment
import dev.anonymous.onetaptransfer.R
import dev.anonymous.onetaptransfer.databinding.DialogPrivacyPolicyBinding

class PrivacyPolicyDialog : DialogFragment() {

    private var _binding: DialogPrivacyPolicyBinding? = null
    private val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, 0)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogPrivacyPolicyBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val content = getString(R.string.privacy_content)
        binding.tvContent.text = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Html.fromHtml(content, Html.FROM_HTML_MODE_COMPACT)
        } else {
            @Suppress("DEPRECATION")
            Html.fromHtml(content)
        }
        binding.tvContent.movementMethod = BrowserLinkMovementMethod(this)
        binding.btnClose.setOnClickListener { dismiss() }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setWindowAnimations(R.style.AnimationOneTapTransferDialogSlideFromRight)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /**
     * Custom MovementMethod that opens links in an external browser
     * instead of the default in-app behavior.
     */
    private class BrowserLinkMovementMethod(
        private val fragment: DialogFragment
    ) : LinkMovementMethod() {

        override fun onTouchEvent(widget: TextView, buffer: Spannable, event: MotionEvent): Boolean {
            if (event.action == MotionEvent.ACTION_UP) {
                val x = event.x.toInt() - widget.totalPaddingLeft + widget.scrollX
                val y = event.y.toInt() - widget.totalPaddingTop + widget.scrollY
                val layout = widget.layout ?: return super.onTouchEvent(widget, buffer, event)
                val line = layout.getLineForVertical(y)
                val offset = layout.getOffsetForHorizontal(line, x.toFloat())
                val links = buffer.getSpans(offset, offset, URLSpan::class.java)

                if (links.isNotEmpty()) {
                    val url = links[0].url
                    val context = fragment.context ?: return true
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        // No browser available, ignore
                    }
                    return true
                }
            }
            return super.onTouchEvent(widget, buffer, event)
        }
    }
}
