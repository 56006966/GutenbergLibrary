package com.kdhuf.projectgutenberglibrary.data.remote

import com.kdhuf.projectgutenberglibrary.BuildConfig
import com.kdhuf.projectgutenberglibrary.data.catalog.RemoteCatalogDataSource
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitInstance {

    val client = OkHttpClient.Builder()
        .retryOnConnectionFailure(false)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request: Request = chain.request()
                .newBuilder()
                .header("User-Agent", BuildConfig.CATALOG_API_USER_AGENT)
                .header("Accept", "application/json")
                .build()
            chain.proceed(request)
        }
        .build()

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.CATALOG_API_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    private val api: CatalogBackendApi by lazy {
        retrofit.create(CatalogBackendApi::class.java)
    }

    val catalogDataSource: RemoteCatalogDataSource by lazy {
        RemoteCatalogDataSource(api)
    }
}
