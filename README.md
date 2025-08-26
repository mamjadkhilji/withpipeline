# KIRO MCP Servers

Collection of Model Context Protocol (MCP) servers for various integrations.

## Available Servers

### 1. S3 MCP Server (`s3-mcp`)
- **Purpose**: AWS S3 operations and management
- **Port**: 8081
- **Features**: Bucket operations, file upload/download, S3 management
- **Run**: `cd s3-mcp && ./run-server.sh`

### 2. CI/CD MCP Server (`ci-cd-mcp`)
- **Purpose**: Continuous Integration and Deployment operations
- **Port**: 8082
- **Features**: Pipeline management, build automation, deployment workflows
- **Run**: `cd ci-cd-mcp && ./run-server.sh`

### 3. Git Notification MCP Server (`git-notify-mcp`)
- **Purpose**: GitHub workflow status notifications
- **Port**: 8080
- **Features**: Webhook listener, workflow polling, real-time notifications
- **Run**: `cd git-notify-mcp && ./run-server.sh`
- **Environment**: Set `GITHUB_TOKEN` and `GITHUB_REPO`

## Quick Start

```bash
# Build all servers
mvn clean package

# Start individual servers
cd <server-name>
./run-server.sh

# Or start Git notify server in background
cd git-notify-mcp
./start-server-background.sh
```

## Configuration

Each server has its own configuration requirements. Check individual README files for specific setup instructions.