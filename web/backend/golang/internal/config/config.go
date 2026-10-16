// Package config loads runtime configuration from the environment.
package config

import (
	"os"
	"strconv"
)

// Config holds all runtime settings for the DAPE backend.
type Config struct {
	Env           string
	HTTPPort      string
	DatabaseURL   string
	RedisAddr     string
	MLBaseURL     string
	InternalToken string
	JWTSecret     string
	JWTTTLHours   int
	AuthRequired  bool
	MinioEndpoint string
	MinioAccess   string
	MinioSecret   string
	MinioBucket   string
	MinioPublic   string
}

// Load reads configuration from environment variables with sane defaults.
func Load() Config {
	return Config{
		Env:           getEnv("DAPE_ENV", "development"),
		HTTPPort:      getEnv("HTTP_PORT", "8080"),
		DatabaseURL:   getEnv("DATABASE_URL", "postgres://dape:dape_secret@localhost:55491/dape?sslmode=disable"),
		RedisAddr:     getEnv("REDIS_ADDR", "localhost:56438"),
		MLBaseURL:     getEnv("ML_BASE_URL", "http://localhost:58059"),
		InternalToken: getEnv("INTERNAL_TOKEN", "internal-shared-secret-change-me"),
		JWTSecret:     getEnv("JWT_SECRET", "change-me-in-production-please-32chars-min"),
		JWTTTLHours:   getEnvInt("JWT_TTL_HOURS", 720),
		AuthRequired:  getEnvBool("AUTH_REQUIRED", false),
		MinioEndpoint: getEnv("MINIO_ENDPOINT", "localhost:59059"),
		MinioAccess:   getEnv("MINIO_ACCESS_KEY", "dape_minio"),
		MinioSecret:   getEnv("MINIO_SECRET_KEY", "dape_minio_secret"),
		MinioBucket:   getEnv("MINIO_BUCKET_RAW", "dape-raw"),
		MinioPublic:   getEnv("MINIO_PUBLIC_BASE", "http://localhost:59059"),
	}
}

func getEnv(key, def string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return def
}

func getEnvInt(key string, def int) int {
	if v := os.Getenv(key); v != "" {
		if n, err := strconv.Atoi(v); err == nil {
			return n
		}
	}
	return def
}

func getEnvBool(key string, def bool) bool {
	if v := os.Getenv(key); v != "" {
		if b, err := strconv.ParseBool(v); err == nil {
			return b
		}
	}
	return def
}
