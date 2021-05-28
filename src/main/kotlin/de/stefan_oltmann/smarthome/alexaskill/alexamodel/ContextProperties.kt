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

data class ContextProperties(
    val namespace: String,
    val name: String,
    val timeOfSample: String,
    val uncertaintyInMilliseconds: Int = UNCERTAINTY_IN_MS,
    val value: Any
) {

    companion object {

        const val UNCERTAINTY_IN_MS = 200

        const val DATE_FORMAT_PATTERN = "yyyy-MM-dd'T'HH:mm:ss.sss'Z'"
        const val DATE_FORMAT_TIMEZONE = "UTC"
    }
}
