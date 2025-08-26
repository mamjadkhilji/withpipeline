#!/bin/bash

echo "Watching Git workflow notifications..."
tail -f server.log | grep "NOTIFICATION:" | while read line; do
    echo "🔔 $(date): $line"
    # Optional: Send desktop notification
    # osascript -e "display notification \"$line\" with title \"Git Workflow\""
done