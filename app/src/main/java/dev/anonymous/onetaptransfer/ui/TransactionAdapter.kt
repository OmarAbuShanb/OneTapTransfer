package dev.anonymous.onetaptransfer.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import dev.anonymous.onetaptransfer.data.model.PinnedContact
import dev.anonymous.onetaptransfer.data.model.Transaction
import dev.anonymous.onetaptransfer.databinding.ItemTransactionBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class TransactionAdapter(
    private val onDelete: (Transaction) -> Unit,
    private val onPin: (Transaction) -> Unit,
    private val onClick: (Transaction) -> Unit
) : ListAdapter<Transaction, TransactionAdapter.ViewHolder>(DiffCallback) {

    /** Maps recipient phone number → pinned contact name. */
    private var pinnedMap: Map<String, String> = emptyMap()

    fun updatePinnedContacts(contacts: List<PinnedContact>) {
        pinnedMap = contacts.associate { it.number to it.name }
        notifyDataSetChanged()
    }

    inner class ViewHolder(private val binding: ItemTransactionBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: Transaction) {
            val isPinned = pinnedMap.containsKey(item.recipient)

            // Show pinned contact name if available, otherwise show the number
            binding.tvRecipient.text = if (isPinned) pinnedMap[item.recipient] else item.recipient
            binding.tvDetails.text = "${item.amount} شيكل - ${getTypeDisplayName(item.type)}"

            val iconRes = when (item.type) {
                "BANK" -> dev.anonymous.onetaptransfer.R.drawable.image1
                "WALLET_1", "MERCHANT" -> dev.anonymous.onetaptransfer.R.drawable.image2
                "WALLET_2" -> dev.anonymous.onetaptransfer.R.drawable.image3
                else -> 0
            }
            if (iconRes != 0) {
                binding.ivTypeIcon.setImageResource(iconRes)
                binding.ivTypeIcon.visibility = View.VISIBLE
            } else {
                binding.ivTypeIcon.visibility = View.GONE
            }

            // 12-hour format with Arabic AM/PM
            val sdf = SimpleDateFormat("yyyy/MM/dd hh:mm", Locale.getDefault())
            val cal = Calendar.getInstance().apply { timeInMillis = item.timestamp }
            val amPm = if (cal.get(Calendar.AM_PM) == Calendar.AM) "صباحاً" else "مساءً"
            binding.tvDate.text = "${sdf.format(Date(item.timestamp))} $amPm"

            binding.ivDelete.setOnClickListener { onDelete(item) }

            // Hide pin button if already pinned, show if not
            binding.ivPin.visibility = if (isPinned) View.GONE else View.VISIBLE
            binding.ivPin.setOnClickListener { onPin(item) }

            binding.root.setOnClickListener { onClick(item) }
        }
    }

    companion object {
        fun getTypeDisplayName(type: String): String {
            return when (type) {
                "BANK" -> "بنك"
                "WALLET_1" -> "محفظة ١"
                "MERCHANT" -> "تاجر"
                "WALLET_2" -> "محفظة ٢"
                else -> type
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemTransactionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    object DiffCallback : DiffUtil.ItemCallback<Transaction>() {
        override fun areItemsTheSame(oldItem: Transaction, newItem: Transaction) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Transaction, newItem: Transaction) = oldItem == newItem
    }
}