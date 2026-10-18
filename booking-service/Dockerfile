FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY booking-service booking-service
COPY notification-service notification-service
# Optional BuildKit secrets support corporate/cloud Maven proxies without baking settings into images.
RUN --mount=type=secret,id=maven_settings,target=/root/.m2/settings.xml \
    --mount=type=secret,id=java_truststore,target=/tmp/java-truststore \
    if [ -f /tmp/java-truststore ]; then export MAVEN_OPTS="-Djavax.net.ssl.trustStore=/tmp/java-truststore"; fi; \
    mvn -B -pl booking-service -am package -DskipTests
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/booking-service/target/booking-service-0.1.0-SNAPSHOT.jar app.jar
RUN mkdir /logs && chown 10001:10001 /logs
USER 10001
ENTRYPOINT ["java","-jar","app.jar"]
