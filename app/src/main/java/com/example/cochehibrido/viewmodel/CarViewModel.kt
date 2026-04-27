package com.example.cochehibrido.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cochehibrido.data.Car
import com.example.cochehibrido.data.CarRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CarUiState(
    val marca: String = "",
    val modelo: String = "",
    val matricula: String = "",
    val kmActuales: String = "",
    val isSaved: Boolean = false
)

class CarViewModel(
    private val carRepository: CarRepository
) : ViewModel() {

    val currentCar = carRepository.getCar().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null
    )

    private val _uiState = MutableStateFlow(CarUiState())
    val uiState: StateFlow<CarUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            currentCar.collect { car ->
                if (car != null) {
                    _uiState.value = CarUiState(
                        marca = car.marca,
                        modelo = car.modelo,
                        matricula = car.matricula,
                        kmActuales = car.kmActuales.toString(),
                        isSaved = _uiState.value.isSaved
                    )
                }
            }
        }
    }

    fun onMarcaChange(value: String) {
        _uiState.value = _uiState.value.copy(marca = value, isSaved = false)
    }

    fun onModeloChange(value: String) {
        _uiState.value = _uiState.value.copy(modelo = value, isSaved = false)
    }

    fun onMatriculaChange(value: String) {
        _uiState.value = _uiState.value.copy(matricula = value, isSaved = false)
    }

    fun onKmChange(value: String) {
        _uiState.value = _uiState.value.copy(kmActuales = value, isSaved = false)
    }

    fun saveCar() {
        val km = _uiState.value.kmActuales.toIntOrNull() ?: 0
        val car = Car(
            marca = _uiState.value.marca.trim(),
            modelo = _uiState.value.modelo.trim(),
            matricula = _uiState.value.matricula.trim(),
            kmActuales = km
        )

        viewModelScope.launch {
            carRepository.saveCar(car)
            _uiState.value = _uiState.value.copy(isSaved = true)
        }
    }
}
