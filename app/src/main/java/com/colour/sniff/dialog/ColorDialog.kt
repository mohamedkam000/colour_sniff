package com.colour.sniff.dialog

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import androidx.recyclerview.widget.LinearLayoutManager
import com.colour.sniff.adapter.ColorAdapter
import com.colour.sniff.database.ColorViewModel
import com.colour.sniff.databinding.DialogColorBinding

class ColorDialog(
    context: Context,
    private val colorViewModel: ColorViewModel,
    private val colorAdapter: ColorAdapter,
    private val onClearColor: () -> Unit
) : Dialog(context) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = DialogColorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        binding.rvColor.layoutManager = layoutManager
        binding.rvColor.setHasFixedSize(true)
        binding.rvColor.adapter = colorAdapter

        binding.btnAddColor.setOnClickListener {
            val name = binding.edtNameOfList.text.toString()

            if (name.isNotEmpty()) {
                colorAdapter.colors.forEach {
                    it.name = name
                    colorViewModel.insertColor(it)
                }
                onClearColor()
                dismiss()
            }
        }

        binding.btnCancel.setOnClickListener {
            dismiss()
        }
    }
}