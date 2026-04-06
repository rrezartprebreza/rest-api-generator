# ── Stage 1: build ────────────────────────────────────────────────────────────
FROM gradle:8.10-jdk17 AS builder
WORKDIR /workspace
COPY . .
RUN gradle --no-daemon installDist -x test

# ── Stage 2: runtime ──────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-jammy
LABEL org.opencontainers.image.title="REST API Generator" \
      org.opencontainers.image.description="Generate production-ready Spring Boot APIs from plain English" \
      org.opencontainers.image.source="https://github.com/rrezartprebreza/rest-api-generator"

RUN apt-get update \
 && apt-get install -y --no-install-recommends wget \
 && rm -rf /var/lib/apt/lists/* \
 && groupadd --system appgroup \
 && useradd --system --gid appgroup --home-dir /app --no-create-home appuser
WORKDIR /app
COPY --from=builder /workspace/build/install/rest-api-generator .
RUN chown -R appuser:appgroup /app
USER appuser

EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=3s --start-period=5s --retries=3 \
  CMD wget -qO- http://localhost:8080/health || exit 1

ENV PORT=8080
ENTRYPOINT ["bin/rest-api-generator", "serve"]
CMD ["--port", "8080"]
