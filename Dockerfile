FROM europe-north1-docker.pkg.dev/cgr-nav/pull-through/nav.no/jre:openjdk-25@sha256:2bfccc2a7e00483a18003f1db6c65bfe6735c140447c27cba5701dab18b5c066
ENV TZ="Europe/Oslo"
COPY target/navansatt-1-SNAPSHOT-jar-with-dependencies.jar /app/app.jar
CMD ["-jar", "/app/app.jar"]
