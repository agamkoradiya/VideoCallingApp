package com.example.codefunvideocallingtest.ui.fragment

import android.Manifest
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.app.ActivityCompat
import androidx.navigation.fragment.findNavController
import com.example.codefunvideocallingtest.databinding.FragmentHomeBinding
import dagger.hilt.android.AndroidEntryPoint
import java.util.*

@AndroidEntryPoint
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val PERIMISSION_CODE = 101

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


        checkPermission(
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ),
            PERIMISSION_CODE
        )

        binding.testCreateRoomBtn.setOnClickListener {
            val roomName = binding.roomNameEdt.text.toString().trim()
            if (roomName.isNotEmpty()) {
                val action =
                    HomeFragmentDirections.actionHomeFragmentToCallFragment(roomName, false)
                findNavController().navigate(action)
            }
        }

        binding.testJoinRoomBtn.setOnClickListener {
            val roomName = binding.roomNameEdt.text.toString().trim()
            if (roomName.isNotEmpty()) {
                val action = HomeFragmentDirections.actionHomeFragmentToCallFragment(roomName, true)
                findNavController().navigate(action)

            }
        }

        binding.uiCreateRoomBtn.setOnClickListener {
            val roomName = binding.roomNameEdt.text.toString().trim()
            if (roomName.isNotEmpty()) {
                val action =
                    HomeFragmentDirections.actionHomeFragmentToUiCallFragment(roomName, false)
                findNavController().navigate(action)
            }
        }

        binding.uiJoinRoomBtn.setOnClickListener {
            val roomName = binding.roomNameEdt.text.toString().trim()
            if (roomName.isNotEmpty()) {
                val action =
                    HomeFragmentDirections.actionHomeFragmentToUiCallFragment(roomName, true)
                findNavController().navigate(action)
            }
        }

        binding.screenShareCreateRoomBtn.setOnClickListener {
            val roomName = binding.roomNameEdt.text.toString().trim()
            if (roomName.isNotEmpty()) {
                val action =
                    HomeFragmentDirections.actionHomeFragmentToScreenShareCallFragment(roomName, false)
                findNavController().navigate(action)
            }
        }

        binding.screenShareJoinRoomBtn.setOnClickListener {
            val roomName = binding.roomNameEdt.text.toString().trim()
            if (roomName.isNotEmpty()) {
                val action =
                    HomeFragmentDirections.actionHomeFragmentToScreenShareCallFragment(roomName, true)
                findNavController().navigate(action)
            }
        }

    }

    // Function to check and request permission.
    private fun checkPermission(permission: Array<String>, requestCode: Int) {
        ActivityCompat.requestPermissions(requireActivity(), permission, requestCode)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}