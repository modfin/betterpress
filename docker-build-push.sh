#!/bin/bash

VERSION=$(date +%Y-%m-%dT%H.%M.%S)-$(git log -1 --pretty=format:"%h")
NAME=betterpress
IMAGE_NAME=modfin/${NAME}

docker build -f ./Dockerfile \
    -t ${IMAGE_NAME}:latest \
    -t ${IMAGE_NAME}:${VERSION} \
    . || exit 1

## Push to dockerhub
docker push ${IMAGE_NAME}:latest || exit 1
docker push ${IMAGE_NAME}:${VERSION} || exit 1

## Clean up if not specifically prompted to keep
if [ "$1" != "keep" ]; then
    docker rmi -f ${IMAGE_NAME}:latest || exit 1
    docker rmi -f ${IMAGE_NAME}:${VERSION} || exit 1
fi
