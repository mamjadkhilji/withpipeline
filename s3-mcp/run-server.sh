#!/bin/bash
cd "$(dirname "$0")"
exec java -cp "target/classes:$(mvn dependency:build-classpath -Dmdep.outputFile=/dev/stdout -q)" com.mcp.s3.S3McpServer