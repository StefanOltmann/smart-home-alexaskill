/*
 * Stefans Smart Home Project
 * Copyright (C) 2025 Stefan Oltmann
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

import de.jensklingenberg.ktorfit.Ktorfit
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager

object RestApiClientFactory {

    fun createRestApiClient(
        baseUrl: String,
        authCode: String
    ): RestApi {

        val ktorClient = HttpClient(CIO) {

            install(ContentNegotiation) {
                json(Json)
            }

            engine {
                https.trustManager = TrustAllX509TrustManager
            }

            defaultRequest {
                header("AUTH_CODE", authCode)
            }
        }

        val ktorfit = Ktorfit.Builder()
            .baseUrl(baseUrl)
            .httpClient(ktorClient)
            .build()

        return ktorfit.createRestApi()
    }

    /*
     * Empty trust manager that does not validate certificate chains.
     * Because we use a self-signed certificate, it never has a valid chain.
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
}
