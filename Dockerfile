FROM docker.io/eclipse-temurin:17
RUN useradd -m -d /rahla -s /bin/bash rahla
USER rahla
WORKDIR /rahla
COPY --chown=rahla:rahla assembly/target/assembly /rahla
ENV PATH $PATH:/rahla/bin
ENV KARAF_EXEC exec
ENV KARAF_SYSTEM_OPTS -javaagent:./lib/jmx_prometheus_javaagent-1.0.0.jar=9001:etc/config.yaml -javaagent:./lib/opentelemetry-javaagent-2.4.0.jar
ENV OTEL_LOGS_EXPORTER none
ENV OTEL_METRICS_EXPORTER none
ENV OTEL_TRACES_EXPORTER none
EXPOSE 8101 1099 44444 8181 8182
RUN sed -i -e '/ rahla-logging.*/d' -i -e '/ framework.*/d'  /rahla/etc/org.apache.karaf.features.cfg # temporary workaround
CMD ["karaf", "run"]
