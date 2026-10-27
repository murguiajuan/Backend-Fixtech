# syntax=docker/dockerfile:1

# ---------- Etapa 1: compilación ----------
FROM eclipse-temurin:21-jdk-jammy AS build

WORKDIR /workspace

# Se copian primero los archivos de Maven para aprovechar la caché de dependencias.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw \
    && ./mvnw -B -q -DskipTests dependency:go-offline

COPY src/ src/
RUN ./mvnw -B -q -DskipTests package

# ---------- Etapa 2: ejecución ----------
FROM eclipse-temurin:21-jre-jammy

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system fixtech \
    && useradd --system --gid fixtech --home-dir /app --shell /usr/sbin/nologin fixtech

WORKDIR /app

COPY --from=build /workspace/target/*.jar /app/app.jar
RUN mkdir -p /app/uploads \
    && chown -R fixtech:fixtech /app

USER fixtech

ENV SPRING_PROFILES_ACTIVE=prod \
    SERVER_PORT=8080 \
    FIXTECH_ARCHIVOS_DIR=/app/uploads \
    JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseContainerSupport"

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=5 \
    CMD curl -fsS http://127.0.0.1:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
