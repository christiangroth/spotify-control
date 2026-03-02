#!/bin/sh

set -a
. ./.env
set +a

./gradlew :application-quarkus:quarkusDev -Dquarkus.profile=prod
