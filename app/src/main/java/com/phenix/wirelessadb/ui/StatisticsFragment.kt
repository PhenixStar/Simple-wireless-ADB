package com.phenix.wirelessadb.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.phenix.wirelessadb.R
import com.phenix.wirelessadb.databinding.FragmentStatisticsBinding
import com.phenix.wirelessadb.viewmodel.StatisticsViewModel

/**
 * TAB 3: STATISTICS (v1.3.0)
 * ==========================
 * Connection statistics dashboard:
 * - Total Uptime: Service uptime since install
 * - Total Connections: Number of successful connections
 * - Reliability Score: Calculated reliability percentage
 * - Recent Connections: RecyclerView with connection history
 */
class StatisticsFragment : Fragment() {

  private var _binding: FragmentStatisticsBinding? = null
  private val binding get() = _binding!!
  private val viewModel: StatisticsViewModel by viewModels()

  private lateinit var adapter: ConnectionRecordsAdapter

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    _binding = FragmentStatisticsBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    setupViews()
    observeViewModel()
  }

  private fun setupViews() {
    binding.apply {
      // Setup RecyclerView
      adapter = ConnectionRecordsAdapter()
      recentConnectionsRecycler.layoutManager = LinearLayoutManager(requireContext())
      recentConnectionsRecycler.adapter = adapter
    }
  }

  private fun observeViewModel() {
    // Total uptime
    viewModel.totalUptime.observe(viewLifecycleOwner) { uptime ->
      binding.uptimeValue.text = uptime
    }

    // Total connections
    viewModel.totalConnections.observe(viewLifecycleOwner) { count ->
      binding.connectionsValue.text = count.toString()
    }

    // Reliability score
    viewModel.reliabilityScore.observe(viewLifecycleOwner) { score ->
      binding.apply {
        // Format score as percentage
        reliabilityValue.text = getString(R.string.stats_reliability_format, score)

        // Update icon color based on score
        val colorRes = viewModel.getReliabilityColor(score)
        val color = requireContext().getColor(colorRes)
        reliabilityIcon.setColorFilter(color)
        reliabilityValue.setTextColor(color)
      }
    }

    // Connection history
    viewModel.connectionHistory.observe(viewLifecycleOwner) { history ->
      binding.apply {
        // Update count
        recentCountText.text = "(${history.size})"

        // Update list
        adapter.submitList(history)

        // Show/hide empty state
        updateEmptyState(history.isEmpty())
      }
    }

    // Error handling
    viewModel.error.observe(viewLifecycleOwner) { error ->
      error?.let {
        // TODO: Show error message to user (e.g., Snackbar)
        viewModel.clearError()
      }
    }
  }

  private fun updateEmptyState(isEmpty: Boolean) {
    binding.apply {
      emptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
      recentConnectionsRecycler.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }
  }

  override fun onResume() {
    super.onResume()
    // Refresh statistics when fragment becomes visible
    viewModel.refreshStatistics()
  }

  override fun onDestroyView() {
    super.onDestroyView()
    _binding = null
  }
}
