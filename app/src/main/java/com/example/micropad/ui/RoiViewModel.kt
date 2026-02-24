package com.example.micropad.ui

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import com.example.micropad.data.model.Roi
import com.example.micropad.data.model.DyeLabel

class RoiViewModel : ViewModel() {

    private val _rois = MutableStateFlow<List<Roi>>(emptyList())
    val rois: StateFlow<List<Roi>> = _rois

    fun setRois(list: List<Roi>) {
        _rois.value = list
    }

    fun assignLabel(roiId: Int, label: DyeLabel) {

        val updated = _rois.value.map {
            if (it.id == roiId) it.copy(label = label)
            else it
        }

        _rois.value = updated
    }

    fun validateLabels(): Boolean {

        val current = _rois.value

        // all labeled
        if (current.any { it.label == null }) return false

        // no duplicates
        val labels = current.map { it.label }
        return labels.distinct().size == labels.size
    }
}