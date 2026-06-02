ARG SPARK_VERSION=3.3.2-scala2.12-java17-ubuntu24.04
ARG RS_FISH_BRANCH=ome
ARG RS_FISH_SPARK_GIT_HASH=e81ca9e

FROM ghcr.io/janeliascicomp/spark:${SPARK_VERSION}
ARG RS_FISH_SPARK_GIT_HASH

LABEL \
    org.opencontainers.image.title="RS-FISH Spark" \
    org.opencontainers.image.description="Spark version of RS-FISH" \
    org.opencontainers.image.authors="rokickik@janelia.hhmi.org,preibischs@janelia.hhmi.org,goinac@janelia.hhmi.org" \
    org.opencontainers.image.licenses="GPL-2.0" \
    org.opencontainers.image.version=${RS_FISH_SPARK_GIT_HASH}

USER root

ENV HADOOP_HOME=/opt/spark

RUN apt update -y; \
    apt-get install -y \
        libblosc1 libblosc-dev \
        libzstd1 libzstd-dev libhdf5-dev;

WORKDIR /app
COPY LICENSE.txt /app/LICENSE.txt
COPY target/RS-FISH-Spark-0.0.3-SNAPSHOT-with-dependencies.jar /app/app.jar
RUN echo "${RS_FISH_BRANCH}:${RS_FISH_SPARK_GIT_HASH}" > /app/VERSION

