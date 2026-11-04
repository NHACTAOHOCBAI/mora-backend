# Build stage
FROM eclipse-temurin:26-jdk AS build
WORKDIR /app

# Sao chép các file cấu hình maven
COPY .mvn/ .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw
RUN ./mvnw dependency:go-offline

# Sao chép source code và build
COPY src ./src
RUN ./mvnw clean package -DskipTests

# Run stage
FROM eclipse-temurin:26-jre
WORKDIR /app
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
COPY --from=build /app/target/mora-backend-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
  CMD curl --fail --silent http://127.0.0.1:8080/actuator/health/liveness || exit 1

ENTRYPOINT ["java", "-Xmx380m", "-Xss512k", "-XX:+UseSerialGC", "-XX:+UseContainerSupport", "-jar", "app.jar"]
