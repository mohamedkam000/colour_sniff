package com.colour.sniff.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.colour.sniff.adapter.ColorListAdapter
import com.colour.sniff.database.ColorViewModel
import com.colour.sniff.databinding.FragmentColorsBinding
import com.colour.sniff.dialog.ColorDetailDialog
import com.colour.sniff.model.UserColor
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class ColorsFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentColorsBinding? = null
    private val binding get() = _binding!!

    private val colorViewModel: ColorViewModel by lazy {
        ViewModelProvider(
            this,
            ColorViewModel.ColorViewModelFactory(requireActivity().application)
        )[ColorViewModel::class.java]
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentColorsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val colorListAdapter = ColorListAdapter(requireContext()) {
            val detailDialog = ColorDetailDialog(requireContext(), it, deleteColor)
            detailDialog.show()
        }

        colorViewModel.getAllColor().observe(viewLifecycleOwner, { colors ->
            val names = LinkedHashSet(colors.map { it.name }).toList()

            colorListAdapter.notifyData(names, colors)
        })
        val layoutManager = LinearLayoutManager(context)

        binding.rvColorList.layoutManager = layoutManager
        binding.rvColorList.setHasFixedSize(true)

        binding.rvColorList.adapter = colorListAdapter
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private val deleteColor: (UserColor) -> Unit = {
        colorViewModel.deleteColor(it)
    }
}