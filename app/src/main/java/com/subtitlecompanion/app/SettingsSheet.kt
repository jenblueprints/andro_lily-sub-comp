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
import com.google.android.material.textfield.TextInputEditText

class SettingsSheet : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.sheet_settings, container, false)
        val settings = SettingsStore.load()

        val shapeGroup = view.findViewById<ChipGroup>(R.id.shapeGroup)
        val posGroup = view.findViewById<ChipGroup>(R.id.posGroup)
        val bgGroup = view.findViewById<ChipGroup>(R.id.bgGroup)
        val textColorGroup = view.findViewById<ChipGroup>(R.id.textColorGroup)
        val panelColorGroup = view.findViewById<ChipGroup>(R.id.panelColorGroup)
        val opacitySeek = view.findViewById<SeekBar>(R.id.opacitySeek)
        val fontSeek = view.findViewById<SeekBar>(R.id.fontSeek)
        val nextToggle = view.findViewById<Chip>(R.id.nextToggle)
        val boldToggle = view.findViewById<Chip>(R.id.boldToggle)
        val scaleValueText = view.findViewById<android.widget.TextView>(R.id.scaleValueText)
        val resetScaleButton = view.findViewById<MaterialButton>(R.id.resetScaleButton)
        val supportLinkInput = view.findViewById<TextInputEditText>(R.id.supportLinkInput)

        selectChip(shapeGroup, settings.shape)
        selectChip(posGroup, settings.position)
        selectChip(bgGroup, settings.bg)
        selectChip(textColorGroup, settings.color)
        selectChip(panelColorGroup, settings.panelColor)
        opacitySeek.progress = settings.opacity
        fontSeek.progress = settings.fontSize
        nextToggle.isChecked = settings.showNext
        boldToggle.isChecked = settings.textBold
        supportLinkInput.setText(settings.supportLinkUrl)
        scaleValueText.text = "${settings.overlayScalePct.toInt()}%"

        fun persist() {
            val s = SettingsStore.load()
            s.shape = chipValue(shapeGroup) ?: s.shape
            s.position = chipValue(posGroup) ?: s.position
            s.bg = chipValue(bgGroup) ?: s.bg
            s.color = chipValue(textColorGroup) ?: s.color
            s.panelColor = chipValue(panelColorGroup) ?: s.panelColor
            s.opacity = opacitySeek.progress
            s.fontSize = fontSeek.progress.coerceAtLeast(14)
            s.showNext = nextToggle.isChecked
            s.textBold = boldToggle.isChecked
            SettingsStore.save(s)
            // Push the change to a currently-running floating overlay
            // immediately, instead of only taking effect on the next start.
            OverlayStyleBus.notifyChanged()
        }

        shapeGroup.setOnCheckedStateChangeListener { _, _ -> persist() }
        posGroup.setOnCheckedStateChangeListener { _, _ -> persist() }
        bgGroup.setOnCheckedStateChangeListener { _, _ -> persist() }
        textColorGroup.setOnCheckedStateChangeListener { _, _ -> persist() }
        panelColorGroup.setOnCheckedStateChangeListener { _, _ -> persist() }
        nextToggle.setOnCheckedChangeListener { _, _ -> persist() }
        boldToggle.setOnCheckedChangeListener { _, _ -> persist() }
        opacitySeek.setOnSeekBarChangeListener(simpleSeek { persist() })
        fontSeek.setOnSeekBarChangeListener(simpleSeek { persist() })

        resetScaleButton.setOnClickListener {
            SettingsStore.update { it.overlayScalePct = 100f }
            scaleValueText.text = "100%"
            OverlayStyleBus.notifyChanged()
        }

        supportLinkInput.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                SettingsStore.update { it.supportLinkUrl = supportLinkInput.text?.toString()?.trim() ?: "" }
            }
        }

        view.findViewById<MaterialButton>(R.id.closeSheet).setOnClickListener {
            SettingsStore.update { it.supportLinkUrl = supportLinkInput.text?.toString()?.trim() ?: "" }
            dismiss()
        }
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
