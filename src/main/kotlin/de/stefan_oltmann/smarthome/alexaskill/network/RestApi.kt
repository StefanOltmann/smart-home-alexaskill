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

import de.stefan_oltmann.smarthome.alexaskill.model.Device
import de.stefan_oltmann.smarthome.alexaskill.model.DevicePowerState
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Path

interface RestApi {

    /**
     * Returns all devices for device discovery.
     */
    @GET("/devices")
    fun findAllDevices(): Call<List<Device>>

    /**
     * Turns a device (for e.g. a light) on and off.
     */
    @GET("/device/{deviceId}/set/power-state/value/{powerState}")
    fun setDevicePowerState(
        @Path("deviceId") deviceId: String,
        @Path("powerState") powerState: DevicePowerState
    ): Call<Unit>

    /**
     * Sets a percentage value to a device. For example a dimmer or a roller shutter.
     */
    @GET("/device/{deviceId}/set/percentage/value/{percentage}")
    fun setDevicePercentage(
        @Path("deviceId") deviceId: String,
        @Path("percentage") percentage: Int
    ): Call<Unit>

    /**
     * Sets a target temperature value to a device. For example a heating.
     */
    @GET("/device/{deviceId}/set/target-temperature/value/{targetTemperature}")
    fun setDeviceTargetTemperature(
        @Path("deviceId") deviceId: String,
        @Path("targetTemperature") targetTemperature: Int
    ): Call<Unit>

}
