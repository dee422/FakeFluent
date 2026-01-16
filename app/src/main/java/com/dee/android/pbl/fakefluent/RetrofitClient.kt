package com.dee.android.pbl.fakefluent

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    // 创建一个变量，根据需要更改地址
    var baseUrl = "https://api.siliconflow.com/"

    fun getService(): ApiService {
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}