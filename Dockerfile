# Use Java 17 as the base image
FROM gradle:7.6.3-jdk17 AS build

# Set the working directory
WORKDIR /app

# Copy the build files
COPY build.gradle.kts settings.gradle.kts gradle.properties ./
COPY gradle ./gradle

# Copy the source code
COPY src ./src

# Build the application
RUN gradle buildFatJar --no-daemon

# Runtime stage
FROM eclipse-temurin:17-jre

# Set the working directory
WORKDIR /app

# Copy the built JAR file from the build stage
COPY --from=build /app/build/libs/*.jar /app/reverse-proxy.jar

# Create directories for config and logs
RUN mkdir -p /app/config /app/logs

# Copy the Docker-specific configuration file
COPY src/main/resources/docker-application.yaml /app/config/application.yaml

# Expose the application port
EXPOSE 8080

# Set environment variables
ENV JAVA_OPTS="-Xms128m -Xmx512m"

# Run the application with the Docker-specific configuration
ENTRYPOINT ["java", "-jar", "/app/reverse-proxy.jar", "-config=/app/config/application.yaml"]
