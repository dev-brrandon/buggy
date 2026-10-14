FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /src
COPY . .
RUN ./gradlew --no-daemon clean qualityCheck :assistant-bootstrap:installDist

FROM eclipse-temurin:25-jre-alpine
RUN addgroup -S -g 10001 legit \
    && adduser -S -D -H -u 10001 -G legit legit \
    && apk add --no-cache curl
WORKDIR /app
COPY --from=build --chown=10001:10001 /src/assistant-bootstrap/build/install/legit-assistant/ /app/
USER 10001:10001
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=15s --retries=3 \
    CMD curl --fail --silent http://127.0.0.1:8080/health/live >/dev/null || exit 1
ENTRYPOINT ["/app/bin/legit-assistant"]
