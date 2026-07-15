package com.phenix.wirelessadb.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.phenix.wirelessadb.AdbService
import com.phenix.wirelessadb.R
import com.phenix.wirelessadb.databinding.FragmentDevicesBinding
import com.phenix.wirelessadb.model.TrustedDevice
import com.phenix.wirelessadb.relay.DeviceAuthManager
import com.phenix.wirelessadb.util.AccessibilityHelper.announceForAccessibility
import com.phenix.wirelessadb.viewmodel.AdbViewModel
import com.phenix.wirelessadb.util.applyBottomSystemBarInsets

/**
 * TAB 2: DEVICES (v1.3.0)
 * =======================
 * Trusted device management:
 * - Trusted Devices list (RecyclerView)
 * - Pending Approval section with approve/deny
 */
class DevicesFragment : Fragment() {

  private var _binding: FragmentDevicesBinding? = null
  private val binding get() = _binding!!
  private val viewModel: AdbViewModel by activityViewModels()

  private lateinit var authManager: DeviceAuthManager
  private lateinit var adapter: TrustedDevicesAdapter

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    _binding = FragmentDevicesBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    // Edge-to-edge: keep scrollable content clear of the gesture/navigation bar
    view.applyBottomSystemBarInsets()
    authManager = DeviceAuthManager(requireContext())
    setupViews()
    observeViewModel()
    loadTrustedDevices()
  }

  private fun setupViews() {
    binding.apply {
      // Setup RecyclerView
      adapter = TrustedDevicesAdapter { device ->
        // Remove device callback
        authManager.removeTrustedDeviceById(device.id)
        loadTrustedDevices()
        viewModel.refreshTrustedCount()
      }
      trustedDevicesRecycler.layoutManager = LinearLayoutManager(requireContext())
      trustedDevicesRecycler.adapter = adapter

      // Pending approval buttons
      approveButton.setOnClickListener {
        viewModel.pendingApprovalIp.value?.let { ip ->
          AdbService.approveDevice(requireContext(), ip)
          viewModel.setPendingApproval(null)
          loadTrustedDevices()
          viewModel.refreshTrustedCount()
          approveButton.announceForAccessibility(
            getString(R.string.announcement_device_connected, ip)
          )
        }
      }

      denyButton.setOnClickListener {
        viewModel.pendingApprovalIp.value?.let { ip ->
          AdbService.denyDevice(requireContext(), ip)
          viewModel.setPendingApproval(null)
          denyButton.announceForAccessibility(getString(R.string.announcement_device_disconnected))
        }
      }
    }
  }

  private fun observeViewModel() {
    // Trusted device count
    viewModel.trustedDeviceCount.observe(viewLifecycleOwner) { count ->
      binding.trustedCountText.text = "($count)"
      updateEmptyState(count == 0)
    }

    // Pending approval
    viewModel.pendingApprovalIp.observe(viewLifecycleOwner) { ip ->
      binding.apply {
        if (ip != null) {
          pendingCard.visibility = View.VISIBLE
          pendingIpText.text = getString(R.string.pending_device_format, ip)
        } else {
          pendingCard.visibility = View.GONE
        }
      }
    }
  }

  private fun loadTrustedDevices() {
    val devices = authManager.getTrustedDevices()
    adapter.submitList(devices)
    updateEmptyState(devices.isEmpty())
  }

  private fun updateEmptyState(isEmpty: Boolean) {
    binding.apply {
      emptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
      trustedDevicesRecycler.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }
  }

  override fun onResume() {
    super.onResume()
    loadTrustedDevices()
    viewModel.refreshTrustedCount()
  }

  override fun onDestroyView() {
    super.onDestroyView()
    _binding = null
  }
}
