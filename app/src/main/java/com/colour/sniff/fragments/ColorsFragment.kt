package com.colour.sniff.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.colour.sniff.R
import com.colour.sniff.adapter.ColorListAdapter
import com.colour.sniff.database.ColorViewModel
import com.colour.sniff.dialog.ColorDetailDialog
import com.colour.sniff.model.UserColor
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.android.synthetic.main.fragment_colors.*


class ColorsFragment : BottomSheetDialogFragment() {

    private val colorViewModel: ColorViewModel by lazy {
        ViewModelProvider(
            this,
            ColorViewModel.ColorViewModelFactory(requireActivity().application)
        )[ColorViewModel::class.java]
    }


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_colors, container, false)
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

        rv_color_list.layoutManager = layoutManager
        rv_color_list.setHasFixedSize(true)

        rv_color_list.adapter = colorListAdapter
    }

    private val deleteColor: (UserColor) -> Unit = {
        colorViewModel.deleteColor(it)
    }

}