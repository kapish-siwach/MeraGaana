package com.first.meragaana.utils

import android.net.Uri
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class AudioFile(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val uri: Uri,
    val path: String,
    val albumArtUri: Uri? = null,
    val size: Long = 0L,
    val dateAdded: Long = 0L,
    val dateModified: Long = 0L
) : Parcelable
