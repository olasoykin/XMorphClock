package com.drdisagree.xmorphclock.domain.usecase

import com.drdisagree.xmorphclock.domain.model.WeatherCity
import com.drdisagree.xmorphclock.domain.repository.FaceDataRepository

class SearchWeatherCitiesUseCase(private val repository: FaceDataRepository) {
    suspend operator fun invoke(query: String): List<WeatherCity> = repository.searchWeatherCities(query)
}
