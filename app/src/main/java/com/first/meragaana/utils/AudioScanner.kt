package com.first.meragaana.utils

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import com.first.meragaana.data.FolderPlaylist
import java.io.File

class AudioScanner(private val context: Context) {
    fun getAllAudioFiles(): List<AudioFile> {
        val audioFiles = mutableListOf<AudioFile>()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DATE_MODIFIED
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        context.contentResolver.query(
            collection,
            projection,
            selection,
            null,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val pathColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val dateModifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val contentUri = ContentUris.withAppendedId(collection, id)
                val path = cursor.getString(pathColumn)

                val audioFile = AudioFile(
                    id = id,
                    title = cursor.getString(titleColumn),
                    artist = cursor.getString(artistColumn),
                    album = cursor.getString(albumColumn),
                    duration = cursor.getLong(durationColumn),
                    uri = contentUri,
                    path = path,
                    size = cursor.getLong(sizeColumn),
                    dateAdded = cursor.getLong(dateAddedColumn),
                    dateModified = cursor.getLong(dateModifiedColumn)
                )
                audioFiles.add(audioFile)
            }
        }

        return audioFiles
    }

    fun getFolderPlaylists(): List<FolderPlaylist> {
        val audioFiles = getAllAudioFiles()
        return audioFiles
            .groupBy { File(it.path).parentFile?.absolutePath ?: "" }
            .map { (folderPath, songs) ->
                FolderPlaylist(
                    folderPath = folderPath,
                    folderName = File(folderPath).name,
                    songs = songs
                )
            }
            .filter { it.songs.isNotEmpty() }
            .sortedBy { it.folderName }
    }
}
