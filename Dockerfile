# syntax=docker/dockerfile:1
FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /build
COPY backend/.mvn .mvn
COPY backend/mvnw backend/pom.xml ./
COPY docker/ProxySettings.java docker/Healthcheck.java ./
RUN javac Healthcheck.java
COPY docker/maven-build.sh /usr/local/bin/maven-build
RUN chmod +x /usr/local/bin/maven-build
# Optional session CA and settings support a managed build proxy; ordinary Docker needs neither.
RUN --mount=type=cache,target=/root/.m2 \
    --mount=type=secret,id=proxy_ca \
    --mount=type=secret,id=maven_settings \
    maven-build dependency:go-offline
COPY backend/src src
RUN --mount=type=cache,target=/root/.m2 \
    --mount=type=secret,id=proxy_ca \
    --mount=type=secret,id=maven_settings \
    rm -f src/main/resources/application-dev.properties && maven-build package -DskipTests

FROM eclipse-temurin:17-jre-jammy AS runtime
RUN groupadd --system finance && useradd --system --gid finance --home-dir /app finance
WORKDIR /app
ENV SPRING_PROFILES_ACTIVE=docker
COPY --from=build --chown=finance:finance /build/target/personal-finance-0.0.1-SNAPSHOT.jar app.jar
COPY --from=build --chown=finance:finance /build/Healthcheck.class ./
COPY --chown=finance:finance docker/start-backend.sh ./
USER finance
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=10s --start-period=60s --retries=6 \
  CMD ["java", "-cp", "/app", "Healthcheck"]
ENTRYPOINT ["sh", "/app/start-backend.sh"]
