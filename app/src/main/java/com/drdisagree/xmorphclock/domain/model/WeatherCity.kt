package com.drdisagree.xmorphclock.domain.model

data class WeatherCity(
    val name: String,
    val region: String = "",
    val country: String = "",
    val latitude: Double,
    val longitude: Double
) {
    val displaySubtitle: String
        get() = listOf(region, country)
            .filter { it.isNotBlank() && !it.equals(name, ignoreCase = true) }
            .distinct()
            .joinToString(", ")

    val fullName: String
        get() = listOf(name, region, country)
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(", ")
}
