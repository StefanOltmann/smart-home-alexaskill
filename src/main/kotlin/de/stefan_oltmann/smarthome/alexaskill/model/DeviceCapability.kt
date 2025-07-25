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

/**
 * This represents what can be done with the device.
 *
 * This is Alexa specific and determined from the {@link DeviceType}.
 */
@Serializable
enum class DeviceCapability {

    /** Can be turned on and off */
    POWER_STATE,

    /** Can take a percent value (e.g. Dimmers or Roller shutters) */
    PERCENTAGE,

    /** Can set temperature */
    THERMOSTAT;

}
