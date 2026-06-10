package dev.anonymous.onetaptransfer.ui

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.DialogFragment
import dev.anonymous.onetaptransfer.R
import dev.anonymous.onetaptransfer.databinding.DialogPinContactBinding

class PinContactDialogFragment : DialogFragment() {

    companion object {
        const val RESULT_KEY = "pin_contact_result"
        const val RESULT_NAME = "pin_contact_result_name"
        const val RESULT_NUMBER = "pin_contact_result_number"
        const val RESULT_TYPE = "pin_contact_result_type"

        private const val ARG_NUMBER = "arg_number"
        private const val ARG_TYPE = "arg_type"

        fun newInstance(number: String, type: String): PinContactDialogFragment {
            return PinContactDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_NUMBER, number)
                    putString(ARG_TYPE, type)
                }
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val number = requireArguments().getString(ARG_NUMBER).orEmpty()
        val type = requireArguments().getString(ARG_TYPE).orEmpty()
        val binding = DialogPinContactBinding.inflate(layoutInflater)

        val dialog = Dialog(requireContext())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(binding.root)
        dialog.setCanceledOnTouchOutside(true)

        binding.tvPinnedNumber.text = getString(R.string.pin_contact_number_value, number)

        fun clearError() {
            binding.nameLayout.error = null
        }

        fun pinContact() {
            val name = binding.etContactName.text?.toString()?.trim().orEmpty()
            if (name.length < 3) {
                binding.nameLayout.error = getString(R.string.error_contact_name_min)
                binding.etContactName.requestFocus()
                return
            }

            clearError()
            parentFragmentManager.setFragmentResult(
                RESULT_KEY,
                Bundle().apply {
                    putString(RESULT_NAME, name)
                    putString(RESULT_NUMBER, number)
                    putString(RESULT_TYPE, type)
                }
            )
            dismiss()
        }

        dialog.setOnShowListener {
            dialog.window?.apply {
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                attributes = attributes.apply { dimAmount = 0.58f }
                setLayout(
                    (resources.displayMetrics.widthPixels * 0.9f).toInt(),
                    WindowManager.LayoutParams.WRAP_CONTENT
                )
                setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
            }

            binding.etContactName.requestFocus()
            binding.etContactName.postDelayed({
                val imm = requireContext().getSystemService(InputMethodManager::class.java)
                imm?.showSoftInput(binding.etContactName, InputMethodManager.SHOW_IMPLICIT)
            }, 200)
        }

        binding.etContactName.doAfterTextChanged { clearError() }
        binding.btnCancel.setOnClickListener { dismiss() }
        binding.btnPin.setOnClickListener { pinContact() }

        binding.etContactName.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                pinContact()
                true
            } else {
                false
            }
        }

        return dialog
    }
}
