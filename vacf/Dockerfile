FROM maven:3.9-eclipse-temurin-11 AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN mvn -q package -DskipTests

FROM eclipse-temurin:11-jre
WORKDIR /app
COPY --from=build /build/target/vacf-1.0.0.jar app.jar
EXPOSE 8084 9084
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
