FROM maven:3.9.9-eclipse-temurin-21-alpine AS build
WORKDIR /build
COPY . .
RUN --mount=type=cache,target=/root/.m2/repository,sharing=locked \
    --mount=type=secret,id=maven_settings,target=/root/.m2/settings.xml \
    --mount=type=secret,id=java_truststore,target=/tmp/java-truststore \
    if [ -f /tmp/java-truststore ]; then export MAVEN_OPTS="-Djavax.net.ssl.trustStore=/tmp/java-truststore"; fi; \
    mvn -B -DskipTests package
FROM eclipse-temurin:21-jre-alpine
ARG SERVICE
WORKDIR /app
RUN addgroup -g 10001 ecal && adduser -D -u 10001 -G ecal ecal
COPY --from=build --chown=ecal:ecal --chmod=444 /build/${SERVICE}/target/${SERVICE}-0.3.0-SNAPSHOT-exec.jar app.jar
USER ecal
ENTRYPOINT ["java","-XX:MaxRAMPercentage=70","-jar","app.jar"]
