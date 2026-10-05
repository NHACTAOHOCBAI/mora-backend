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
COPY --from=build /app/target/mora-backend-0.0.1-SNAPSHOT.jar app.jar

ENTRYPOINT ["java", "-Xmx380m", "-Xss512k", "-XX:+UseSerialGC", "-XX:+UseContainerSupport", "-jar", "app.jar"]
