# Build stage
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /app

COPY mvnw pom.xml .
COPY .mvn .mvn

# Download dependencies up front to leverage Docker layer caching
RUN ./mvnw -B -q dependency:go-offline

COPY src src

# Build the application without running tests
RUN ./mvnw -B -DskipTests package

# Runtime stage
FROM eclipse-temurin:21-jre
WORKDIR /app

# Use the profile-specific config by default inside the container
ENV SPRING_PROFILES_ACTIVE=dev

COPY --from=builder /app/target/MrPot-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080

ENTRYPOINT ["java","-jar","/app/app.jar"]
