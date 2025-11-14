#!/usr/bin/env bash
set -euo pipefail

# Build the uberjar first
lein uberjar

# Run with native-image-agent to trace reflection usage
java -agentlib:native-image-agent=config-output-dir=resources/META-INF/native-image/game-catalog \
     -jar target/uberjar/game-catalog.jar &

APP_PID=$!

# Give the app time to start
sleep 5

echo "App started on PID $APP_PID. Exercise all functionality (visit pages, etc.), then press Enter to stop..."
read

# Stop the app
kill $APP_PID
wait $APP_PID 2>/dev/null || true

echo "Configuration generated in resources/META-INF/native-image/game-catalog/"
ls -lh resources/META-INF/native-image/game-catalog/
