#!/bin/bash

# Start MCP server in background
export GITHUB_TOKEN="${GITHUB_TOKEN:-}"
export GITHUB_REPO="${GITHUB_REPO:-mamjadkhilji/withpipeline}"

echo "Starting Git Notification MCP Server..."
nohup java -jar target/git-notify-mcp-1.0.0.jar > server.log 2>&1 &
echo $! > server.pid
echo "Server started with PID $(cat server.pid)"
echo "Logs: tail -f server.log"