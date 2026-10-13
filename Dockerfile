# ===== BUILD STAGE =====
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY target/*.jar app.jar

# ===== RUN STAGE =====
FROM eclipse-temurin:17-jdk-jammy
WORKDIR /app
ENTRYPOINT ["java","-jar","app.jar"]