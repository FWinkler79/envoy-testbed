#!/bin/bash
mvn clean package -f client/application/pom.xml -Dmaven.test.skip=true &
mvn clean package -f server/application/pom.xml -Dmaven.test.skip=true &