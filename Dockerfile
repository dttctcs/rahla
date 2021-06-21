FROM maven:3.6.3-jdk-11 as compile
RUN mkdir /build
COPY . /build
WORKDIR /build
RUN mvn  -B -f pom.xml clean package

FROM openjdk:11-jre-slim
ENV KARAF_INSTALL_PATH /
ENV KARAF_HOME $KARAF_INSTALL_PATH/rahla
ENV PATH $PATH:$KARAF_HOME/bin
COPY --from=compile /build/assembly/target/assembly $KARAF_HOME
COPY docker/docker-entrypoint.sh $KARAF_HOME/bin
RUN rm $KARAF_HOME/deploy/README
RUN apt update && apt install -y openssh-client \
  && rm -rf /var/lib/apt/lists/*
EXPOSE 8101 1099 44444 8181
ENTRYPOINT ["docker-entrypoint.sh"]
CMD ["karaf", "daemon"]