# Both images are pinned by digest and kept current by Renovate (one grouped,
# automerged PR, see renovate.json). The Temurin JRE comes from the official
# eclipse-temurin image of the SAME alpine release (musl), so the alpine tag in
# both lines must move together — Renovate is told not to bump it on its own.
FROM eclipse-temurin:21.0.13_9-jre-alpine-3.24@sha256:ac05c3c42b7c2f1a52c9bd52f01041fa1958231b15ebeae7420429cbc5baf474 AS jre

FROM ghcr.io/linuxserver/baseimage-alpine:3.24@sha256:d07ad0eb9e65feb40ded2125e17ad8e1b33228b3cf480567378c8042ef0a8b2d

ENV JAVA_HOME=/opt/java/openjdk
ENV PATH=$JAVA_HOME/bin:$PATH

# Default to UTF-8 file.encoding
ENV LANG='en_US.UTF-8' LANGUAGE='en_US:en' LC_ALL='en_US.UTF-8'

# runtime deps of the Temurin JRE, same list as adoptium's 21/jre/alpine Dockerfile
RUN set -eux; \
    apk add --no-cache \
        # java.lang.UnsatisfiedLinkError: libfontmanager.so: libfreetype.so.6: cannot open shared object file: No such file or directory
        # java.lang.NoClassDefFoundError: Could not initialize class sun.awt.X11FontManager
        # https://github.com/docker-library/openjdk/pull/235#issuecomment-424466077
        fontconfig ttf-dejavu \
        # utilities for keeping Alpine and OpenJDK CA certificates in sync
        # https://github.com/adoptium/containers/issues/293
        ca-certificates p11-kit-trust \
        # locales ensures proper character encoding and locale-specific behaviors using en_US.UTF-8
        musl-locales musl-locales-lang \
        tzdata \
        # Contains `csplit` used for splitting multiple certificates in one file to multiple files, since keytool can
        # only import one at a time.
        coreutils \
        # Needed to extract CN and generate aliases for certificates
        openssl \
    ; \
    rm -rf /var/cache/apk/*

COPY --from=jre /opt/java/openjdk $JAVA_HOME

RUN set -eux; \
    echo "Verifying install ..."; \
    echo "java --version"; java --version; \
    echo "Complete."

COPY root /
COPY --chown=abc:911 assembly/target/assembly /app/rahla
RUN sed -i -e '/ rahla-logging.*/d' -i -e '/ framework.*/d'  /app/rahla/etc/org.apache.karaf.features.cfg; \
    rm -rf /app/rahla/deploy; \
    mkdir -p /config/deploy; \
    mv /app/rahla/etc /config/; \
    chown abc:911 /config/deploy; \
    ln -s /config/deploy /app/rahla/deploy; \
    ln -s /config/etc /app/rahla/etc; \
    printf '\norg.ops4j.pax.url.mvn.localRepository=/config/.m2\n' >> /config/etc/org.ops4j.pax.url.mvn.cfg; \
    mkdir -p /config/.m2; \
    chown abc:911 /config/.m2;


ENV PATH=$PATH:/app/rahla/bin
ENV KARAF_EXEC=exec
ENV KARAF_SYSTEM_OPTS="-javaagent:./lib/jmx_prometheus_javaagent-1.0.1.jar=9001:etc/config.yaml -javaagent:./lib/opentelemetry-javaagent-2.30.0.jar"
ENV OTEL_LOGS_EXPORTER=none
ENV OTEL_METRICS_EXPORTER=none
ENV OTEL_TRACES_EXPORTER=none

EXPOSE 8101 1099 44444 8181


