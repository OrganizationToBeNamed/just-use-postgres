#!/usr/bin/env bash
# ──────────────────────────────────────────────────────────────────────────────
# start.sh — One command to spin up the entire Just Use Postgres stack
# ──────────────────────────────────────────────────────────────────────────────
set -euo pipefail

# ── Colors ────────────────────────────────────────────────────────────────────
GREEN='\033[0;32m'
CYAN='\033[0;36m'
YELLOW='\033[1;33m'
BOLD='\033[1m'
NC='\033[0m' # No Color

echo -e "${BOLD}┌──────────────────────────────────────────────────────────────┐${NC}"
echo -e "${BOLD}│         🐘  Just Use Postgres — Starting Stack              │${NC}"
echo -e "${BOLD}└──────────────────────────────────────────────────────────────┘${NC}"
echo ""

# ── Pre-flight checks ────────────────────────────────────────────────────────
if ! command -v docker &> /dev/null; then
    echo -e "${YELLOW}✗ Docker is not installed. Please install Docker first.${NC}"
    exit 1
fi

if ! docker info &> /dev/null 2>&1; then
    echo -e "${YELLOW}✗ Docker daemon is not running. Please start Docker Desktop.${NC}"
    exit 1
fi

# ── Tear down any previous run ───────────────────────────────────────────────
echo -e "${CYAN}▸ Stopping any previous containers...${NC}"
docker compose down --remove-orphans 2>/dev/null || true
echo ""

# ── Build and start ──────────────────────────────────────────────────────────
echo -e "${CYAN}▸ Building and starting Postgres + Spring Boot...${NC}"
echo -e "  This may take a minute on first run (downloading images + Maven deps)."
echo ""
docker compose up --build -d

# ── Wait for Postgres health check ───────────────────────────────────────────
echo ""
echo -e "${CYAN}▸ Waiting for Postgres to be healthy...${NC}"
SECONDS_WAITED=0
MAX_WAIT=60
until docker inspect --format='{{.State.Health.Status}}' just-use-postgres-db 2>/dev/null | grep -q "healthy"; do
    sleep 2
    SECONDS_WAITED=$((SECONDS_WAITED + 2))
    if [ $SECONDS_WAITED -ge $MAX_WAIT ]; then
        echo -e "${YELLOW}✗ Postgres did not become healthy within ${MAX_WAIT}s.${NC}"
        echo "  Check logs: docker compose logs postgres"
        exit 1
    fi
    printf "  Waiting... (%ds)\r" $SECONDS_WAITED
done
echo -e "  ${GREEN}✓ Postgres is healthy (${SECONDS_WAITED}s)${NC}"

# ── Wait for Spring Boot to be ready ─────────────────────────────────────────
echo ""
echo -e "${CYAN}▸ Waiting for Spring Boot application to start...${NC}"
SECONDS_WAITED=0
MAX_WAIT=120
until curl -sf http://localhost:8080/actuator/health > /dev/null 2>&1; do
    sleep 3
    SECONDS_WAITED=$((SECONDS_WAITED + 3))
    if [ $SECONDS_WAITED -ge $MAX_WAIT ]; then
        echo -e "${YELLOW}✗ Spring Boot did not start within ${MAX_WAIT}s.${NC}"
        echo "  Check logs: docker compose logs app"
        exit 1
    fi
    printf "  Waiting... (%ds)\r" $SECONDS_WAITED
done
echo -e "  ${GREEN}✓ Application is ready (${SECONDS_WAITED}s)${NC}"

# ── Print URLs ────────────────────────────────────────────────────────────────
echo ""
echo -e "${BOLD}┌──────────────────────────────────────────────────────────────┐${NC}"
echo -e "${BOLD}│  ${GREEN}✓ Stack is up and running!${NC}${BOLD}                                  │${NC}"
echo -e "${BOLD}├──────────────────────────────────────────────────────────────┤${NC}"
echo -e "${BOLD}│                                                              │${NC}"
echo -e "${BOLD}│  ${CYAN}Swagger UI${NC}  ${BOLD}→${NC}  http://localhost:8080/swagger-ui.html        ${BOLD}│${NC}"
echo -e "${BOLD}│  ${CYAN}API Docs${NC}    ${BOLD}→${NC}  http://localhost:8080/api-docs               ${BOLD}│${NC}"
echo -e "${BOLD}│  ${CYAN}Health${NC}      ${BOLD}→${NC}  http://localhost:8080/actuator/health         ${BOLD}│${NC}"
echo -e "${BOLD}│                                                              │${NC}"
echo -e "${BOLD}│  ${YELLOW}Start exploring the API in Swagger UI!${NC}                       ${BOLD}│${NC}"
echo -e "${BOLD}│  All 10 Postgres-powered services are ready to demo.${NC}        ${BOLD}│${NC}"
echo -e "${BOLD}│                                                              │${NC}"
echo -e "${BOLD}├──────────────────────────────────────────────────────────────┤${NC}"
echo -e "${BOLD}│  Stop:${NC}  docker compose down                                   ${BOLD}│${NC}"
echo -e "${BOLD}│  Logs:${NC}  docker compose logs -f app                            ${BOLD}│${NC}"
echo -e "${BOLD}└──────────────────────────────────────────────────────────────┘${NC}"
