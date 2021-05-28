/*
 * Stefans Smart Home Project
 * Copyright (C) 2021 Stefan Oltmann
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package de.stefan_oltmann.smarthome.alexaskill.network

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

object RestApiClientFactory {

    fun createRestApiClient(
        baseUrl: String,
        authCode: String
    ): RestApi {

        val retrofit: Retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(createHttpClient(authCode))
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        return retrofit.create(RestApi::class.java)
    }

    private fun createHttpClient(authCode: String): OkHttpClient {

        /*
         * Create a trust manager that does not validate certificate chains.
         * Because we use a self-signed certificate it has never a valid chain.
         */
        val trustManagers = arrayOf<TrustManager>(TrustAllX509TrustManager)

        /* Install the all-trusting trust manager */
        val sslContext = SSLContext.getInstance("SSL")
        sslContext.init(null, trustManagers, SecureRandom())

        /* Create an ssl socket factory with our all-trusting manager */
        val sslSocketFactory = sslContext.socketFactory

        val okHttpClientBuilder = OkHttpClient.Builder()

        okHttpClientBuilder.sslSocketFactory(sslSocketFactory, trustManagers[0] as X509TrustManager)

        /*
         * Don't check the hostname.
         * We want to use the same certificate for testing and production.
         */
        okHttpClientBuilder.hostnameVerifier { _, _ -> true }

        /*
         * Add the Auth Code to the HTTP header.
         * This is the way we authenticate.
         */
        okHttpClientBuilder.addInterceptor(AuthCodeHeaderInterceptor(authCode))

        return okHttpClientBuilder.build()
    }

    /*
     * Empty trust manager that does not validate certificate chains.
     * Because we use a self-signed certificate it has never a valid chain.
     */
    private object TrustAllX509TrustManager : X509TrustManager {

        @Throws(CertificateException::class)
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
            /* No exception -> Client is trusted. */
        }

        @Throws(CertificateException::class)
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
            /* No exception -> Server is trusted. */
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()

    }

    /**
     * Sets an AUTH_CODE http header to each request so that it's clear
     * to the backend API that the request comes from this source.
     */
    private class AuthCodeHeaderInterceptor(private val authCode: String) : Interceptor {

        override fun intercept(chain: Interceptor.Chain): okhttp3.Response {

            val originalRequest = chain.request()

            val modifiedRequest = originalRequest.newBuilder()
                .header(HEADER_KEY_AUTH_CODE, authCode)
                .method(originalRequest.method(), originalRequest.body())
                .build()

            return chain.proceed(modifiedRequest)
        }

        companion object {
            const val HEADER_KEY_AUTH_CODE = "AUTH_CODE"
        }
    }
}
