package com.first.meragaana.ui.player

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.first.meragaana.databinding.FragmentPlayerBinding
import com.first.meragaana.service.MediaPlayerService
import com.first.meragaana.viewmodel.PlayerViewModel
import kotlinx.coroutines.launch

class PlayerFragment : Fragment() {
    private var _binding: FragmentPlayerBinding? = null
    private val binding get() = _binding!!
    private val viewModel: PlayerViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlayerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
        observeViewModel()
        
        // Hide the action bar
        (activity as? AppCompatActivity)?.supportActionBar?.hide()
        
        // Make the fragment full screen
        activity?.window?.apply {
            decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Show the action bar when leaving the player
        (activity as? AppCompatActivity)?.supportActionBar?.show()
        _binding = null
    }

    private fun setupUI() {
        with(binding) {
            playPauseButton.setOnClickListener {
                viewModel.playPause()
            }

            nextButton.setOnClickListener {
                viewModel.next()
            }

            previousButton.setOnClickListener {
                viewModel.previous()
            }

            shuffleButton.setOnClickListener {
                viewModel.toggleShuffle()
            }

            repeatButton.setOnClickListener {
                viewModel.toggleRepeatMode()
            }

            seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        viewModel.seekTo(progress.toLong())
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.currentSong.collect { song ->
                        Log.d(TAG, "Current song updated: ${song?.title}")
                        binding.apply {
                            songTitleTextView.text = song?.title ?: "No song playing"
                            artistTextView.text = song?.artist ?: "Unknown Artist"
                            
                            // Update album art if available
                            song?.let {
                                // Default to app icon if no album art
                                albumArtImageView.setImageResource(android.R.drawable.ic_media_play)
                            }
                        }
                    }
                }

                launch {
                    viewModel.isPlaying.collect { isPlaying ->
                        Log.d(TAG, "Play state updated: $isPlaying")
                        binding.playPauseButton.isSelected = isPlaying
                    }
                }

                launch {
                    viewModel.currentPosition.collect { position ->
                        binding.apply {
                            seekBar.progress = position.toInt()
                            currentTimeTextView.text = formatDuration(position)
                        }
                    }
                }

                launch {
                    viewModel.duration.collect { duration ->
                        binding.apply {
                            seekBar.max = duration.toInt()
                            totalTimeTextView.text = formatDuration(duration)
                        }
                    }
                }

                launch {
                    viewModel.isShuffleEnabled.collect { isEnabled ->
                        Log.d(TAG, "Shuffle state updated: $isEnabled")
                        binding.shuffleButton.apply {
                            isSelected = isEnabled
                            alpha = if (isEnabled) 1.0f else 0.7f
                        }
                    }
                }

                launch {
                    viewModel.repeatMode.collect { mode ->
                        Log.d(TAG, "Repeat mode updated: $mode")
                        binding.repeatButton.apply {
                            isSelected = mode != MediaPlayerService.REPEAT_OFF
                            alpha = when (mode) {
                                MediaPlayerService.REPEAT_ONE -> 1.0f
                                MediaPlayerService.REPEAT_ALL -> 0.85f
                                else -> 0.7f
                            }
                        }
                    }
                }
            }
        }
    }

    companion object {
        private const val TAG = "PlayerFragment"
    }

    private fun formatDuration(durationMs: Long): String {
        val totalSeconds = durationMs / 1000
        val minutes = totalSeconds / 60
        val remainingSeconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, remainingSeconds)
    }
}
