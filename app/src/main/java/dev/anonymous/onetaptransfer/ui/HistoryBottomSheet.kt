package dev.anonymous.onetaptransfer.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dev.anonymous.onetaptransfer.databinding.BottomSheetHistoryBinding
import dev.anonymous.onetaptransfer.viewmodel.MainViewModel
import kotlinx.coroutines.launch

class HistoryBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetHistoryBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var adapter: TransactionAdapter

    var onTransactionClick: ((String, String, String) -> Unit)? = null
    var onPinTransaction: ((String, String) -> Unit)? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BottomSheetHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = TransactionAdapter(
            onDelete = { viewModel.deleteTransaction(it.id) },
            onPin = { onPinTransaction?.invoke(it.recipient, it.type) },
            onClick = {
                onTransactionClick?.invoke(it.recipient, it.amount, it.type)
                dismiss()
            }
        )

        binding.rvFullHistory.layoutManager = LinearLayoutManager(requireContext())
        binding.rvFullHistory.adapter = adapter

        binding.tvClearHistory.setOnClickListener {
            viewModel.clearHistory()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.history.collect {
                        adapter.submitList(it)
                        binding.tvNoHistory.visibility = if (it.isEmpty()) View.VISIBLE else View.GONE
                    }
                }
                launch {
                    viewModel.pinnedContacts.collect {
                        adapter.updatePinnedContacts(it)
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
