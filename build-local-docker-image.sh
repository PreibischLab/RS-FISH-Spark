#!/bin/bash

what=$1
shift

GIT_HASH=e81ca9e

echo "Build RS-FISH:${GIT_HASH}"

IMAGE_NAME=ghcr.io/janeliascicomp/rs-fish-spark:omedev-${GIT_HASH}

case $what in
  --build)
  # remove existing image
  podman manifest rm ${IMAGE_NAME} -i
  podman image rm ${IMAGE_NAME} -f
  podman image prune -f
  echo "Create ${IMAGE_NAME} image"
  podman build  \
        --platform linux/amd64,linux/arm64 \
        --manifest ${IMAGE_NAME} \
        -f Dockerfile \
        $*
  ;;
  --build-and-push)
  # remove existing image
  podman manifest rm ${IMAGE_NAME} -i
  podman image rm ${IMAGE_NAME} -f
  podman image prune -f
  echo "Create ${IMAGE_NAME} image"
  podman build  \
        --platform linux/amd64,linux/arm64 \
        --manifest ${IMAGE_NAME} \
        -f Dockerfile \
        $*
  echo "Push ${IMAGE_NAME} images"
  podman manifest push ${IMAGE_NAME}
  ;;
  --push)
  echo "Push ${IMAGE_NAME} images"
  podman manifest push ${IMAGE_NAME}
  ;;
esac
