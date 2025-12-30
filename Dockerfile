## Multistage build: compile with sbt, run on slim JRE

# ---- Build stage ----
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app

# sbt install (debian-based)
RUN apt-get update \
 && apt-get install -y curl gnupg \
 && echo "deb https://repo.scala-sbt.org/scalasbt/debian all main" > /etc/apt/sources.list.d/sbt.list \
 && echo "deb https://repo.scala-sbt.org/scalasbt/debian /" > /etc/apt/sources.list.d/sbt_old.list \
 && curl -sL https://keyserver.ubuntu.com/pks/lookup?op=get\&search=0x2EE0EA64E40A89B84B2DF73499E82A75642AC823 | apt-key add - \
 && apt-get update \
 && apt-get install -y sbt \
 && rm -rf /var/lib/apt/lists/*

# Copy build metadata first to leverage docker cache
COPY build.sbt ./
COPY project ./project

# Pre-fetch dependencies with cache mounts (BuildKit required)
RUN --mount=type=cache,target=/root/.ivy2 \
    --mount=type=cache,target=/root/.cache/coursier \
    --mount=type=cache,target=/root/.sbt \
    sbt -batch -no-colors update

# Copy the rest of the sources
COPY . .

# Build fat jar with cached dependencies
RUN --mount=type=cache,target=/root/.ivy2 \
    --mount=type=cache,target=/root/.cache/coursier \
    --mount=type=cache,target=/root/.sbt \
    sbt -batch -no-colors assembly

# ---- Run stage ----
FROM eclipse-temurin:17-jre AS run
WORKDIR /opt/app

ENV SERVER_HOST=0.0.0.0
ENV SERVER_PORT=8080
# Leave SERVER_PARALLELISM unset to use availableProcessors

COPY --from=build /app/target/scala-2.13/individual-task-assembly-0.1.0-SNAPSHOT.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]

