#!/usr/bin/env bash
set -euxo pipefail

lein uberjar
docker build -t game-catalog:latest .
