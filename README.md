# Stefans Smart Home Alexa Skill

[![CI](https://github.com/StefanOltmann/smart-home-alexaskill/actions/workflows/ci.yml/badge.svg?branch=master)](https://github.com/StefanOltmann/smart-home-alexaskill/actions/workflows/ci.yml)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=smart-home-alexaskill&metric=alert_status)](https://sonarcloud.io/dashboard?id=smart-home-alexaskill)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=smart-home-alexaskill&metric=coverage)](https://sonarcloud.io/dashboard?id=smart-home-alexaskill)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)

## Prerequisites

You need a running instance of my [smart home server](https://github.com/StefanOltmann/smart-home-server)
that is public available via HTTPS.

This would look like `https://home.mydomain.com:50000/` for example.

## Packaging the application

The application can be packaged using:

```shell script
./gradlew build
```

This will create a file named `smart-home-alexaskill-XY.jar` in `build\libs`.

## Deploying the skill

Alexa Smart Home skills always run as AWS Lambda and unfortunately take quite some steps to setup.

You can
follow [these instructions](https://github.com/alexa/skill-sample-java-smarthome-switch/blob/master/instructions/README.md)
from the official Java sample for the skill and leave out step 7 as we need no database.

After you ran through these steps you should have an AWS Lambda function named `SmartHomeFunction` that can be managed
here:
[https://eu-west-1.console.aws.amazon.com/lambda/home?region=eu-west-1#/functions/SmartHomeFunction](https://eu-west-1.console.aws.amazon.com/lambda/home?region=eu-west-1#/functions/SmartHomeFunction)
.

Proceed with these steps to configure the skill:

1. Click to the `Code` tab.
2. Upload the `smart-home-alexaskill-XY.jar` in the `Code source` panel.
3. Click `Edit` on the `Runtime settings` panel.
4. Change the runtime to `Java 11 (Corretto)`.
5. Set the `Handler` in the `Runtime settings` to `de.stefan_oltmann.smarthome.alexaskill.AlexaHandler::handleRequest`.
6. Click on `Save`.
7. Click on the `Configuration` and select `Environment variables` on the left side.
9. Add key `API_URL` and set it to `https://home.mydomain.com:50000/`. Replace `home.mydomain.com` with your actual
   domain name.
10. Add key `AUTH_CODE` and set it to your security token. This is the content of _auth_code.txt_ you got by setting up
    the server.
11. Go to [https://alexa.amazon.com](https://alexa.amazon.com) and discover your devices again.

If Alexa lists all your devices as expected you can start using them.
