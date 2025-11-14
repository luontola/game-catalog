FROM eclipse-temurin:25-jre

RUN groupadd -r app \
    && useradd -r -g app app

WORKDIR /app

COPY target/uberjar/game-catalog.jar /app/game-catalog.jar

COPY data/*.csv /app/data/

RUN chown -R app:app /app

USER app

EXPOSE 8080

CMD ["java", \
     "-XX:MaxRAMPercentage=70", \
     "-XX:+UseCompactObjectHeaders", \
     "-XX:+PrintCommandLineFlags", \
     "-jar", "game-catalog.jar"]
