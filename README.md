# AsyncFactorial

Scala service for factorial processing with three modes:
- Kafka consumer/producer: consumes `factorial.tasks`, produces to `factorial.results`.
- HTTP server: consumes JSON and responds with factorial results.
- Console mode: reads numbers from stdin, writes results to `out.txt` asynchronously.

## Quick Start
1. Clone repo into a sibling folder of PRAssign (for Go/Kafka stack):
   ```bash
   git clone https://github.com/UsatovPavel/AsyncFactorial.git
   cd AsyncFactorial
   ```
2. Create `.env`:
   ```ini
   # HTTP сервис Scala 
   SERVER_HOST=0.0.0.0
   SERVER_PORT=8080
   SERVER_PARALLELISM=4   # empty => use availableProcessors

   # Kafka service
   KAFKA_BOOTSTRAP_SERVERS=kafka1:9092,kafka2:9092,kafka3:9092
   KAFKA_GROUP_ID=asyncfactorial-consumer
   KAFKA_INPUT_TOPIC=factorial.tasks
   KAFKA_OUTPUT_TOPIC=factorial.results
   CONSUMER_REPLICAS=4

   KAFKA_EXPOSE_PORT=9092  # for local-kafka profile only
   ```
3. Run Kafka consumer (needs Go/Kafka network `prassign_default`):
   ```bash
   make service
   ```
   (reads `.env`, scales consumer to `CONSUMER_REPLICAS`).

## Modes
- **Kafka service**: IOApp `KafkaConsumerTask`, consumes `factorial.tasks`, publishes `factorial.results`. Config via env/application.conf.
- **Console**:
  - IOApp `Task` parses integers from stdin until `exit`, runs factorials concurrently (non-blocking input), appends results to `out.txt` (order not guaranteed), logs parse errors.
  - Two modes: `waitAll=true` waits for all tasks on exit; `waitAll=false` cancels unfinished work and stops cleanly.
  - Built on cats-effect fibers with Supervisor (lightweight, easy to spawn).
- **HTTP**: `POST /factorial` (Tapir + Vert.x), body = JSON array of Int, optional header `X-Job-Id` (if missing, generated). Swagger: `/docs`

## Tech Stack
- **Language**: Scala 2.13
- **Concurrency/Streams**: cats-effect 3, fs2
- **Kafka**: fs2-kafka
- **HTTP**: tapir 
- **JSON**: circe
- **Test**: scalatest (with cats-effect)
- **Build tools**: Docker Compose (external `prassign_default`), Makefile

## Env / Config
- Defaults in `application.conf` (`server.*`, `kafka.*`).
- Runtime overrides via env (`SERVER_*`, `KAFKA_*`, `CONSUMER_REPLICAS`, `KAFKA_EXPOSE_PORT`).

## Run
- Kafka consumer: `make service` (requires `.env`, uses `KAFKA_BOOTSTRAP_SERVERS`, `CONSUMER_REPLICAS`).
- Local single-broker profile (optional): `docker compose --profile local-kafka up`.
- HTTP mode (if needed): `docker compose --profile http up app` (expects env `SERVER_*`).

## Testing
``` bash
make test
```
- Console pipeline: TaskSpec checks input/parse/write, waitAll true/false, performance
- Writer: NumberWriterSpec — verifies how the writer logs parse errors and normal results, and that it stops cleanly on Shutdown
- Core: FactorialAccumulatorSpec covers factorial on valid/invalid inputs and parse errors.
- HTTP: smoke/bulk/invalid-json/parallel/big-job-id specs for POST /factorial 

## Notes
- Compose `networks.default` is external `prassign_default` to talk to Go/Kafka stack.