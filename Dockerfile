# Build stage
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src ./src
RUN mvn -q package -DskipTests

# Run stage — optimized for Google Cloud Run
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S bizlink && adduser -S bizlink -G bizlink
COPY --from=build /app/target/*.jar app.jar
RUN mkdir -p /app/uploads && chown -R bizlink:bizlink /app
USER bizlink
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
