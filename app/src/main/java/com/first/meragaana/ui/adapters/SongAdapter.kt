package com.first.meragaana.ui.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.first.meragaana.R
import com.first.meragaana.databinding.ItemSongBinding
import com.first.meragaana.utils.AudioFile

class SongAdapter(
    private val onItemClick: (AudioFile, Int) -> Unit
) : ListAdapter<AudioFile, SongAdapter.SongViewHolder>(SongDiffCallback()) {

    private var currentlyPlayingPosition: Int = -1

    fun setCurrentlyPlaying(position: Int) {
        val oldPosition = currentlyPlayingPosition
        currentlyPlayingPosition = position
        notifyItemChanged(oldPosition)
        notifyItemChanged(position)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongViewHolder {
        val binding = ItemSongBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return SongViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
        val song = getItem(position)
        holder.bind(song, position == currentlyPlayingPosition)
        holder.itemView.setOnClickListener { onItemClick(song, position) }
    }

    inner class SongViewHolder(
        private val binding: ItemSongBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(song: AudioFile, isPlaying: Boolean) {
            binding.apply {
                songTitle.text = song.title
                artistName.text = song.artist
                
                Glide.with(itemView)
                    .load(song.albumArtUri)
                    .placeholder(R.drawable.ic_music_note)
                    .error(R.drawable.ic_music_note)
                    .into(albumArt)
                
                // Highlight if currently playing
                root.setBackgroundColor(
                    if (isPlaying) 
                        root.context.getColor(R.color.currently_playing_background)
                    else 
                        Color.TRANSPARENT
                )
                
                songTitle.setTextColor(
                    if (isPlaying) 
                        root.context.getColor(R.color.currently_playing_text)
                    else 
                        root.context.getColor(R.color.normal_text)
                )
            }
        }
    }

    private class SongDiffCallback : DiffUtil.ItemCallback<AudioFile>() {
        override fun areItemsTheSame(oldItem: AudioFile, newItem: AudioFile): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: AudioFile, newItem: AudioFile): Boolean {
            return oldItem == newItem
        }
    }
}
