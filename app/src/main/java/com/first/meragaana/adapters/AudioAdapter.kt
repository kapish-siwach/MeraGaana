package com.first.meragaana.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.first.meragaana.R
import com.first.meragaana.utils.AudioFile

class AudioAdapter(
    private var _audioFiles: List<AudioFile>,
    private val onItemClick: (AudioFile) -> Unit
) : RecyclerView.Adapter<AudioAdapter.AudioViewHolder>() {

    val audioFiles: List<AudioFile>
        get() = _audioFiles

    class AudioViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val titleTextView: TextView = view.findViewById(R.id.title_text)
        val artistTextView: TextView = view.findViewById(R.id.artist_text)
        val durationTextView: TextView = view.findViewById(R.id.duration_text)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AudioViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_audio, parent, false)
        return AudioViewHolder(view)
    }

    override fun onBindViewHolder(holder: AudioViewHolder, position: Int) {
        val audio = _audioFiles[position]
        holder.titleTextView.text = audio.title
        holder.artistTextView.text = audio.artist
        holder.durationTextView.text = formatDuration(audio.duration)
        
        holder.itemView.setOnClickListener {
            onItemClick(audio)
        }
    }

    override fun getItemCount() = _audioFiles.size

    fun updateData(newAudioFiles: List<AudioFile>) {
        _audioFiles = newAudioFiles
        notifyDataSetChanged()
    }

    private fun formatDuration(duration: Long): String {
        val minutes = duration / 1000 / 60
        val seconds = duration / 1000 % 60
        return String.format("%d:%02d", minutes, seconds)
    }
}
