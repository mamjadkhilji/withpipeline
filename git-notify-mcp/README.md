# Git Notify MCP Server

GitHub workflow status notification server with webhook listener and polling.

## Features

- **Webhook Listener**: HTTP server on port 8080 for GitHub webhooks
- **Workflow Polling**: Checks workflow status every 30 seconds
- **Real-time Notifications**: Logs workflow status changes
- **MCP Integration**: Tools for health check and status monitoring

## Setup

1. **Configure Environment:**
   ```bash
   export GITHUB_TOKEN=your_github_token
   export GITHUB_REPO=owner/repo
   ```

2. **Build and Start:**
   ```bash
   mvn compile
   ./start-server-background.sh
   ```

3. **Configure GitHub Webhook:**
   - Go to Repository Settings → Webhooks
   - Add webhook: `http://your-server:8080/webhook`
   - Select "Workflow runs" events

## Tools

- `health_check`: Check service status
- `get_notifications`: View recent notifications
- `webhook_status`: Check webhook server status

## Usage

The server runs in background and logs notifications to `git-notify.log`. 
Webhook endpoint: `http://localhost:8080/webhook`