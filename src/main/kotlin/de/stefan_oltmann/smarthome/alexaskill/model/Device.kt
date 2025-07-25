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

package de.stefan_oltmann.smarthome.alexaskill.model

import kotlinx.serialization.Serializable

/*
 * A device like a light switch, a dimmer or a roller shutter.
 *
 * Alexa calls these "endpoints".
 */
@Serializable
data class Device(
    val id: String,
    val name: String,
    val type: DeviceType
) {

    val category: DeviceCategory
        get() = type.deviceCategory

    val capabilities: List<DeviceCapability>
        get() = type.deviceCapabilities
}
