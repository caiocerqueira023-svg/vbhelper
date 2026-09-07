package com.github.nacabaro.vbhelper.species

import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Url

interface SpeciesDatabaseService {
    @GET
    suspend fun getSpeciesDatabase(@Url url: String): ResponseBody
}
