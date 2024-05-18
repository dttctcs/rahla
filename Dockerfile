FROM docker.io/eclipse-temurin:17
RUN useradd -m -d /rahla -s /bin/bash rahla
USER rahla
WORKDIR /rahla
COPY --chown=rahla:rahla assembly/target/assembly /rahla
ENV PATH $PATH:/rahla/bin
ENV KARAF_EXEC exec
ENV KARAF_SYSTEM_OPTS -javaagent:./jmx_prometheus_javaagent-0.20.0.jar=9001:etc/config.yaml
EXPOSE 8101 1099 44444 8181 8182
CMD ["karaf", "run"]
