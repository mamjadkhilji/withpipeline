#!/bin/bash
cd "$(dirname "$0")"

# Build if needed
if [ ! -f "target/classes/com/mcp/git/GitCustomMcpServer.class" ]; then
    mvn compile
fi

# Run server
exec java -cp "target/classes:$(mvn dependency:build-classpath -Dmdep.outputFile=/dev/stdout -q)" \
    com.mcp.git.GitCustomMcpServer