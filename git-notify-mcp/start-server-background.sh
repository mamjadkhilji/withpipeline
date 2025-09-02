#!/bin/bash
cd "$(dirname "$0")"

# Build if needed
if [ ! -f "target/classes/com/mcp/gitnotify/GitNotifyMcpServer.class" ]; then
    mvn compile
fi

# Start server in background
nohup java -cp "target/classes:$(mvn dependency:build-classpath -Dmdep.outputFile=/dev/stdout -q)" \
    com.mcp.gitnotify.GitNotifyMcpServer > git-notify.log 2>&1 &

echo $! > git-notify.pid
echo "Git Notify MCP Server started in background (PID: $!)"
echo "Webhook endpoint: http://localhost:8080/webhook"
echo "Logs: git-notify.log"