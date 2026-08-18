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
import dev.anonymous.onetaptransfer.databinding.DialogInfoBinding

open class InfoDialogFragment : DialogFragment() {

    private var _binding: DialogInfoBinding? = null
    protected val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, 0)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogInfoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val titleRes = arguments?.getInt(ARG_TITLE_RES, 0) ?: 0
        val titleText = arguments?.getString(ARG_TITLE_TEXT)
        val contentRes = arguments?.getInt(ARG_CONTENT_RES, 0) ?: 0
        val contentText = arguments?.getString(ARG_CONTENT_TEXT)

        val title = when {
            titleRes != 0 -> getString(titleRes)
            titleText != null -> titleText
            else -> ""
        }

        val rawContent = when {
            contentRes != 0 -> getString(contentRes)
            contentText != null -> contentText
            else -> ""
        }

        binding.tvTitle.text = title
        binding.tvContent.text = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Html.fromHtml(rawContent, Html.FROM_HTML_MODE_COMPACT)
        } else {
            @Suppress("DEPRECATION")
            Html.fromHtml(rawContent)
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

    companion object {
        private const val ARG_TITLE_RES = "title_res"
        private const val ARG_TITLE_TEXT = "title_text"
        private const val ARG_CONTENT_RES = "content_res"
        private const val ARG_CONTENT_TEXT = "content_text"

        fun newInstance(titleRes: Int, contentRes: Int): InfoDialogFragment {
            val fragment = InfoDialogFragment()
            fragment.arguments = Bundle().apply {
                putInt(ARG_TITLE_RES, titleRes)
                putInt(ARG_CONTENT_RES, contentRes)
            }
            return fragment
        }

        fun newInstance(title: String, content: String): InfoDialogFragment {
            val fragment = InfoDialogFragment()
            fragment.arguments = Bundle().apply {
                putString(ARG_TITLE_TEXT, title)
                putString(ARG_CONTENT_TEXT, content)
            }
            return fragment
        }
    }
}
