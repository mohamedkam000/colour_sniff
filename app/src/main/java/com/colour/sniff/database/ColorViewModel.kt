package com.colour.sniff.database

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.CreationExtras
import androidx.lifecycle.viewModelScope
import com.colour.sniff.model.UserColor
import kotlinx.coroutines.launch

class ColorViewModel(application: Application) : AndroidViewModel(application) {
    private val colorRepository = ColorRepository(application)

    fun insertColor(color: UserColor) = viewModelScope.launch {
        colorRepository.insertColor(color)
    }

    fun deleteColor(color: UserColor) = viewModelScope.launch {
        colorRepository.deleteColor(color)
    }

    fun getAllColor(): LiveData<List<UserColor>> {
        return colorRepository.getAllColor()
    }

    class ColorViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            if (modelClass.isAssignableFrom(ColorViewModel::class.java)) {
                return ColorViewModel(application) as T
            }
            throw IllegalArgumentException("Unable to construct viewModel")
        }
    }
}