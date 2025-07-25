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

@Serializable
enum class DeviceType {

    /**
     * A simple light that can be turned off and on.
     */
    LIGHT_SWITCH,

    /**
     * A dimmer is a light that can be set to a percentage value.
     * In addition it can be turned off and on.
     */
    DIMMER,

    /**
     * A exterior blind that can be set to a percentage value.
     * Also "on" and "off" is supported for going all the way up or down.
     */
    ROLLER_SHUTTER,

    /**
     * A heating.
     */
    HEATING,

    /**
     * A fire or wind alarm for instance.
     */
    ALARM;

    val deviceCategory: DeviceCategory
        get() = when (this) {
            LIGHT_SWITCH -> DeviceCategory.LIGHT
            DIMMER -> DeviceCategory.LIGHT
            ROLLER_SHUTTER -> DeviceCategory.EXTERIOR_BLIND
            HEATING -> DeviceCategory.THERMOSTAT
            ALARM -> DeviceCategory.OTHER
        }

    val deviceCapabilities: List<DeviceCapability>
        get() = when (this) {
            LIGHT_SWITCH -> listOf(DeviceCapability.POWER_STATE)
            DIMMER -> listOf(DeviceCapability.POWER_STATE, DeviceCapability.PERCENTAGE)
            ROLLER_SHUTTER -> listOf(DeviceCapability.POWER_STATE, DeviceCapability.PERCENTAGE)
            HEATING -> listOf(DeviceCapability.THERMOSTAT)
            ALARM -> emptyList()
        }
}
