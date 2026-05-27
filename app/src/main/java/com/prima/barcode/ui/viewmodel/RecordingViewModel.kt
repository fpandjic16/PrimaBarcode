package com.prima.barcode.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prima.barcode.data.model.Document
import com.prima.barcode.data.repository.DocumentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RecordingViewModel @Inject constructor(
    private val repository: DocumentRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val documentNo: String = checkNotNull(savedStateHandle["documentNo"])
    private val type: String = checkNotNull(savedStateHandle["type"])

    val document: StateFlow<Document?> = repository.observeDocument(documentNo, type)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun recordScan(lineNo: Int, barcodeNo: String, userId: String, quantity: Double) {
        viewModelScope.launch {
            repository.recordScan(documentNo, type, lineNo, barcodeNo, userId, quantity, null)
        }
    }

    fun setLineScanned(lineNo: Int, scanned: Double) {
        viewModelScope.launch {
            repository.setLineScanned(documentNo, type, lineNo, scanned)
        }
    }

    fun addExtraLine(barcodeNo: String, userId: String, quantity: Double) {
        viewModelScope.launch {
            repository.addExtraLine(documentNo, type, barcodeNo, userId, quantity)
        }
    }

    fun updateExtraLineQuantity(recordingLineNo: Int, quantity: Double) {
        viewModelScope.launch {
            repository.updateExtraLineQuantity(documentNo, type, recordingLineNo, quantity)
        }
    }

    fun deleteExtraLine(recordingLineNo: Int) {
        viewModelScope.launch {
            repository.deleteExtraLine(documentNo, type, recordingLineNo)
        }
    }
}
