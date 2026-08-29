# Multi-stage build — igual que los demás servicios del proyecto
FROM maven:3.9.6-eclipse-temurin-17 AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -q
COPY src ./src
RUN mvn package -DskipTests -q

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
# Picks up base-OS CVE patches (e.g. openssl) ahead of the next eclipse-temurin tag refresh
RUN apk upgrade --no-cache && addgroup -S spring && adduser -S spring -G spring
USER spring
EXPOSE 8086
ENTRYPOINT ["java","-jar","app.jar"]
