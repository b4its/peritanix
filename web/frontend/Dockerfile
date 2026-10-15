# =============================================================================
# DAPE Web — SvelteKit PWA
# Multi-stage: base -> dev -> build -> prod
# =============================================================================
FROM node:22-alpine AS base
WORKDIR /app
RUN apk add --no-cache libc6-compat curl
COPY package.json ./
COPY package-lock.jso[n] ./
RUN (npm ci --no-audit --no-fund || npm install --no-audit --no-fund)

# ---------------------------------------------------------------------------
FROM base AS dev
COPY . .
EXPOSE 5173
CMD ["npm", "run", "dev", "--", "--host", "0.0.0.0", "--port", "5173"]

# ---------------------------------------------------------------------------
FROM base AS build
COPY . .
RUN npm run build

# ---------------------------------------------------------------------------
FROM node:22-alpine AS prod
WORKDIR /app
RUN addgroup -S dape && adduser -S dape -G dape
COPY --from=build /app/build ./build
COPY --from=build /app/package.json ./
COPY --from=build /app/node_modules ./node_modules
USER dape
EXPOSE 3000
ENV NODE_ENV=production PORT=3000 HOST=0.0.0.0
CMD ["node", "build"]
