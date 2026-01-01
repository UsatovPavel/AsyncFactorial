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
	docker network inspect prassign_default >/dev/null 2>&1 || docker network create prassign_default
	KAFKA_BOOTSTRAP_SERVERS="kafka1:9092,kafka2:9092,kafka3:9092" \
	COMPOSE_PROJECT_NAME=asyncfactorial \
	docker compose down --remove-orphans ; \
	KAFKA_BOOTSTRAP_SERVERS="kafka1:9092,kafka2:9092,kafka3:9092" \
	COMPOSE_PROJECT_NAME=asyncfactorial \
	docker compose up --force-recreate --remove-orphans consumer

