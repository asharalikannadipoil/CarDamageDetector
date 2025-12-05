package com.cardamage.detector.ui.viewmodel

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cardamage.detector.data.model.DamageAnalysisResult
import com.cardamage.detector.data.repository.DamageResultRepository
import com.cardamage.detector.gallery.ImagePicker
import com.cardamage.detector.ml.DamageDetectionService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val damageDetectionService: DamageDetectionService,
    private val imagePickerService: ImagePicker,
    private val repository: DamageResultRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val _analysisResult = MutableStateFlow<DamageAnalysisResult?>(null)
    val analysisResult: StateFlow<DamageAnalysisResult?> = _analysisResult.asStateFlow()

    private val _currentBitmap = MutableStateFlow<Bitmap?>(null)
    val currentBitmap: StateFlow<Bitmap?> = _currentBitmap.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    // Roboflow API settings
    private val _roboflowApiKey = MutableStateFlow("")
    val roboflowApiKey: StateFlow<String> = _roboflowApiKey.asStateFlow()
    
    private val _useRoboflow = MutableStateFlow(true)
    val useRoboflow: StateFlow<Boolean> = _useRoboflow.asStateFlow()

    val recentResults = repository.getAllResults()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun analyzeImage(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            _errorMessage.value = null

            try {
                val bitmap = imagePickerService.loadImageFromUri(uri)
                if (bitmap == null) {
                    _errorMessage.value = "Failed to load image"
                    _uiState.value = _uiState.value.copy(isLoading = false, error = "Failed to load image")
                    return@launch
                }

                if (!imagePickerService.validateImage(bitmap)) {
                    _errorMessage.value = "Invalid image format or size"
                    _uiState.value = _uiState.value.copy(isLoading = false, error = "Invalid image format or size")
                    return@launch
                }

                _currentBitmap.value = bitmap
                
                // Use enhanced detection with Roboflow integration
                val result = damageDetectionService.detectDamageEnhanced(
                    bitmap = bitmap,
                    imagePath = uri.toString(),
                    apiKey = _roboflowApiKey.value,
                    useRoboflow = _useRoboflow.value
                )
                _analysisResult.value = result

                // Save result to database
                repository.saveResult(result)

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    analysisCompleted = true
                )

            } catch (e: Exception) {
                val errorMsg = "Analysis failed: ${e.message}"
                _errorMessage.value = errorMsg
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = errorMsg
                )
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun resetAnalysis() {
        _analysisResult.value = null
        _currentBitmap.value?.recycle()
        _currentBitmap.value = null
        _uiState.value = MainUiState()
    }

    fun deleteResult(resultId: Long) {
        viewModelScope.launch {
            try {
                repository.deleteResult(resultId)
            } catch (e: Exception) {
                _errorMessage.value = "Failed to delete result: ${e.message}"
            }
        }
    }

    fun setRoboflowApiKey(apiKey: String) {
        _roboflowApiKey.value = apiKey
    }
    
    fun toggleRoboflowUsage(use: Boolean) {
        _useRoboflow.value = use
    }
    
    fun getStatistics() = flow {
        try {
            val stats = repository.getStatistics()
            emit(stats)
        } catch (e: Exception) {
            _errorMessage.value = "Failed to load statistics: ${e.message}"
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(),
        initialValue = null
    )

    override fun onCleared() {
        super.onCleared()
        _currentBitmap.value?.recycle()
        damageDetectionService.close()
    }
}

data class MainUiState(
    val isLoading: Boolean = false,
    val analysisCompleted: Boolean = false,
    val error: String? = null
)