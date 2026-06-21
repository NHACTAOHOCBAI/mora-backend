# Build stage
FROM eclipse-temurin:26-jdk AS build
WORKDIR /app

# Sao chép các file cấu hình maven
COPY .mvn/ .mvn
COPY mvnw pom.xml ./
RUN ./mvnw dependency:go-offline

# Sao chép source code và build
COPY src ./src
RUN ./mvnw clean package -DskipTests

# Run stage
FROM eclipse-temurin:26-jre
WORKDIR /app
COPY --from=build /app/target/mora-backend-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
