# FROM navikt/java:14
FROM eclipse-temurin:17-jre

RUN apt-get update && apt-get install -y \
  curl \
  dumb-init \
  && rm -rf /var/lib/apt/lists/*

WORKDIR /app

RUN curl -L -O https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/latest/download/opentelemetry-javaagent.jar

ENV TZ="Europe/Oslo"

ENV MAIN_CLASS=no.nav.pensjon.pen_app.PenApplication
ENV LOGGING_CONFIG=classpath:logback-nais.xml
ENV SECRET_BASEDIR=/

COPY java-opts.sh /app

COPY target/navansatt-1-SNAPSHOT-jar-with-dependencies.jar app.jar

ENTRYPOINT ["/usr/bin/dumb-init", "--"]
CMD ["bash", "-c", "source java-opts.sh && exec java ${DEFAULT_JVM_OPTS} ${JAVA_OPTS} -jar app.jar $@"]
