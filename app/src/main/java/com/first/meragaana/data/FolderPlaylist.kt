package com.first.meragaana.data

import com.first.meragaana.utils.AudioFile

data class FolderPlaylist(
    val folderPath: String,
    val folderName: String,
    val songs: List<AudioFile>
)
