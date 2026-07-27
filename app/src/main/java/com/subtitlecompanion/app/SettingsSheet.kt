package com.subtitlecompanion.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup

class SettingsSheet : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.sheet_settings, container, false)
        val settings = SettingsStore.load()

        val shapeGroup = view.findViewById<ChipGroup>(R.id.shapeGroup)
        val posGroup = view.findViewById<ChipGroup>(R.id.posGroup)
        val bgGroup = view.findViewById<ChipGroup>(R.id.bgGroup)
        val opacitySeek = view.findViewById<SeekBar>(R.id.opacitySeek)
        val fontSeek = view.findViewById<SeekBar>(R.id.fontSeek)
        val nextToggle = view.findViewById<Chip>(R.id.nextToggle)

        selectChip(shapeGroup, settings.shape)
        selectChip(posGroup, settings.position)
        selectChip(bgGroup, settings.bg)
        opacitySeek.progress = settings.opacity
        fontSeek.progress = settings.fontSize
        nextToggle.isChecked = settings.showNext

        fun persist() {
            val s = SettingsStore.load()
            s.shape = chipValue(shapeGroup) ?: s.shape
            s.position = chipValue(posGroup) ?: s.position
            s.bg = chipValue(bgGroup) ?: s.bg
            s.opacity = opacitySeek.progress
            s.fontSize = fontSeek.progress.coerceAtLeast(14)
            s.showNext = nextToggle.isChecked
            SettingsStore.save(s)
        }

        shapeGroup.setOnCheckedStateChangeListener { _, _ -> persist() }
        posGroup.setOnCheckedStateChangeListener { _, _ -> persist() }
        bgGroup.setOnCheckedStateChangeListener { _, _ -> persist() }
        nextToggle.setOnCheckedChangeListener { _, _ -> persist() }
        opacitySeek.setOnSeekBarChangeListener(simpleSeek { persist() })
        fontSeek.setOnSeekBarChangeListener(simpleSeek { persist() })

        view.findViewById<MaterialButton>(R.id.closeSheet).setOnClickListener { dismiss() }
        return view
    }

    private fun chipValue(group: ChipGroup): String? {
        val id = group.checkedChipId
        if (id == View.NO_ID) return null
        return group.findViewById<Chip>(id)?.tag as? String
    }

    private fun selectChip(group: ChipGroup, value: String) {
        for (i in 0 until group.childCount) {
            val chip = group.getChildAt(i) as Chip
            if (chip.tag == value) { chip.isChecked = true; return }
        }
    }

    private fun simpleSeek(onChange: () -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) { if (fromUser) onChange() }
        override fun onStartTrackingTouch(sb: SeekBar?) {}
        override fun onStopTrackingTouch(sb: SeekBar?) {}
    }
}
