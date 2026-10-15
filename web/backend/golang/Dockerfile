# =============================================================================
# DAPE Core Backend — Go (Fiber)
# Multi-stage: base -> dev -> build -> prod
# =============================================================================
FROM golang:1.23-alpine AS base

ENV GO111MODULE=on \
    CGO_ENABLED=0 \
    GOOS=linux

RUN apk add --no-cache git ca-certificates tzdata wget make

WORKDIR /app

COPY go.mod ./
# go.sum generated on first `go mod tidy`; copy if present.
COPY go.su[m] ./
RUN go mod download || true

# ---------------------------------------------------------------------------
FROM base AS dev
RUN go install github.com/air-verse/air@latest || true
COPY . .
RUN go mod tidy
EXPOSE 8080
# air for live-reload in dev; falls back to go run if air missing.
CMD ["sh", "-c", "air || go run ./cmd/api"]

# ---------------------------------------------------------------------------
FROM base AS build
COPY . .
RUN go mod tidy && \
    go build -trimpath -ldflags="-s -w" -o /out/dape-api ./cmd/api

# ---------------------------------------------------------------------------
FROM alpine:3.20 AS prod
RUN apk add --no-cache ca-certificates tzdata wget && \
    adduser -D -u 10001 dape
WORKDIR /app
COPY --from=build /out/dape-api /app/dape-api
USER dape
EXPOSE 8080
ENTRYPOINT ["/app/dape-api"]
