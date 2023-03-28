#FROM maven:3.8.4-openjdk-11 as compile
#ARG EXTRA_MAVEN
#RUN mkdir /build
#COPY . /build
#WORKDIR /build
#RUN mvn $EXTRA_MAVEN -B -f pom.xml clean package

FROM docker.io/eclipse-temurin:17
ENV KARAF_HOME /rahla
ENV PATH $PATH:$KARAF_HOME/bin
ENV KARAF_EXEC exec
ENV EXTRA_JAVA_OPTS "$EXTRA_JAVA_OPTS -Dkaraf.log.console=ALL"
#COPY --from=compile /build/assembly/target/assembly $KARAF_HOME
COPY assembly/target/assembly $KARAF_HOME
COPY docker/docker-entrypoint.sh /
#ADD https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v1.9.1/opentelemetry-javaagent.jar /
EXPOSE 8101 1099 44444 8181 8182
CMD ["karaf", "run"]
