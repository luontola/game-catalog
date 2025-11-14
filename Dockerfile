FROM ghcr.io/graalvm/native-image-community:25 AS builder

WORKDIR /build

COPY target/uberjar/game-catalog.jar /build/game-catalog.jar

# https://github.com/clj-easy/graalvm-clojure/blob/master/doc/clojure-graalvm-native-binary.md
RUN native-image \
    --report-unsupported-elements-at-runtime \
    --initialize-at-build-time \
    --no-server \
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
