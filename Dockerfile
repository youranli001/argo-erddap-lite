# Build stage: compile and package the fat jar with Maven.
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml .
# Pre-fetch dependencies so subsequent rebuilds reuse the layer cache.
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q -DskipTests package

# Runtime stage: only the JRE + the shaded jar.
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=build /workspace/target/argo-erddap-lite.jar /app/argo-erddap-lite.jar

# Bake demo NetCDF files into the image for hosted deployment.
COPY data /data

ENV ARGO_DATA_DIR=/data
ENV PORT=7000
EXPOSE 7000

ENTRYPOINT ["java", "-jar", "/app/argo-erddap-lite.jar"]