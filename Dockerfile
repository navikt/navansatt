FROM europe-north1-docker.pkg.dev/cgr-nav/pull-through/nav.no/jre:openjdk-25@sha256:6636414bda979991bfea1b8386e1fd567f5f322a0b03ae458fb083348e2edc71
ENV TZ="Europe/Oslo"
COPY target/navansatt-1-SNAPSHOT-jar-with-dependencies.jar /app/app.jar
CMD ["-jar", "/app/app.jar"]
