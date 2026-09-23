# One container serves the app and its API from the same address (Railway).
# Build: frontend → static files, backend → jar. Run: JRE + rhwp (PDF/preview) + Korean fonts.

FROM node:22-bookworm-slim AS frontend
WORKDIR /build/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

FROM eclipse-temurin:25-jdk-noble AS backend
WORKDIR /build
COPY hwplib-poc/lib/hwplib-1.1.11.jar hwplib-poc/lib/
COPY backend/gradlew backend/settings.gradle.kts backend/build.gradle.kts backend/
COPY backend/gradle backend/gradle
RUN cd backend && chmod +x gradlew && ./gradlew --no-daemon -q dependencies > /dev/null
COPY backend/src backend/src
RUN cd backend && ./gradlew --no-daemon -q bootJar -x test

FROM eclipse-temurin:25-jre-noble
# Without Korean fonts rhwp draws the PDF and the preview with empty boxes instead of 한글.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl ca-certificates fonts-nanum fonts-noto-cjk \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY tools/install-rhwp.sh tools/install-rhwp.sh
RUN bash tools/install-rhwp.sh
COPY --from=backend /build/backend/build/libs/actor-api-0.0.1-SNAPSHOT.jar app.jar
COPY --from=frontend /build/frontend/dist public/

# /data is a Railway volume: the completion count and the logs survive redeploys.
ENV SPRING_WEB_RESOURCES_STATIC_LOCATIONS=file:/app/public/ \
    YESULIN_RHWP_PATH=/app/tools/rhwp/rhwp/rhwp \
    YESULIN_STATS_FILE=/data/completed-count.txt \
    YESULIN_LOG_FILE=/data/logs/actor-api.log \
    YESULIN_WORKSPACE=/tmp/yesulin-actor \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
EXPOSE 8080
CMD ["java", "-jar", "app.jar"]
