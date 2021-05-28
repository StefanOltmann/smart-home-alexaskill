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

import com.google.gson.annotations.SerializedName

/**
 * A capability is something a device can do like be turned off and on (power control)
 * as well as set to a specific value (percentage control) which applies to dimmers and roller shutters.
 */
data class Capability(
    val type: String = "AlexaInterface",
    @SerializedName("interface")
    val interfaceName: String,
    val version: String = Header.PAYLOAD_VERSION,
    val properties: CapabilityProperties? = null
) {

    companion object {

        fun create(
            interfaceName: String,
            supportedName: String
        ): Capability {

            return Capability(
                interfaceName = interfaceName,
                properties = CapabilityProperties(
                    supported = listOf(Supported(name = supportedName))
                )
            )
        }
    }
}
