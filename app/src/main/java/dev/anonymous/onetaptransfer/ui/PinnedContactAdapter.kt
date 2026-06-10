package dev.anonymous.onetaptransfer.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import dev.anonymous.onetaptransfer.data.model.PinnedContact
import dev.anonymous.onetaptransfer.databinding.ItemContactBinding

class PinnedContactAdapter(
    private val onDelete: (PinnedContact) -> Unit,
    private val onClick: (PinnedContact) -> Unit
) : ListAdapter<PinnedContact, PinnedContactAdapter.ViewHolder>(DiffCallback) {

    inner class ViewHolder(private val binding: ItemContactBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: PinnedContact) {
            binding.tvName.text = item.name
            binding.tvNumber.text = item.number
            
            binding.ivDelete.setOnClickListener { onDelete(item) }
            binding.root.setOnClickListener { onClick(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemContactBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    object DiffCallback : DiffUtil.ItemCallback<PinnedContact>() {
        override fun areItemsTheSame(oldItem: PinnedContact, newItem: PinnedContact) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: PinnedContact, newItem: PinnedContact) = oldItem == newItem
    }
}
