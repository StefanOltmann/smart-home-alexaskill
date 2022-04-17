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

package de.stefan_oltmann.smarthome.alexaskill.model

/**
 * This is useful to have a nice icon in the Alexa App and nothing else.
 *
 * This is Alexa specific and determined from the {@link DeviceType}.
 */
enum class DeviceCategory {

    /** Lights */
    LIGHT,

    /** Roller shutter */
    EXTERIOR_BLIND,

    THERMOSTAT,

    OTHER

}
