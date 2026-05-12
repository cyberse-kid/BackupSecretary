package com.clickwise.backupsecretary.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.clickwise.backupsecretary.databinding.ItemLeadBinding
import com.clickwise.backupsecretary.model.Lead

class LeadsAdapter : ListAdapter<Lead, LeadsAdapter.LeadViewHolder>(LeadDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LeadViewHolder {
        val binding = ItemLeadBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return LeadViewHolder(binding)
    }

    override fun onBindViewHolder(holder: LeadViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class LeadViewHolder(private val binding: ItemLeadBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(lead: Lead) {
            binding.tvContactName.text = lead.sender_name.ifEmpty { "Sin nombre" }
            binding.tvContactPhone.text = lead.sender_phone
            binding.tvMessage.text = lead.text_content
            binding.tvBotResponse.text = lead.bot_response
            binding.tvStatus.text = when (lead.status) {
                "lead_captured" -> "🔥 Lead nuevo"
                "contacted"     -> "📞 Contactado"
                "in_progress"   -> "⏳ En proceso"
                "closed"        -> "✅ Cerrado"
                "lost"          -> "❌ Perdido"
                else            -> lead.status
            }
            binding.tvDate.text = lead.created_at.take(10)
        }
    }

    class LeadDiffCallback : DiffUtil.ItemCallback<Lead>() {
        override fun areItemsTheSame(oldItem: Lead, newItem: Lead) = oldItem._id == newItem._id
        override fun areContentsTheSame(oldItem: Lead, newItem: Lead) = oldItem == newItem
    }
}