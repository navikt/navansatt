FROM europe-north1-docker.pkg.dev/cgr-nav/pull-through/nav.no/jre:openjdk-25@sha256:2c799fa168bfa527884e8ae5a14ad3d46a6919d37185ca2466eead6eb1e76d1e
ENV TZ="Europe/Oslo"
COPY target/navansatt-1-SNAPSHOT-jar-with-dependencies.jar /app/app.jar
CMD ["-jar", "/app/app.jar"]
