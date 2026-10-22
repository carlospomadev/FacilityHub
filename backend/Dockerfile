FROM maven:3.9.16-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/target/facilityhub-backend-0.5.0-SNAPSHOT.jar app.jar
RUN mkdir -p /app/storage/documents
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
