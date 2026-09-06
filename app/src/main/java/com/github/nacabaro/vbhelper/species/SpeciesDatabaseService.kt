package com.github.nacabaro.vbhelper.species

import retrofit2.http.GET
import retrofit2.http.Url

interface SpeciesDatabaseService {
    @GET
    suspend fun getSpeciesDatabase(@Url url: String): SpeciesDatabaseDto
}
