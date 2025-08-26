#!/bin/bash

echo "🔔 Watching Git workflow notifications..."
echo "Press Ctrl+C to stop"

if [ ! -f server.log ]; then
    echo "❌ server.log not found. Make sure the Git notify MCP server is running."
    exit 1
fi

# Show recent notifications first
echo "📋 Recent notifications:"
grep "NOTIFICATION:" server.log | tail -3 | while read line; do
    echo "   $line"
done
echo ""

# Watch for new notifications
tail -f server.log | grep --line-buffered "NOTIFICATION:" | while read line; do
    timestamp=$(date '+%H:%M:%S')
    echo "🔔 [$timestamp] $line"
    
    # Optional: macOS desktop notification
    if command -v osascript >/dev/null 2>&1; then
        notification=$(echo "$line" | sed 's/NOTIFICATION: //')
        osascript -e "display notification \"$notification\" with title \"Git Workflow\"" 2>/dev/null
    fi
done