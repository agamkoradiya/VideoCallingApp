package com.example.codefunvideocallingtest.adapter.ui_call

import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.codefunvideocallingtest.databinding.ItemTextViewBinding
import javax.inject.Inject

class TextMessagesAdapter @Inject constructor() :
    ListAdapter<String, TextMessagesAdapter.TextMessagesViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TextMessagesViewHolder {
        val binding =
            ItemTextViewBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        return TextMessagesViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TextMessagesViewHolder, position: Int) {
        val currentItem = getItem(position)
        Log.d("viewmodel", "onBindViewHolder: current value $currentItem")
        holder.messageTextView.text = currentItem
    }

    inner class TextMessagesViewHolder(private val binding: ItemTextViewBinding) :
        RecyclerView.ViewHolder(binding.root) {

        val messageTextView = binding.messageTxt
    }

    class DiffCallback : DiffUtil.ItemCallback<String>() {
        override fun areItemsTheSame(oldItem: String, newItem: String) = false

        override fun areContentsTheSame(oldItem: String, newItem: String) = false
    }
}