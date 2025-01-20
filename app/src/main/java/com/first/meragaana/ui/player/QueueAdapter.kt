package com.first.meragaana.ui.player

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.first.meragaana.databinding.ItemQueueSongBinding
import com.first.meragaana.utils.AudioFile

class QueueAdapter(
    private val onItemClick: (Int) -> Unit
) : ListAdapter<AudioFile, QueueAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemQueueSongBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), position)
    }

    inner class ViewHolder(
        private val binding: ItemQueueSongBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(position)
                }
            }
        }

        fun bind(song: AudioFile, position: Int) {
            binding.apply {
                songTitle.text = song.title
                artistName.text = song.artist
            }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<AudioFile>() {
        override fun areItemsTheSame(oldItem: AudioFile, newItem: AudioFile): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: AudioFile, newItem: AudioFile): Boolean {
            return oldItem == newItem
        }
    }
}
