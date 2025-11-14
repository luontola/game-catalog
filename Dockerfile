FROM ghcr.io/graalvm/native-image-community:23 AS builder

WORKDIR /build

COPY target/uberjar/game-catalog.jar /build/game-catalog.jar

RUN native-image \
    --no-fallback \
    --install-exit-handlers \
    -H:+ReportExceptionStackTraces \
    -jar game-catalog.jar \
    game-catalog

FROM ubuntu:24.04

RUN groupadd -r app \
    && useradd -r -g app app

WORKDIR /app

COPY --from=builder /build/game-catalog /app/game-catalog

COPY data/*.csv /app/data/

RUN chown -R app:app /app

USER app

EXPOSE 8080

CMD ["/app/game-catalog"]
