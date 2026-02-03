package com.phenix.wirelessadb.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.phenix.wirelessadb.R
import com.phenix.wirelessadb.databinding.ItemTrustedDeviceBinding
import com.phenix.wirelessadb.model.TrustedDevice
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * RecyclerView adapter for trusted devices list.
 */
class TrustedDevicesAdapter(
  private val onRemoveClick: (TrustedDevice) -> Unit
) : ListAdapter<TrustedDevice, TrustedDevicesAdapter.ViewHolder>(DiffCallback()) {

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
    val binding = ItemTrustedDeviceBinding.inflate(
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
    private val binding: ItemTrustedDeviceBinding
  ) : RecyclerView.ViewHolder(binding.root) {

    fun bind(device: TrustedDevice) {
      binding.apply {
        deviceName.text = device.getDisplayName()
        deviceIp.text = device.ip ?: root.context.getString(R.string.devices_unknown_ip)
        deviceAddedDate.text = root.context.getString(
          R.string.devices_added_date,
          formatDate(device.addedAt)
        )

        removeButton.setOnClickListener {
          onRemoveClick(device)
        }
      }
    }

    private fun formatDate(timestamp: Long): String {
      val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
      return dateFormat.format(Date(timestamp))
    }
  }

  private class DiffCallback : DiffUtil.ItemCallback<TrustedDevice>() {
    override fun areItemsTheSame(oldItem: TrustedDevice, newItem: TrustedDevice): Boolean {
      return oldItem.ip == newItem.ip
    }

    override fun areContentsTheSame(oldItem: TrustedDevice, newItem: TrustedDevice): Boolean {
      return oldItem == newItem
    }
  }
}
