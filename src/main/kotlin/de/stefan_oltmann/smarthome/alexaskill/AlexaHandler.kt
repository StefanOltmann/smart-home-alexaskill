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

package de.stefan_oltmann.smarthome.alexaskill

import com.amazonaws.services.lambda.runtime.LambdaLogger
import com.google.gson.GsonBuilder
import de.stefan_oltmann.smarthome.alexaskill.alexamodel.AlexaRequest
import de.stefan_oltmann.smarthome.alexaskill.alexamodel.AlexaResponse
import de.stefan_oltmann.smarthome.alexaskill.alexamodel.Capability
import de.stefan_oltmann.smarthome.alexaskill.alexamodel.Context
import de.stefan_oltmann.smarthome.alexaskill.alexamodel.ContextProperties
import de.stefan_oltmann.smarthome.alexaskill.alexamodel.Directive
import de.stefan_oltmann.smarthome.alexaskill.alexamodel.DiscoveryEndpoint
import de.stefan_oltmann.smarthome.alexaskill.alexamodel.Endpoint
import de.stefan_oltmann.smarthome.alexaskill.alexamodel.Event
import de.stefan_oltmann.smarthome.alexaskill.alexamodel.Header
import de.stefan_oltmann.smarthome.alexaskill.alexamodel.Payload
import de.stefan_oltmann.smarthome.alexaskill.alexamodel.Scope
import de.stefan_oltmann.smarthome.alexaskill.alexamodel.TargetSetpoint
import de.stefan_oltmann.smarthome.alexaskill.model.DeviceCapability
import de.stefan_oltmann.smarthome.alexaskill.model.DevicePowerState
import de.stefan_oltmann.smarthome.alexaskill.network.RestApi
import de.stefan_oltmann.smarthome.alexaskill.network.RestApiClientFactory
import retrofit2.Response
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Scanner
import java.util.TimeZone
import java.util.UUID
import kotlin.math.roundToInt

/**
 * The main class handling all the logic.
 *
 * Attention: This must be a class (and cannot be an object) because the AWS Lambda
 * will create an instance of it before calling the handleRequest() method.
 */
class AlexaHandler {

    /**
     * GSON instance that creates pretty JSON.
     *
     * This is good for unit tests and log statements as well.
     */
    private val gson by lazy {

        val builder = GsonBuilder()
        builder.setPrettyPrinting()
        builder.create()
    }

    /**
     * Modifies behaviour to help us with unit tests.
     *
     * Should never be 'true' in production.
     */
    var unitTesting = false

    /**
     * This method is called by the AWS Lambda.
     *
     * This method reads and outputs JSON data as bytes. To better work on this we
     * only handle his conversion here and delegate JSON strings to handleRequestJson().
     */
    @Suppress("unused")
    fun handleRequest(
        inputStream: InputStream,
        outputStream: OutputStream,
        context: com.amazonaws.services.lambda.runtime.Context
    ) {

        val logger = context.logger

        /* For e.g. "https://myserver.com:50000/" (without quotes) */
        val apiUrl = System.getenv("API_URL")

        /* Auth Code send to the backend API for authorization */
        val authCode = System.getenv("AUTH_CODE")

        val restApi = RestApiClientFactory.createRestApiClient(apiUrl, authCode)

        val requestJson = getRequestString(inputStream)

        logger.log("Request: $requestJson")

        val responseJson = handleRequestJson(requestJson, restApi, logger)

        logger.log("Response: $responseJson")

        outputStream.write(responseJson.toByteArray(StandardCharsets.UTF_8))
    }

    /**
     * This method takes in the request as JSON string and returns the response as JSON string.
     *
     * It delegates to handleRequestObject() which then works on the object level.
     */
    fun handleRequestJson(requestJson: String, restApi: RestApi, logger: LambdaLogger): String {

        val alexaRequest = gson.fromJson(requestJson, AlexaRequest::class.java)

        val alexaResponse = handleRequestObject(alexaRequest, restApi, logger)

        return gson.toJson(alexaResponse)
    }

    /**
     * Finally on this level we only work with real objects and keep the JSON stuff out of the logic.
     */
    private fun handleRequestObject(
        alexaRequest: AlexaRequest,
        restApi: RestApi,
        logger: LambdaLogger
    ): AlexaResponse {

        /* Every call to an Alexa Skill is a "Directive" like orders: "Do this, do that." */
        val directive = alexaRequest.directive

        /*
         * We can us the Namespace to decide upon the action.
         */
        return when (directive.header.namespace) {

            /**
             * This is the first call to this skill when the users wants to activate it.
             *
             * In this simple case it's always accepted.
             */
            Header.NAMESPACE_AUTHORIZATION -> createAuthorizationGrantedResponse()

            /**
             * This is the second call to this skill after successful authorization:
             * Alexa needs to find all our devices for configuration.
             *
             * This is called only on user interaction. Alexa will cache the discovery result.
             */
            Header.NAMESPACE_DISCOVERY -> createDeviceDiscoveryResponse(restApi, logger)

            /**
             * This call is made when you tell Alexa to turn something on or off.
             *
             * "Alexa, kitchen light on!" is considered a "Power Controller Directive".
             */
            Header.NAMESPACE_POWER_CONTROLLER ->
                executePowerControllerDirectiveAndCreateResponse(directive, restApi, logger)

            /**
             * This call is made when you tell Alexa to set something to a percentage value.
             *
             * "Alexa, kitchen light to 60 percent!" is considered a "Percentage Controller Directive".
             */
            Header.NAMESPACE_PERCENTAGE_CONTROLLER ->
                executePercentageControllerDirectiveAndCreateResponse(directive, restApi, logger)

            /**
             * This call is made when you tell Alexa to set something to a temperature.
             *
             * "Alexa, kitchen heating to 30 degree!" is considered a "Thermostat Controller Directive".
             */
            Header.NAMESPACE_THERMOSTAT_CONTROLLER ->
                executeThermostatControllerDirectiveAndCreateResponse(directive, restApi, logger)

            /**
             * In case this skill receives a directive it cannot handle we return an error.
             */
            else -> createErrorResponse()
        }
    }

    /**
     * Creates a response that grants authorization
     */
    private fun createAuthorizationGrantedResponse(): AlexaResponse {

        return AlexaResponse(
            event = Event(
                header = Header(
                    namespace = Header.NAMESPACE_AUTHORIZATION,
                    name = "AcceptGrant.Response",
                    messageId = createMessageId()
                ),
                payload = Payload()
            )
        )
    }

    /**
     * Calls the backend API for the devices and creates a Discovery response.
     */
    private fun createDeviceDiscoveryResponse(
        restApi: RestApi,
        logger: LambdaLogger
    ): AlexaResponse {

        /* Fetch all the devices from the REST API */
        val devicesResponse = restApi.findAllDevices().execute()

        /* Return ASAP if a problem occurred. */
        if (!devicesResponse.isSuccessful)
            return createErrorResponse()

        val devices = devicesResponse.body()

        /**
         * It's very helpful for debugging to see which devices are actually returned by the backend API.
         */
        if (!unitTesting)
            logger.log("Devices from API: $devices")

        /*
         * Creation of capabilities our devices need.
         *
         * Of course there are a lot more, but we go with the most common used: Switches & Dimmers.
         *
         * We define them here as we don't want to create more objects in memory
         * as needed and these can be reused for all devices.
         */

        val alexaCapability = Capability(
            interfaceName = Header.NAMESPACE_ALEXA
        )

        val powerControllerCapability = Capability.create(
            interfaceName = Header.NAMESPACE_POWER_CONTROLLER,
            supportedName = "powerState"
        )

        val percentageControllerCapability = Capability.create(
            interfaceName = Header.NAMESPACE_PERCENTAGE_CONTROLLER,
            supportedName = "percentage"
        )

        val thermostatControllerCapability = Capability.create(
            interfaceName = Header.NAMESPACE_THERMOSTAT_CONTROLLER,
            supportedName = "targetSetpoint"
        )

        /*
         * An "endpoint" is a device. For example the light you want to turn off and on is a "endpoint".
         */
        val endpoints = mutableListOf<DiscoveryEndpoint>()

        devices?.forEach { device ->

            /*
             * Devices can have many capabilities and you can mix them like you need it.
             */
            val capabilities = mutableListOf<Capability>()

            for (capability in device.capabilities) {

                /* We always need this base capability. */
                capabilities.add(alexaCapability)

                /* Add other capabilities based on supported functions. */
                when (capability) {
                    DeviceCapability.POWER_STATE -> capabilities.add(powerControllerCapability)
                    DeviceCapability.PERCENTAGE -> capabilities.add(percentageControllerCapability)
                    DeviceCapability.THERMOSTAT -> capabilities.add(thermostatControllerCapability)
                }
            }

            val discoveryEndpoint = DiscoveryEndpoint(
                endpointId = device.id,
                friendlyName = device.name,
                description = DEVICE_DESCRIPTION,
                manufacturerName = MANUFACTURER_NAME,
                capabilities = capabilities,
                displayCategories = listOf(device.category.name)
            )

            endpoints.add(discoveryEndpoint)
        }

        return AlexaResponse(
            event = Event(
                header = Header(
                    namespace = Header.NAMESPACE_DISCOVERY,
                    name = "Discover.Response",
                    messageId = createMessageId()
                ),
                payload = Payload(
                    endpoints = endpoints
                )
            )
        )
    }

    private fun executePowerControllerDirectiveAndCreateResponse(
        directive: Directive,
        restApi: RestApi,
        logger: LambdaLogger
    ): AlexaResponse {

        val endpointId = directive.endpoint.endpointId

        val powerState =
            if (directive.header.name == "TurnOn")
                DevicePowerState.ON else DevicePowerState.OFF

        val callWasSuccessful = executePowerStateCall(endpointId, powerState, restApi, logger)

        if (!callWasSuccessful)
            return createErrorResponse()

        return AlexaResponse(
            event = Event(
                header = Header(
                    correlationToken = directive.header.correlationToken,
                    messageId = createMessageId()
                ),
                endpoint = Endpoint(
                    endpointId = directive.endpoint.endpointId,
                    scope = Scope(token = directive.endpoint.scope.token)
                )
            ),
            context = Context(
                properties = listOf(
                    ContextProperties(
                        namespace = Header.NAMESPACE_POWER_CONTROLLER,
                        name = "powerState",
                        value = powerState.name,
                        timeOfSample = createCurrentTimeString()
                    )
                )
            )
        )
    }

    private fun executePercentageControllerDirectiveAndCreateResponse(
        directive: Directive,
        restApi: RestApi,
        logger: LambdaLogger
    ): AlexaResponse {

        val endpointId = directive.endpoint.endpointId

        val percentage = directive.payload.percentage!!

        val callWasSuccessful = executePercentageCall(endpointId, percentage, restApi, logger)

        if (!callWasSuccessful)
            return createErrorResponse()

        return AlexaResponse(
            event = Event(
                header = Header(
                    correlationToken = directive.header.correlationToken,
                    messageId = createMessageId()
                ),
                endpoint = Endpoint(
                    endpointId = directive.endpoint.endpointId,
                    scope = Scope(token = directive.endpoint.scope.token)
                )
            ),
            context = Context(
                properties = listOf(
                    ContextProperties(
                        namespace = Header.NAMESPACE_PERCENTAGE_CONTROLLER,
                        name = "percentage",
                        value = percentage.toString(),
                        timeOfSample = createCurrentTimeString()
                    )
                )
            )
        )
    }

    private fun executeThermostatControllerDirectiveAndCreateResponse(
        directive: Directive,
        restApi: RestApi,
        logger: LambdaLogger
    ): AlexaResponse {

        val endpointId = directive.endpoint.endpointId

        val targetTemperature = directive.payload.targetSetpoint!!.value

        val callWasSuccessful =
            executeTargetTemperatureCall(endpointId, targetTemperature, restApi, logger)

        if (!callWasSuccessful)
            return createErrorResponse()

        return AlexaResponse(
            event = Event(
                header = Header(
                    correlationToken = directive.header.correlationToken,
                    messageId = createMessageId()
                ),
                endpoint = Endpoint(
                    endpointId = directive.endpoint.endpointId,
                    scope = Scope(token = directive.endpoint.scope.token)
                )
            ),
            context = Context(
                properties = listOf(
                    ContextProperties(
                        namespace = Header.NAMESPACE_THERMOSTAT_CONTROLLER,
                        name = "targetSetpoint",
                        value = TargetSetpoint(
                            value = targetTemperature,
                            scale = "CELSIUS"
                        ),
                        timeOfSample = createCurrentTimeString()
                    )
                )
            )
        )
    }

    /*
     * Methods to communicate with backend API
     */

    /**
     * Calls the backend API to set the power state of specified endpoint.
     */
    private fun executePowerStateCall(
        endpointId: String,
        devicePowerState: DevicePowerState,
        restApi: RestApi,
        logger: LambdaLogger
    ): Boolean {

        val call = restApi.setDevicePowerState(endpointId, devicePowerState)

        if (!unitTesting)
            logger.log(EXECUTE_CALL_MESSAGE + call.request().url())

        val response: Response<*> = call.execute()

        if (!unitTesting)
            logger.log(CALL_RESULT_MESSAGE + response.code() + " - " + response.message())

        return response.isSuccessful
    }

    /**
     * Calls the backend API to set the percentage of specified endpoint.
     */
    private fun executePercentageCall(
        endpointId: String,
        percentage: Int,
        restApi: RestApi,
        logger: LambdaLogger
    ): Boolean {

        val call = restApi.setDevicePercentage(endpointId, percentage)

        if (!unitTesting)
            logger.log(EXECUTE_CALL_MESSAGE + call.request().url())

        val response: Response<*> = call.execute()

        if (!unitTesting)
            logger.log(CALL_RESULT_MESSAGE + response.code() + " - " + response.message())

        return response.isSuccessful
    }

    /**
     * Calls the backend API to set the target temperature of specified endpoint.
     */
    private fun executeTargetTemperatureCall(
        endpointId: String,
        targetTemperature: Double,
        restApi: RestApi,
        logger: LambdaLogger
    ): Boolean {

        val call = restApi.setDeviceTargetTemperature(endpointId, targetTemperature.roundToInt())

        if (!unitTesting)
            logger.log(EXECUTE_CALL_MESSAGE + call.request().url())

        val response: Response<*> = call.execute()

        if (!unitTesting)
            logger.log(CALL_RESULT_MESSAGE + response.code() + " - " + response.message())

        return response.isSuccessful
    }

    /*
     * Helper methods
     */

    private fun getRequestString(inputStream: InputStream): String {

        val scanner = Scanner(inputStream).useDelimiter("\\A")

        return if (scanner.hasNext()) scanner.next() else ""
    }

    private fun createErrorResponse(): AlexaResponse {

        return AlexaResponse(
            event = Event(
                header = Header(
                    name = "ErrorResponse",
                    messageId = createMessageId()
                ),
                payload = Payload(
                    type = "INVALID_DIRECTIVE",
                    message = "Request is invalid."
                )
            )
        )
    }

    /**
     * Every message needs a unique UUID as identifier.
     *
     * This method creates that. Expect we are in unitTesting mode.
     */
    private fun createMessageId() =
        if (unitTesting) UNIT_TEST_MESSAGE_ID else UUID.randomUUID().toString()

    private fun createCurrentTimeString(): String {

        val timestamp = if (unitTesting) UNIT_TEST_TIMESTAMP else Date().time

        val simpleDataFormat = SimpleDateFormat(ContextProperties.DATE_FORMAT_PATTERN)
        simpleDataFormat.timeZone = TimeZone.getTimeZone(ContextProperties.DATE_FORMAT_TIMEZONE)

        return simpleDataFormat.format(timestamp)
    }

    companion object {

        /**
         * Likely your name if these are your devices.
         * Must not be empty.
         */
        const val MANUFACTURER_NAME = "Smart Home"

        /**
         * A placeholder description for the devices.
         * Must not be empty.
         */
        const val DEVICE_DESCRIPTION = "-"

        /**
         * Constants for the Unit Test fake messages
         */
        const val EXECUTE_CALL_MESSAGE = "Execute call: "
        const val CALL_RESULT_MESSAGE = "Call result: "
        const val UNIT_TEST_MESSAGE_ID = "MESSAGE_ID"
        const val UNIT_TEST_TIMESTAMP = 1577885820000
    }
}
