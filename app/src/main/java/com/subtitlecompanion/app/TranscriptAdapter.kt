package com.subtitlecompanion.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class TranscriptAdapter(private val onRowClick: (Cue) -> Unit) :
    RecyclerView.Adapter<TranscriptAdapter.RowHolder>() {

    private var cues: List<Cue> = emptyList()
    private var activeIndex: Int = -1

    fun submit(newCues: List<Cue>) {
        cues = newCues
        activeIndex = -1
        notifyDataSetChanged()
    }

    fun setActive(index: Int) {
        val old = activeIndex
        activeIndex = index
        if (old in cues.indices) notifyItemChanged(old)
        if (activeIndex in cues.indices) notifyItemChanged(activeIndex)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_transcript_row, parent, false)
        return RowHolder(v)
    }

    override fun onBindViewHolder(holder: RowHolder, position: Int) {
        val cue = cues[position]
        holder.text.text = cue.text
        val m = (cue.startMs / 1000) / 60
        val s = (cue.startMs / 1000) % 60
        holder.time.text = String.format("%02d:%02d", m, s)
        holder.itemView.setBackgroundColor(if (position == activeIndex) 0x22D4A24C else 0x00000000)
        holder.itemView.setOnClickListener { onRowClick(cue) }
    }

    override fun getItemCount() = cues.size

    class RowHolder(v: View) : RecyclerView.ViewHolder(v) {
        val time: TextView = v.findViewById(R.id.rowTime)
        val text: TextView = v.findViewById(R.id.rowText)
    }
}
