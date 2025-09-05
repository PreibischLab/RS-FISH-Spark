#!/bin/bash

# Run this using `./build-docker-image.sh docker|podman --load|--push`

BUILD_CONTAINER_TOOL=$1
shift
GIT_HASH=f5cad2d

echo "Build RS-FISH:${GIT_HASH} container using ${BUILD_CONTAINER_TOOL}"

if [[ "${BUILD_CONTAINER_TOOL}" == "podman" ]] ; then
  podman build \
       --platform linux/arm64,linux/amd64 \
       --tag ghcr.io/janeliascicomp/rs-fish-spark:${GIT_HASH} \
       --build-arg RS_FISH_SPARK_GIT_HASH=${GIT_HASH} \
       . \
       $*
else
  docker buildx build \
       --platform linux/arm64,linux/amd64 \
       --tag ghcr.io/janeliascicomp/rs-fish-spark:${GIT_HASH} \
       --build-arg RS_FISH_SPARK_GIT_HASH=${GIT_HASH} \
       . \
       $*
fi
