# Build stage
FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /workspace/app

COPY pom.xml .
RUN mvn dependency:go-offline -q

COPY src src
RUN mvn package -DskipTests -q

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

COPY --from=build /workspace/app/target/*.jar app.jar

USER appuser

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
