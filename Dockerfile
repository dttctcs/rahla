FROM docker.io/eclipse-temurin:17
RUN useradd -m -d /rahla -s /bin/bash rahla
USER rahla
WORKDIR /rahla
COPY --chown=rahla:rahla assembly/target/assembly /rahla
ENV PATH $PATH:/rahla/bin
ENV KARAF_EXEC exec
EXPOSE 8101 1099 44444 8181 8182
CMD ["karaf", "run"]
