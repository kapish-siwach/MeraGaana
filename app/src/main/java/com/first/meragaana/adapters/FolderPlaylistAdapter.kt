package com.first.meragaana.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.first.meragaana.R
import com.first.meragaana.data.FolderPlaylist
import com.first.meragaana.utils.AudioFile

class FolderPlaylistAdapter(
    private var folderPlaylists: List<FolderPlaylist>,
    private val onSongClick: (AudioFile) -> Unit
) : RecyclerView.Adapter<FolderPlaylistAdapter.FolderViewHolder>() {

    // Getter for folderPlaylists
    fun getFolderPlaylists(): List<FolderPlaylist> = folderPlaylists

    inner class FolderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val folderNameText: TextView = itemView.findViewById(R.id.folder_name)
        private val songsRecyclerView: RecyclerView = itemView.findViewById(R.id.songs_recycler_view)
        private val songCountText: TextView = itemView.findViewById(R.id.song_count)

        fun bind(folderPlaylist: FolderPlaylist) {
            folderNameText.text = folderPlaylist.folderName
            songCountText.text = "${folderPlaylist.songs.size} songs"

            // Set up nested RecyclerView for songs
            songsRecyclerView.layoutManager = LinearLayoutManager(itemView.context)
            val audioAdapter = AudioAdapter(folderPlaylist.songs, onSongClick)
            songsRecyclerView.adapter = audioAdapter
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FolderViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_folder_playlist, parent, false)
        return FolderViewHolder(view)
    }

    override fun onBindViewHolder(holder: FolderViewHolder, position: Int) {
        holder.bind(folderPlaylists[position])
    }

    override fun getItemCount() = folderPlaylists.size

    fun updateData(newFolderPlaylists: List<FolderPlaylist>) {
        folderPlaylists = newFolderPlaylists
        notifyDataSetChanged()
    }
}
