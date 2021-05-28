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

package de.stefan_oltmann.smarthome.alexaskill.alexamodel

data class Header(
    val namespace: String = NAMESPACE_ALEXA,
    val name: String = NAME_RESPONSE,
    val payloadVersion: String = PAYLOAD_VERSION,
    val messageId: String,
    val correlationToken: String? = null
) {

    companion object {

        const val PAYLOAD_VERSION = "3"

        const val NAME_RESPONSE = "Response"

        const val NAMESPACE_ALEXA = "Alexa"

        const val NAMESPACE_AUTHORIZATION = "Alexa.Authorization"

        const val NAMESPACE_DISCOVERY = "Alexa.Discovery"

        const val NAMESPACE_POWER_CONTROLLER = "Alexa.PowerController"
        const val NAMESPACE_PERCENTAGE_CONTROLLER = "Alexa.PercentageController"
        const val NAMESPACE_THERMOSTAT_CONTROLLER = "Alexa.ThermostatController"
    }
}
