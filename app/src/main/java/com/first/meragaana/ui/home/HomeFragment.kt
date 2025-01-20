package com.first.meragaana.ui.home

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.first.meragaana.R
import com.first.meragaana.databinding.FragmentHomeBinding
import com.first.meragaana.ui.adapters.SongAdapter
import com.first.meragaana.utils.AudioFile
import com.first.meragaana.utils.AudioScanner
import com.first.meragaana.viewmodel.PlayerViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    
    private val playerViewModel: PlayerViewModel by activityViewModels()
    private var songAdapter: SongAdapter? = null
    private var audioScanner: AudioScanner? = null
    
    override fun onAttach(context: Context) {
        super.onAttach(context)
        audioScanner = AudioScanner(context)
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        loadAudioFiles()
        observeCurrentSong()
    }

    private fun observeCurrentSong() {
        viewLifecycleOwner.lifecycleScope.launch {
            playerViewModel.currentSongPosition.collectLatest { position ->
                songAdapter?.setCurrentlyPlaying(position)
            }
        }
    }
    
    private fun setupRecyclerView() {
        songAdapter = SongAdapter { song, position ->
            Log.d(TAG, "Song clicked: ${song.title} at position $position")
            songAdapter?.currentList?.let { allSongs ->
                try {
                    playerViewModel.setQueue(allSongs)
                    playerViewModel.playSong(position)
                    val navOptions = NavOptions.Builder()
                        .setEnterAnim(androidx.navigation.ui.R.anim.nav_default_enter_anim)
                        .setExitAnim(androidx.navigation.ui.R.anim.nav_default_exit_anim)
                        .setPopEnterAnim(androidx.navigation.ui.R.anim.nav_default_pop_enter_anim)
                        .setPopExitAnim(androidx.navigation.ui.R.anim.nav_default_pop_exit_anim)
                        .build()
                    findNavController().navigate(R.id.navigation_player, null, navOptions)
                } catch (e: Exception) {
                    Log.e(TAG, "Error playing song", e)
                    Toast.makeText(context, "Error playing song: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
        
        binding.recyclerView.apply {
            adapter = songAdapter
            layoutManager = LinearLayoutManager(context)
        }
    }
    
    fun loadAudioFiles() {
        Log.d(TAG, "Loading audio files...")
        audioScanner?.let { scanner ->
            try {
                val audioFiles = scanner.getAllAudioFiles()
                Log.d(TAG, "Found ${audioFiles.size} audio files")
                
                if (audioFiles.isEmpty()) {
                    context?.let {
                        Toast.makeText(it, "No audio files found", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    songAdapter?.submitList(audioFiles)
                    playerViewModel.setQueue(audioFiles)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading audio files", e)
                context?.let {
                    Toast.makeText(it, "Error loading audio files: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        songAdapter = null
    }
    
    companion object {
        private const val TAG = "Home"
    }
}
