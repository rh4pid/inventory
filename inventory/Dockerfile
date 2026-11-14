# syntax=docker/dockerfile:1

# ---- Build ----
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw
# Download dependencies in their own layer so code-only changes rebuild fast.
RUN ./mvnw -q -B dependency:go-offline
COPY src src
RUN ./mvnw -q -B -DskipTests clean package

# ---- Run ----
FROM eclipse-temurin:25-jre
WORKDIR /app

RUN useradd --system --uid 1001 app \
 && mkdir -p /app/uploads \
 && chown -R app /app
USER app

COPY --from=build /workspace/target/inventory-0.0.1-SNAPSHOT.jar app.jar

# Hosts like Render set PORT; locally it falls back to 5001 (see application.properties).
# Keep the JVM inside the container's memory limit (the free tier has 512 MB).
# UPLOAD_DIR: point this at a mounted persistent disk in production, the container disk is wiped on redeploy.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC" \
    UPLOAD_DIR=/app/uploads

EXPOSE 5001

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
