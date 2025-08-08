package com.lykos.pointage.adapter

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.lykos.pointage.R
import com.lykos.pointage.databinding.ItemExpenseBinding
import com.lykos.pointage.model.*

class ExpenseAdapter(
    private var expenses: List<ExpenseResponse>, private val onItemClick: (ExpenseResponse) -> Unit
) : RecyclerView.Adapter<ExpenseAdapter.ExpenseViewHolder>() {

    inner class ExpenseViewHolder(private val binding: ItemExpenseBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(expense: ExpenseResponse) {
            // Display first item's note or summary
            val firstItem = expense.items.firstOrNull()
            binding.textViewNote.text = firstItem?.note ?: "مصروف"
            binding.textViewPrice.text = "${expense.totalAmount} Dh"

            // Load first item's image if exists
            val firstImage = expense.items.firstOrNull()?.image
            if (!firstImage.isNullOrEmpty()) {
                Glide.with(itemView.context).load(firstImage).centerCrop()
                    .placeholder(R.drawable.ic_image).error(R.drawable.ic_image)
                    .into(binding.imageViewReceipt)
                binding.imageViewReceipt.visibility = View.VISIBLE
            } else {
                binding.imageViewReceipt.visibility = View.GONE
            }

            // Show item count if multiple items
            if (expense.items.size > 1) {
                binding.textViewNote.text =
                    "${firstItem?.note} (+${expense.items.size - 1} عناصر أخرى)"
            }

            binding.root.setOnClickListener {
                onItemClick(expense)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExpenseViewHolder {
        val binding = ItemExpenseBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ExpenseViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ExpenseViewHolder, position: Int) {
        holder.bind(expenses[position])
    }

    override fun getItemCount(): Int = expenses.size

    fun updateExpenses(newExpenses: List<ExpenseResponse>) {
        val oldSize = expenses.size
        expenses = newExpenses

        // Use more specific notify methods for better performance
        when {
            oldSize == 0 && newExpenses.isNotEmpty() -> {
                // First time loading data
                notifyItemRangeInserted(0, newExpenses.size)
            }

            oldSize > 0 && newExpenses.isEmpty() -> {
                // Data cleared
                notifyItemRangeRemoved(0, oldSize)
            }

            newExpenses.size > oldSize -> {
                // New items added - notify all changed first, then new items
                if (oldSize > 0) {
                    notifyItemRangeChanged(0, oldSize)
                }
                notifyItemRangeInserted(oldSize, newExpenses.size - oldSize)
            }

            newExpenses.size < oldSize -> {
                // Items removed
                notifyItemRangeChanged(0, newExpenses.size)
                notifyItemRangeRemoved(newExpenses.size, oldSize - newExpenses.size)
            }

            else -> {
                // Same size, but content might have changed
                notifyDataSetChanged()
            }
        }

        Log.d("ExpenseAdapter", "Updated expenses: old=$oldSize, new=${newExpenses.size}")
    }
}
