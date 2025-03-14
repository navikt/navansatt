# === Build stage ===
FROM maven:3.8.6-openjdk-21 AS build
WORKDIR /app

COPY pom.xml .
COPY src/ ./src

RUN mvn clean package -DskipTests

# === Runtime stage ===
FROM eclipse-temurin:21-jre

RUN apt-get update && apt-get install -y \
  curl \
  dumb-init \
  && rm -rf /var/lib/apt/lists/*

WORKDIR /app

ENV TZ="Europe/Oslo"

ENV MAIN_CLASS=no.nav.pensjon.pen_app.PenApplication
ENV LOGGING_CONFIG=classpath:logback-nais.xml

COPY java-opts.sh .

COPY target/navansatt-1-SNAPSHOT-jar-with-dependencies.jar app.jar

ENTRYPOINT ["/usr/bin/dumb-init", "--"]
CMD ["bash", "-c", "source java-opts.sh && exec java ${DEFAULT_JVM_OPTS} ${JAVA_OPTS} -jar app.jar ${RUNTIME_OPTS} $@"]
