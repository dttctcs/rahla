FROM docker.io/eclipse-temurin:21-jre
RUN adduser -D -h /rahla -s /bin/ash -u 101 rahla
#RUN apt update && apt upgrade -y && apt clean
USER rahla
WORKDIR /rahla
COPY --chown=rahla:rahla assembly/target/assembly /rahla
ENV PATH=$PATH:/rahla/bin
ENV KARAF_EXEC=exec
ENV KARAF_SYSTEM_OPTS="-javaagent:./lib/jmx_prometheus_javaagent-1.0.1.jar=9001:etc/config.yaml -javaagent:./lib/opentelemetry-javaagent-2.12.0.jar"
ENV OTEL_LOGS_EXPORTER=none
ENV OTEL_METRICS_EXPORTER=none
ENV OTEL_TRACES_EXPORTER=none
EXPOSE 8101 1099 44444 8181 8182
RUN sed -i -e '/ rahla-logging.*/d' -i -e '/ framework.*/d'  /rahla/etc/org.apache.karaf.features.cfg # temporary workaround
CMD ["karaf", "run"]
