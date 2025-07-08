#!/bin/bash

# Run this using `./build-docker-image.sh --load|--push`

GIT_HASH=a06db09
docker buildx build \
       --platform linux/arm64,linux/amd64 \
       --tag ghcr.io/janeliascicomp/rs-fish-spark:${GIT_HASH} \
       --build-arg RS_FISH_SPARK_GIT_HASH=${GIT_HASH} \
       . $*
