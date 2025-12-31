.PHONY: build up up-app up-kafka up-all down test logs clean-tests

build:
	docker compose build app
	
up-app:
	docker compose up app

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

