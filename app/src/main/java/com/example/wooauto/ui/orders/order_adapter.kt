package com.example.wooauto.ui.orders

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.wooauto.R
import com.example.wooauto.data.database.entities.OrderEntity
import com.example.wooauto.databinding.ItemOrderBinding
import java.text.SimpleDateFormat
import java.util.Locale

class OrderAdapter(
    private val onOrderClick: (Long) -> Unit,
    private val onMarkAsCompleteClick: (Long) -> Unit
) : ListAdapter<OrderEntity, OrderAdapter.OrderViewHolder>(OrderDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OrderViewHolder {
        val binding = ItemOrderBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return OrderViewHolder(binding)
    }

    override fun onBindViewHolder(holder: OrderViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class OrderViewHolder(
        private val binding: ItemOrderBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = adapterPosition // 修复：使用adapterPosition而非bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onOrderClick(getItem(position).id)
                }
            }

            binding.markCompleteButton.setOnClickListener {
                val position = adapterPosition // 修复：使用adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onMarkAsCompleteClick(getItem(position).id)
                }
            }
        }

        fun bind(order: OrderEntity) {
            // Format date
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            val formattedDate = dateFormat.format(order.dateCreated)

            // Set text
            binding.orderNumberTextView.text = itemView.context.getString(
                R.string.order_number,
                order.number
            )
            binding.dateTextView.text = itemView.context.getString(
                R.string.order_date,
                formattedDate
            )
            binding.customerTextView.text = itemView.context.getString(
                R.string.customer_name,
                order.customerName
            )
            binding.amountTextView.text = itemView.context.getString(
                R.string.order_amount,
                order.total
            )

            // Status chip
            binding.statusChip.text = order.status.capitalize(Locale.getDefault())
            val statusColor = when (order.status.lowercase(Locale.getDefault())) {
                "pending" -> R.color.StatusPending
                "processing" -> R.color.StatusProcessing
                "on-hold" -> R.color.StatusOnHold
                "completed" -> R.color.StatusCompleted
                "cancelled" -> R.color.StatusCancelled
                "refunded" -> R.color.StatusRefunded
                "failed" -> R.color.StatusFailed
                else -> R.color.StatusProcessing
            }
            binding.statusChip.chipBackgroundColor = ContextCompat.getColorStateList(
                itemView.context,
                statusColor
            )

            // Hide mark as complete button for completed orders
            binding.markCompleteButton.visibility = if (order.status.equals("completed", ignoreCase = true)) {
                View.GONE
            } else {
                View.VISIBLE
            }

            // Print status
            if (order.isPrinted) {
                binding.printStatusTextView.text = "Printed"
                binding.printIcon.setColorFilter(
                    ContextCompat.getColor(itemView.context, R.color.StatusCompleted)
                )
            } else {
                binding.printStatusTextView.text = "Not Printed"
                binding.printIcon.setColorFilter(
                    ContextCompat.getColor(itemView.context, R.color.StatusFailed)
                )
            }
        }
    }

    private class OrderDiffCallback : DiffUtil.ItemCallback<OrderEntity>() {
        override fun areItemsTheSame(oldItem: OrderEntity, newItem: OrderEntity): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: OrderEntity, newItem: OrderEntity): Boolean {
            return oldItem == newItem
        }
    }
}

private fun String.capitalize(locale: Locale): String {
    return replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
}