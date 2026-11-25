package com.drdisagree.xmorphclock.domain.usecase

import com.drdisagree.xmorphclock.domain.model.WeatherCity
import com.drdisagree.xmorphclock.domain.repository.FaceDataRepository

class SetWeatherCityUseCase(private val repository: FaceDataRepository) {
    suspend operator fun invoke(city: WeatherCity?) = repository.setWeatherCity(city)
}
