package com.tapflow.app

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.tapflow.app.databinding.ItemHistoryBinding

class HistoryAdapter(private var sessions: List<TapSession>) :
    RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemHistoryBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val session = sessions[position]
        holder.binding.tapCountValue.text = "${session.tapCount} taps"
        holder.binding.dateValue.text = session.date
        holder.binding.durationValue.text = formatDuration(session.durationSeconds)
    }

    override fun getItemCount() = sessions.size

    fun updateSessions(newSessions: List<TapSession>) {
        sessions = newSessions
        notifyDataSetChanged()
    }

    private fun formatDuration(seconds: Long): String {
        val mins = seconds / 60
        val secs = seconds % 60
        return "${mins}m ${secs}s"
    }
}
