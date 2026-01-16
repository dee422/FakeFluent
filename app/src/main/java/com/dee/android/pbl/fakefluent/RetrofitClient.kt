package com.dee.android.pbl.fakefluent

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    private var service: ApiService? = null
    private var lastUrl = ""

    fun getService(baseUrl: String): ApiService {
        // 自动补齐末尾斜杠，防止崩溃
        val formattedUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"

        if (service == null || lastUrl != formattedUrl) {
            lastUrl = formattedUrl
            service = Retrofit.Builder()
                .baseUrl(formattedUrl)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(ApiService::class.java)
        }
        return service!!
    }
}