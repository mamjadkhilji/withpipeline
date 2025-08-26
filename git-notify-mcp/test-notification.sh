#!/bin/bash

# Test webhook notification
curl -X POST http://localhost:8080/webhook \
  -H "Content-Type: application/json" \
  -H "X-GitHub-Event: workflow_run" \
  -d '{
    "workflow_run": {
      "name": "Build and Test",
      "status": "completed",
      "conclusion": "success",
      "html_url": "https://github.com/owner/repo/actions/runs/123"
    }
  }'