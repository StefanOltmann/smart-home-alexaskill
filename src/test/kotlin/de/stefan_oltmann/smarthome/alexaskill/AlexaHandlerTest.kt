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
import com.google.gson.Gson
import de.stefan_oltmann.smarthome.alexaskill.alexamodel.AlexaRequest
import de.stefan_oltmann.smarthome.alexaskill.model.Device
import de.stefan_oltmann.smarthome.alexaskill.model.DevicePowerState
import de.stefan_oltmann.smarthome.alexaskill.model.DeviceType
import de.stefan_oltmann.smarthome.alexaskill.network.RestApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import retrofit2.Call
import retrofit2.Response

class AlexaHandlerTest {

    /**
     * The handler in unit testing mode returns static messageIds and timestamps.
     *
     * This way we can easily just compare JSON input and output.
     */
    private val handler = AlexaHandler().apply { unitTesting = true }

    /**
     * A namespace that is not handled by this skill should result in an proper error response.
     */
    @Test
    fun testHandleInvalidRequest() {

        val requestJson = """
            {
              "directive": {
                "header": {
                  "namespace": "Alexa.NamespaceThatDoesNotExist",
                  "name": "WhoKnows",
                  "payloadVersion": "3",
                  "messageId": "<message id>"
                },
                "payload": {}
              }
            }
        """.trimIndent()

        val restApiMock = mock<RestApi> {}

        val logger = mock<LambdaLogger> {}

        val actualResponseJson = handler.handleRequestJson(requestJson, restApiMock, logger)

        val expectedResponseJson = """
            {
              "event": {
                "header": {
                  "namespace": "Alexa",
                  "name": "ErrorResponse",
                  "payloadVersion": "3",
                  "messageId": "MESSAGE_ID"
                },
                "payload": {
                  "type": "INVALID_DIRECTIVE",
                  "message": "Request is invalid."
                }
              }
            }
        """.trimIndent()

        assertEquals(expectedResponseJson, actualResponseJson)

        verifyNoInteractions(restApiMock, logger)
    }

    /**
     * We always accept authorization requests.
     *
     * See https://developer.amazon.com/en-US/docs/alexa/device-apis/alexa-authorization.html
     */
    @Test
    fun testHandleAuthorizationRequest() {

        val requestJson = """
            {
              "directive": {
                "header": {
                  "namespace": "Alexa.Authorization",
                  "name": "AcceptGrant",
                  "messageId": "<message id>",
                  "payloadVersion": "3"
                },
                "payload": {
                  "grant": {
                    "type": "OAuth2.AuthorizationCode",
                    "code": "<auth code>"
                  },
                  "grantee": {
                    "type": "BearerToken",
                    "token": "access-token-from-skill"
                  }
                }
              }
            }
        """.trimIndent()

        val restApiMock = mock<RestApi> {}

        val logger = mock<LambdaLogger> {}

        val actualResponseJson = handler.handleRequestJson(requestJson, restApiMock, logger)

        val expectedResponseJson = """
            {
              "event": {
                "header": {
                  "namespace": "Alexa.Authorization",
                  "name": "AcceptGrant.Response",
                  "payloadVersion": "3",
                  "messageId": "MESSAGE_ID"
                },
                "payload": {}
              }
            }
        """.trimIndent()

        assertEquals(expectedResponseJson, actualResponseJson)

        verifyNoInteractions(restApiMock, logger)
    }

    /**
     * This checks the device discovery routine.
     */
    @Test
    fun testHandleDiscoveryRequest() {

        val requestJson = """
            {
              "directive": {
                "header": {
                  "namespace": "Alexa.Discovery",
                  "name": "Discover",
                  "payloadVersion": "3",
                  "messageId": "<message id>"
                },
                "payload": {
                  "scope": {
                    "type": "BearerToken",
                    "token": "access-token-from-skill"
                  }
                }
              }
            }
        """.trimIndent()

        /*
         * Return mock data if the restApi is called.
         */

        val devices = listOf(
            Device(
                "power_plug",
                "Switchable device",
                DeviceType.LIGHT_SWITCH
            ),
            Device(
                "dimmer",
                "Dimmer",
                DeviceType.DIMMER
            ),
            Device(
                "roller_shutter",
                "Roller shutter",
                DeviceType.ROLLER_SHUTTER
            )
        )

        val callMock = mock<Call<List<Device>>> {
            on { execute() } doReturn Response.success(devices)
        }

        val restApiMock = mock<RestApi> {
            on { findAllDevices() } doReturn callMock
        }

        val logger = mock<LambdaLogger> {}

        /*
         * Check response
         */

        val actualResponseJson = handler.handleRequestJson(requestJson, restApiMock, logger)

        val expectedResponseJson = """
            {
              "event": {
                "header": {
                  "namespace": "Alexa.Discovery",
                  "name": "Discover.Response",
                  "payloadVersion": "3",
                  "messageId": "MESSAGE_ID"
                },
                "payload": {
                  "endpoints": [
                    {
                      "endpointId": "power_plug",
                      "friendlyName": "Switchable device",
                      "description": "-",
                      "manufacturerName": "Smart Home",
                      "capabilities": [
                        {
                          "type": "AlexaInterface",
                          "interface": "Alexa",
                          "version": "3"
                        },
                        {
                          "type": "AlexaInterface",
                          "interface": "Alexa.PowerController",
                          "version": "3",
                          "properties": {
                            "supported": [
                              {
                                "name": "powerState"
                              }
                            ]
                          }
                        }
                      ],
                      "displayCategories": [
                        "LIGHT"
                      ]
                    },
                    {
                      "endpointId": "dimmer",
                      "friendlyName": "Dimmer",
                      "description": "-",
                      "manufacturerName": "Smart Home",
                      "capabilities": [
                        {
                          "type": "AlexaInterface",
                          "interface": "Alexa",
                          "version": "3"
                        },
                        {
                          "type": "AlexaInterface",
                          "interface": "Alexa.PowerController",
                          "version": "3",
                          "properties": {
                            "supported": [
                              {
                                "name": "powerState"
                              }
                            ]
                          }
                        },
                        {
                          "type": "AlexaInterface",
                          "interface": "Alexa",
                          "version": "3"
                        },
                        {
                          "type": "AlexaInterface",
                          "interface": "Alexa.PercentageController",
                          "version": "3",
                          "properties": {
                            "supported": [
                              {
                                "name": "percentage"
                              }
                            ]
                          }
                        }
                      ],
                      "displayCategories": [
                        "LIGHT"
                      ]
                    },
                    {
                      "endpointId": "roller_shutter",
                      "friendlyName": "Roller shutter",
                      "description": "-",
                      "manufacturerName": "Smart Home",
                      "capabilities": [
                        {
                          "type": "AlexaInterface",
                          "interface": "Alexa",
                          "version": "3"
                        },
                        {
                          "type": "AlexaInterface",
                          "interface": "Alexa.PowerController",
                          "version": "3",
                          "properties": {
                            "supported": [
                              {
                                "name": "powerState"
                              }
                            ]
                          }
                        },
                        {
                          "type": "AlexaInterface",
                          "interface": "Alexa",
                          "version": "3"
                        },
                        {
                          "type": "AlexaInterface",
                          "interface": "Alexa.PercentageController",
                          "version": "3",
                          "properties": {
                            "supported": [
                              {
                                "name": "percentage"
                              }
                            ]
                          }
                        }
                      ],
                      "displayCategories": [
                        "EXTERIOR_BLIND"
                      ]
                    }
                  ]
                }
              }
            }
        """.trimIndent()

        assertEquals(expectedResponseJson, actualResponseJson)

        /*
         * Verify mocks
         */

        verify(restApiMock, times(1)).findAllDevices()
        verifyNoMoreInteractions(restApiMock, logger)
    }

    /**
     * This turns a light on.
     */
    @Test
    fun testHandlePowerControllerRequest() {

        val requestJson = """
            {
              "directive": {
                "header": {
                  "namespace": "Alexa.PowerController",
                  "name": "TurnOn",
                  "payloadVersion": "3",
                  "messageId": "34074dd3-d1f2-4925-a338-ccf912bb0335",
                  "correlationToken": "SampleValueOfCorrelationToken"
                },
                "endpoint": {
                  "scope": {
                    "type": "BearerToken",
                    "token": "SampleValueOfBearerToken"
                  },
                  "endpointId": "my_light_switch",
                  "cookie": {}
                },
                "payload": {}
              }
            }
        """.trimIndent()

        /*
         * Return mock data if the restApi is called.
         */

        val callMock = mock<Call<Unit>> {
            on { execute() } doReturn Response.success(null)
        }

        val restApiMock = mock<RestApi> {
            on { setDevicePowerState("my_light_switch", DevicePowerState.ON) } doReturn callMock
        }

        val logger = mock<LambdaLogger> {}

        /*
         * Check response
         */

        val actualResponseJson = handler.handleRequestJson(requestJson, restApiMock, logger)

        val expectedResponseJson = """
            {
              "context": {
                "properties": [
                  {
                    "namespace": "Alexa.PowerController",
                    "name": "powerState",
                    "timeOfSample": "2020-01-01T13:37:00.000Z",
                    "uncertaintyInMilliseconds": 200,
                    "value": "ON"
                  }
                ]
              },
              "event": {
                "header": {
                  "namespace": "Alexa",
                  "name": "Response",
                  "payloadVersion": "3",
                  "messageId": "MESSAGE_ID",
                  "correlationToken": "SampleValueOfCorrelationToken"
                },
                "endpoint": {
                  "endpointId": "my_light_switch",
                  "scope": {
                    "type": "BearerToken",
                    "token": "SampleValueOfBearerToken"
                  }
                }
              }
            }
        """.trimIndent()

        assertEquals(expectedResponseJson, actualResponseJson)

        /*
         * Verify mocks
         */

        verify(restApiMock, times(1)).setDevicePowerState("my_light_switch", DevicePowerState.ON)
        verifyNoMoreInteractions(restApiMock, logger)
    }

    /**
     * Dim a light.
     */
    @Test
    fun testHandlePercentageControllerRequest() {

        val requestJson = """
            {
              "directive": {
                "header": {
                  "namespace": "Alexa.PercentageController",
                  "name": "SetPercentage",
                  "payloadVersion": "3",
                  "messageId": "a5da5a6b-195b-4f40-931a-df94e257d8d5",
                  "correlationToken": "SampleValueOfCorrelationToken"
                },
                "endpoint": {
                  "scope": {
                    "type": "BearerToken",
                    "token": "SampleValueOfBearerToken"
                  },
                  "endpointId": "my_dimmer",
                  "cookie": {}
                },
                "payload": {
                  "percentage": 66
                }
              }
            }
        """.trimIndent()

        /*
         * Return mock data if the restApi is called.
         */

        val callMock = mock<Call<Unit>> {
            on { execute() } doReturn Response.success(null)
        }

        val restApiMock = mock<RestApi> {
            on { setDevicePercentage("my_dimmer", 66) } doReturn callMock
        }

        val logger = mock<LambdaLogger> {}

        /*
         * Check response
         */

        val actualResponseJson = handler.handleRequestJson(requestJson, restApiMock, logger)

        val expectedResponseJson = """
            {
              "context": {
                "properties": [
                  {
                    "namespace": "Alexa.PercentageController",
                    "name": "percentage",
                    "timeOfSample": "2020-01-01T13:37:00.000Z",
                    "uncertaintyInMilliseconds": 200,
                    "value": "66"
                  }
                ]
              },
              "event": {
                "header": {
                  "namespace": "Alexa",
                  "name": "Response",
                  "payloadVersion": "3",
                  "messageId": "MESSAGE_ID",
                  "correlationToken": "SampleValueOfCorrelationToken"
                },
                "endpoint": {
                  "endpointId": "my_dimmer",
                  "scope": {
                    "type": "BearerToken",
                    "token": "SampleValueOfBearerToken"
                  }
                }
              }
            }
        """.trimIndent()

        assertEquals(expectedResponseJson, actualResponseJson)

        /*
         * Verify mocks
         */

        verify(restApiMock, times(1)).setDevicePercentage("my_dimmer", 66)
        verifyNoMoreInteractions(restApiMock, logger)
    }

    /**
     * Set a target temperature.
     */
    @Test
    fun testHandleThermostatControllerRequest() {

        val requestJson = """
            {
              "directive": {
                "header": {
                  "namespace": "Alexa.ThermostatController",
                  "name": "SetTargetTemperature",
                  "payloadVersion": "3",
                  "messageId": "a5da5a6b-195b-4f40-931a-df94e257d8d5",
                  "correlationToken": "SampleValueOfCorrelationToken"
                },
                "endpoint": {
                  "scope": {
                    "type": "BearerToken",
                    "token": "SampleValueOfBearerToken"
                  },
                  "endpointId": "my_heating",
                  "cookie": {}
                },
                "payload": {
                  "targetSetpoint": {
                    "value": 25,
                    "scale": "CELSIUS"
                  }
                }
              }
            }
        """.trimIndent()

        /*
         * Return mock data if the restApi is called.
         */

        val callMock = mock<Call<Unit>> {
            on { execute() } doReturn Response.success(null)
        }

        val restApiMock = mock<RestApi> {
            on { setDeviceTargetTemperature("my_heating", 25) } doReturn callMock
        }

        val logger = mock<LambdaLogger> {}

        /*
         * Check response
         */

        val actualResponseJson = handler.handleRequestJson(requestJson, restApiMock, logger)

        val expectedResponseJson = """
            {
              "context": {
                "properties": [
                  {
                    "namespace": "Alexa.ThermostatController",
                    "name": "targetSetpoint",
                    "timeOfSample": "2020-01-01T13:37:00.000Z",
                    "uncertaintyInMilliseconds": 200,
                    "value": {
                      "value": 25.0,
                      "scale": "CELSIUS"
                    }
                  }
                ]
              },
              "event": {
                "header": {
                  "namespace": "Alexa",
                  "name": "Response",
                  "payloadVersion": "3",
                  "messageId": "MESSAGE_ID",
                  "correlationToken": "SampleValueOfCorrelationToken"
                },
                "endpoint": {
                  "endpointId": "my_heating",
                  "scope": {
                    "type": "BearerToken",
                    "token": "SampleValueOfBearerToken"
                  }
                }
              }
            }
        """.trimIndent()

        assertEquals(expectedResponseJson, actualResponseJson)

        /*
         * Verify mocks
         */

        verify(restApiMock, times(1)).setDeviceTargetTemperature("my_heating", 25)
        verifyNoMoreInteractions(restApiMock, logger)
    }

    /*
     * Example test to verify GSON is working
     */
    @Test
    fun testParseAlexaRequestObectFromDiscoveryRequest() {

        val requestJson = """
            {
              "directive": {
                "header": {
                  "namespace": "Alexa.Discovery",
                  "name": "Discover",
                  "payloadVersion": "3",
                  "messageId": "<message id>"
                },
                "payload": {
                  "scope": {
                    "type": "BearerToken",
                    "token": "access-token-from-skill"
                  }
                }
              }
            }
        """.trimIndent()

        val alexaRequest = Gson().fromJson(requestJson, AlexaRequest::class.java)

        assertNotNull(alexaRequest, "Parsing into object failed.")

        val directive = alexaRequest.directive

        assertNotNull(directive)

        // check header
        assertNotNull(directive.header)
        assertEquals("Alexa.Discovery", directive.header.namespace)
        assertEquals("Discover", directive.header.name)
        assertEquals("3", directive.header.payloadVersion)
        assertEquals("<message id>", directive.header.messageId)

        // check endpoint - should not exist
        assertNull(directive.endpoint)

        // check payload
        assertNotNull(directive.payload)
        assertNotNull(directive.payload.scope)
        assertEquals("BearerToken", directive.payload.scope!!.type)
        assertEquals("access-token-from-skill", directive.payload.scope!!.token)
    }
}
