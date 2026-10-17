FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY booking-service booking-service
COPY notification-service notification-service
RUN mvn -B -pl notification-service -am package -DskipTests
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/notification-service/target/notification-service-0.1.0-SNAPSHOT.jar app.jar
RUN mkdir /logs && chown 10001:10001 /logs
USER 10001
ENTRYPOINT ["java","-jar","app.jar"]
