#!/bin/bash

# Set environment variables
export GITHUB_TOKEN="${GITHUB_TOKEN:-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}"
export GITHUB_REPO="${GITHUB_REPO:-mamjadkhilji/withpipeline}"

# Build and run
mvn clean package -q
java -jar target/git-notify-mcp-1.0.0.jar