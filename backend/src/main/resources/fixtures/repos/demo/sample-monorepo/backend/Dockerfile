FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY backend/pom.xml .
RUN mvn dependency:go-offline -B

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
EXPOSE 8080
