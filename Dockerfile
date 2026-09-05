FROM europe-north1-docker.pkg.dev/cgr-nav/pull-through/nav.no/jre:openjdk-25@sha256:4d023c28bc2e4c4cd8fd9850ff7e034f0183714d3483ca58cc5057f0e6f10068
ENV TZ="Europe/Oslo"
COPY target/navansatt-1-SNAPSHOT-jar-with-dependencies.jar /app/app.jar
CMD ["-jar", "/app/app.jar"]
