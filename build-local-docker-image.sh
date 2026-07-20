#!/bin/bash

what=$1
shift

GIT_HASH=bcbe2c7

echo "Build RS-FISH:${GIT_HASH}"

IMAGE_NAME=ghcr.io/janeliascicomp/rs-fish-spark:spark4.1.2-scala2.13-java21-ubuntu24.04-zarrv3-omedev-${GIT_HASH}

case $what in
  --docker-build)
  docker buildx build \
        --platform linux/amd64,linux/arm64 \
        -t $IMAGE_NAME \
        -f Dockerfile \
        $*
  ;;
  --podman-build)
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
  --podman-build-and-push)
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
  --podman-push)
  echo "Push ${IMAGE_NAME} images"
  podman manifest push ${IMAGE_NAME}
  ;;
esac
