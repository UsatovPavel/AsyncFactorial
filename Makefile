.PHONY: build up up-app up-kafka up-all down test logs clean-tests service

build:
	docker compose build app
	
up-app:
	docker compose --profile http up app

up-kafka:
	docker compose up kafka

up-all:
	docker compose up

down:
	docker compose down

test:
	docker compose run --rm tests

logs:
	docker compose logs -f app

clean-tests:
	docker compose rm -f tests || true

# Run consumer against 3-broker cluster (override bootstrap.servers).
service:
	@{ \
	  set -a; [ -f .env ] && . ./.env; set +a; \
	  [ -n "$${KAFKA_BOOTSTRAP_SERVERS:-}" ] || { echo "KAFKA_BOOTSTRAP_SERVERS is required" && exit 1; }; \
	  [ -n "$${CONSUMER_REPLICAS:-}" ] || { echo "CONSUMER_REPLICAS is required" && exit 1; }; \
	  docker network inspect prassign_default >/dev/null 2>&1 || { echo "Docker network prassign_default not found, make Go app first" && exit 1; }; \
	  KAFKA_BOOTSTRAP_SERVERS="$${KAFKA_BOOTSTRAP_SERVERS}" \
	  COMPOSE_PROJECT_NAME=asyncfactorial \
	  docker compose down --remove-orphans; \
	  KAFKA_BOOTSTRAP_SERVERS="$${KAFKA_BOOTSTRAP_SERVERS}" \
	  COMPOSE_PROJECT_NAME=asyncfactorial \
	  docker compose up -d --force-recreate --remove-orphans --scale consumer=$${CONSUMER_REPLICAS} consumer; \
	}

