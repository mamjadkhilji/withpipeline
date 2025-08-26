#!/bin/bash

# Set environment variables
export GITHUB_TOKEN="${GITHUB_TOKEN:-}"
export GITHUB_REPO="${GITHUB_REPO:-mamjadkhilji/withpipeline}"

# Build and run
mvn clean package -q
java -jar target/git-notify-mcp-1.0.0.jar