package com.github.nacabaro.vbhelper.species

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object SpeciesDatabaseClient {
    fun create(): SpeciesDatabaseService = Retrofit.Builder()
        .baseUrl("https://raw.githubusercontent.com/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(SpeciesDatabaseService::class.java)
}
