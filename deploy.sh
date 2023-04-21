#!/usr/bin/env bash
set -eux
firebase deploy --only firestore:rules
