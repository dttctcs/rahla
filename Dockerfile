FROM ghcr.io/linuxserver/baseimage-debian:trixie

RUN apt update \
    && apt install -y wget apt-transport-https gpg ca-certificates procps \
    && wget -qO - https://packages.adoptium.net/artifactory/api/gpg/key/public | gpg --dearmor | tee /etc/apt/trusted.gpg.d/adoptium.gpg > /dev/null \
    && echo "deb https://packages.adoptium.net/artifactory/deb $(awk -F= '/^VERSION_CODENAME/{print$2}' /etc/os-release) main" | tee /etc/apt/sources.list.d/adoptium.list \
    && apt update \
    && DEBIAN_FRONTEND=noninteractive apt install -y temurin-21-jre \
    && apt upgrade -y \
    && apt clean \
    && mkdir -p /rahla \
    && chown 911:911 /rahla
WORKDIR /rahla/
COPY --chown=911:911 assembly/target/assembly /rahla
COPY root /
RUN sed -i -e '/ rahla-logging.*/d' -i -e '/ framework.*/d'  /rahla/etc/org.apache.karaf.features.cfg # temporary workaround

ENV PATH=$PATH:/rahla/bin
ENV KARAF_EXEC=exec
ENV KARAF_SYSTEM_OPTS="-javaagent:./lib/jmx_prometheus_javaagent-1.0.1.jar=9001:etc/config.yaml -javaagent:./lib/opentelemetry-javaagent-2.22.0.jar"
ENV OTEL_LOGS_EXPORTER=none
ENV OTEL_METRICS_EXPORTER=none
ENV OTEL_TRACES_EXPORTER=none

EXPOSE 8101 1099 44444 8181 8182


