#!/bin/bash
docker-compose -f server/docker-compose.yaml -f client/docker-compose.yaml build
docker-compose -f server/docker-compose.yaml -f client/docker-compose.yaml up -d