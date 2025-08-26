# Git Notification MCP Server

Notifies on GitHub workflow run status conclusions.

## Setup

1. Set environment variables:
```bash
export GITHUB_TOKEN="your_github_token"
export GITHUB_REPO="owner/repo"
```

2. Run server:
```bash
./run-server.sh
```

## Features

- **Webhook listener**: Receives GitHub workflow_run events on port 8080
- **Polling fallback**: Checks workflow status every 30 seconds
- **Real-time notifications**: Sends MCP notifications on workflow completion

## GitHub Webhook Setup

Add webhook to your repository:
- URL: `http://your-server:8080/webhook`
- Events: `Workflow runs`
- Content type: `application/json`

## Usage

The server automatically notifies when workflows complete with status:
- `success` - Workflow passed
- `failure` - Workflow failed  
- `cancelled` - Workflow cancelled
- `timed_out` - Workflow timed out