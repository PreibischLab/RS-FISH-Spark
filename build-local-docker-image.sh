#!/bin/bash

# Run this using `./build-docker-image.sh --load|--push`

docker buildx build --platform linux/arm64,linux/amd64 --tag ghcr.io/janeliascicomp/rs-fish-spark:18ca964 . $*
