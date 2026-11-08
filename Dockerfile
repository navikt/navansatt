FROM europe-north1-docker.pkg.dev/cgr-nav/pull-through/nav.no/jre:openjdk-25@sha256:c303248322530595c12fe14a7624830b3d3eb8b63550bc0a7128ad1b3daed0eb
ENV TZ="Europe/Oslo"
COPY target/navansatt-1-SNAPSHOT-jar-with-dependencies.jar /app/app.jar
CMD ["-jar", "/app/app.jar"]
