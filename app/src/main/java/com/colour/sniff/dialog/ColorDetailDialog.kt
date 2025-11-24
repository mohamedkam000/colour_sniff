package com.colour.sniff.dialog

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import com.colour.sniff.R
import com.colour.sniff.databinding.DialogColorDetailBinding
import com.colour.sniff.model.UserColor

class ColorDetailDialog(
    context: Context,
    private val color: UserColor,
    private val onRemove: (UserColor) -> Unit
) : Dialog(context) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = DialogColorDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setTitle(context.resources.getString(R.string.your_color))

        binding.viewColorPreview.setBackgroundColor(Color.parseColor(color.hex))

        binding.txtRgb.text = ("RGB(${color.r}, ${color.g}, ${color.b})")
        binding.txtHex.text = ("Hex : ${color.hex}")
        binding.txtHsl.text = ("HSL(${color.h}, ${color.s}, ${color.l})")

        binding.btnCancel.setOnClickListener { dismiss() }

        binding.btnRemoveColor.setOnClickListener {
            onRemove(color)
            dismiss()
        }
    }
}