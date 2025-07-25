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

package de.stefan_oltmann.smarthome.alexaskill

import com.amazonaws.services.lambda.runtime.LambdaLogger
import de.stefan_oltmann.smarthome.alexaskill.alexamodel.*
import de.stefan_oltmann.smarthome.alexaskill.model.DeviceCapability
import de.stefan_oltmann.smarthome.alexaskill.model.DevicePowerState
import de.stefan_oltmann.smarthome.alexaskill.network.RestApi
import de.stefan_oltmann.smarthome.alexaskill.network.RestApiClientFactory
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import java.io.InputStream
import java.io.OutputStream
import kotlin.math.roundToInt
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * The main class handling all the logic.
 *
 * Attention: This must be a class (and cannot be an object) because the AWS Lambda
 * will create an instance of it before calling the handleRequest() method.
 */
class AlexaHandler {

    /**
     * Modifies behavior to help us with unit tests.
     *
     * Should never be 'true' in production.
     */
    var unitTesting = false

    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

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

        /* This code is send to the backend for authorization. */
        val authCode = System.getenv("AUTH_CODE")

        val restApi = RestApiClientFactory.createRestApiClient(apiUrl, authCode)

        val requestJson = inputStream.bufferedReader().use { it.readText() }

        logger.log("Request: $requestJson")

        val responseJson = handleRequestJson(requestJson, restApi, logger)

        logger.log("Response: $responseJson")

        outputStream.write(responseJson.encodeToByteArray())
    }

    /**
     * This method takes in the request as a JSON string and returns the response as a JSON string.
     *
     * It delegates to handleRequestObject() which then works on the object level.
     */
    fun handleRequestJson(requestJson: String, restApi: RestApi, logger: LambdaLogger): String {

        val alexaRequest = json.decodeFromString<AlexaRequest>(requestJson)

        val alexaResponse = runBlocking { handleRequestObject(alexaRequest, restApi, logger) }

        return json.encodeToString(alexaResponse)
    }

    /**
     * Finally, on this level we only work with real objects and keep the JSON stuff out of the logic.
     */
    private suspend fun handleRequestObject(
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
    private suspend fun createDeviceDiscoveryResponse(
        restApi: RestApi,
        logger: LambdaLogger
    ): AlexaResponse {

        /* Fetch all the devices from the REST API */
        val devices = try {

            restApi.findAllDevices()

        } catch (ex: Exception) {

            logger.log("Error fetching devices: $ex")

            return createErrorResponse()
        }

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
         * as needed, and these can be reused for all devices.
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

        devices.forEach { device ->

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

    private suspend fun executePowerControllerDirectiveAndCreateResponse(
        directive: Directive,
        restApi: RestApi,
        logger: LambdaLogger
    ): AlexaResponse {

        val endpointId = directive.endpoint!!.endpointId

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
                        value = JsonPrimitive(powerState.name),
                        timeOfSample = createCurrentTimeString(),
                        uncertaintyInMilliseconds = 200
                    )
                )
            )
        )
    }

    private suspend fun executePercentageControllerDirectiveAndCreateResponse(
        directive: Directive,
        restApi: RestApi,
        logger: LambdaLogger
    ): AlexaResponse {

        val endpointId = directive.endpoint!!.endpointId

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
                        value = JsonPrimitive(percentage.toString()),
                        timeOfSample = createCurrentTimeString(),
                        uncertaintyInMilliseconds = 200
                    )
                )
            )
        )
    }

    private suspend fun executeThermostatControllerDirectiveAndCreateResponse(
        directive: Directive,
        restApi: RestApi,
        logger: LambdaLogger
    ): AlexaResponse {

        val endpointId = directive.endpoint!!.endpointId

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
                        value = Json.encodeToJsonElement(
                            TargetSetpoint.serializer(), TargetSetpoint(
                                value = targetTemperature,
                                scale = "CELSIUS"
                            )
                        ),
                        timeOfSample = createCurrentTimeString(),
                        uncertaintyInMilliseconds = 200
                    )
                )
            )
        )
    }

    /*
     * Methods to communicate with backend API
     */

    /**
     * Calls the backend API to set the power state of a specified endpoint.
     */
    private suspend fun executePowerStateCall(
        endpointId: String,
        devicePowerState: DevicePowerState,
        restApi: RestApi,
        logger: LambdaLogger
    ): Boolean {

        try {

            restApi.setDevicePowerState(endpointId, devicePowerState)

        } catch (ex: Exception) {

            logger.log("Error setting power state: $ex")

            return false
        }

        return true
    }

    /**
     * Calls the backend API to set the percentage of a specified endpoint.
     */
    private suspend fun executePercentageCall(
        endpointId: String,
        percentage: Int,
        restApi: RestApi,
        logger: LambdaLogger
    ): Boolean {

        try {

            restApi.setDevicePercentage(endpointId, percentage)

        } catch (ex: Exception) {

            logger.log("Error setting percentage: $ex")

            return false
        }

        return true
    }

    /**
     * Calls the backend API to set the target temperature of specified endpoint.
     */
    private suspend fun executeTargetTemperatureCall(
        endpointId: String,
        targetTemperature: Double,
        restApi: RestApi,
        logger: LambdaLogger
    ): Boolean {

        try {

            restApi.setDeviceTargetTemperature(endpointId, targetTemperature.roundToInt())

        } catch (ex: Exception) {

            logger.log("Error setting target temperature: $ex")

            return false
        }

        return true
    }

    /*
     * Helper methods
     */

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
     * Every message needs a unique UUID as an identifier.
     *
     * This method creates that. Expect we are in unitTesting mode.
     */
    @OptIn(ExperimentalUuidApi::class)
    private fun createMessageId() =
        if (unitTesting) UNIT_TEST_MESSAGE_ID else Uuid.random().toString()

    @OptIn(ExperimentalTime::class)
    private fun createCurrentTimeString(): String {

        if (unitTesting)
            return UNIT_TEST_TIMESTAMP

        return Clock.System.now().toString()
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
        const val UNIT_TEST_MESSAGE_ID = "MESSAGE_ID"
        const val UNIT_TEST_TIMESTAMP = "2020-01-01T13:37:00.000Z"
    }
}
