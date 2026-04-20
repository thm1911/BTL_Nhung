package com.example.btl_nhung.ui.map

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.btl_nhung.R
import com.example.btl_nhung.databinding.FragmentMapBinding
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MapFragment : Fragment() {

    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MapViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.inputMapWidth.setText("500")
        binding.inputMapHeight.setText("300")

        binding.mapCanvas.setOnTargetPickedListener { p ->
            viewModel.onMapTapped(p.x, p.y)
        }

        binding.buttonApplyMapSize.setOnClickListener {
            val width = binding.inputMapWidth.text?.toString()?.toDoubleOrNull()
            val height = binding.inputMapHeight.text?.toString()?.toDoubleOrNull()
            if (width == null || height == null || width <= 0.0 || height <= 0.0) {
                Snackbar.make(binding.root, "Kích thước map không hợp lệ.", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            viewModel.onMapSizeChanged(width, height)
        }
        binding.buttonSetOrigin.setOnClickListener {
            viewModel.sendSetOrigin()
        }
        binding.buttonSendTarget.setOnClickListener {
            viewModel.sendTargetToDevice()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.userMessages.collect { msg ->
                    Snackbar.make(binding.root, msg, Snackbar.LENGTH_SHORT).show()
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.textMeta.text = getString(
                        R.string.map_meta_format,
                        state.robotPose.x,
                        state.robotPose.y,
                        state.robotPose.t,
                        state.target?.x ?: -1.0,
                        state.target?.y ?: -1.0,
                    )
                    binding.mapCanvas.render(
                        mapWidthCm = state.mapWidthCm,
                        mapHeightCm = state.mapHeightCm,
                        robotPose = state.robotPose,
                        target = state.target,
                        trail = state.trail,
                    )
                    binding.mapCanvas.setTargetSelectionEnabled(true)
                    binding.textMapHint.text = getString(R.string.map_tap_hint)
                    binding.cardControlJson.isVisible = !state.lastControlJson.isNullOrBlank()
                    binding.textControlJson.text = state.lastControlJson.orEmpty()
                    binding.cardTargetJson.isVisible = !state.lastTargetJson.isNullOrBlank()
                    binding.textTargetJson.text = state.lastTargetJson.orEmpty()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
