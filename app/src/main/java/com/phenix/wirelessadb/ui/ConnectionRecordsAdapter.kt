package com.phenix.wirelessadb.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.phenix.wirelessadb.R
import com.phenix.wirelessadb.databinding.ItemConnectionRecordBinding
import com.phenix.wirelessadb.model.ConnectionMode
import com.phenix.wirelessadb.model.ConnectionRecord
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * RecyclerView adapter for connection records list.
 * Displays connection history with timestamp, duration, and mode.
 */
class ConnectionRecordsAdapter : ListAdapter<ConnectionRecord, ConnectionRecordsAdapter.ViewHolder>(DiffCallback()) {

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
    val binding = ItemConnectionRecordBinding.inflate(
      LayoutInflater.from(parent.context),
      parent,
      false
    )
    return ViewHolder(binding)
  }

  override fun onBindViewHolder(holder: ViewHolder, position: Int) {
    holder.bind(getItem(position))
  }

  inner class ViewHolder(
    private val binding: ItemConnectionRecordBinding
  ) : RecyclerView.ViewHolder(binding.root) {

    fun bind(record: ConnectionRecord) {
      binding.apply {
        // Format timestamp
        connectionTimestamp.text = formatTimestamp(record.timestamp)

        // Format duration
        connectionDuration.text = root.context.getString(
          R.string.stats_duration_format,
          formatDuration(record.duration)
        )

        // Display connection mode
        connectionMode.text = formatMode(record.mode)

        // Set status icon based on success
        if (record.successful) {
          statusIcon.setImageResource(R.drawable.ic_check)
          statusIcon.setColorFilter(
            root.context.getColor(android.R.color.holo_green_dark)
          )
          connectionIcon.setColorFilter(
            root.context.getColor(R.color.primary)
          )
        } else {
          statusIcon.setImageResource(R.drawable.ic_close)
          statusIcon.setColorFilter(
            root.context.getColor(R.color.error)
          )
          connectionIcon.setColorFilter(
            root.context.getColor(android.R.color.darker_gray)
          )
        }
      }
    }

    private fun formatTimestamp(timestamp: Long): String {
      val dateFormat = SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault())
      return dateFormat.format(Date(timestamp))
    }

    private fun formatDuration(durationMs: Long): String {
      if (durationMs == 0L) {
        return binding.root.context.getString(R.string.stats_active)
      }

      val seconds = durationMs / 1000
      val minutes = seconds / 60
      val hours = minutes / 60
      val days = hours / 24

      return when {
        days > 0 -> {
          val h = hours % 24
          if (h > 0) "${days}d ${h}h" else "${days}d"
        }
        hours > 0 -> {
          val m = minutes % 60
          if (m > 0) "${hours}h ${m}m" else "${hours}h"
        }
        minutes > 0 -> "${minutes}m"
        else -> "${seconds}s"
      }
    }

    private fun formatMode(mode: ConnectionMode): String {
      return when (mode) {
        ConnectionMode.LOCAL_WIFI -> binding.root.context.getString(R.string.mode_local)
        ConnectionMode.TAILSCALE_DIRECT -> binding.root.context.getString(R.string.mode_tailscale_direct)
        ConnectionMode.TAILSCALE_RELAY -> binding.root.context.getString(R.string.mode_tailscale_relay)
        ConnectionMode.WARPGATE -> binding.root.context.getString(R.string.mode_warpgate)
        ConnectionMode.P2P_TOKEN -> binding.root.context.getString(R.string.mode_p2p)
      }
    }
  }

  private class DiffCallback : DiffUtil.ItemCallback<ConnectionRecord>() {
    override fun areItemsTheSame(oldItem: ConnectionRecord, newItem: ConnectionRecord): Boolean {
      return oldItem.timestamp == newItem.timestamp
    }

    override fun areContentsTheSame(oldItem: ConnectionRecord, newItem: ConnectionRecord): Boolean {
      return oldItem == newItem
    }
  }
}
