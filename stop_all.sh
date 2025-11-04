#!/bin/bash

# Stop all running processes (works both inside and outside distrobox)

DISTROBOX_NAME="breadening"

# Color codes
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo -e "${YELLOW}Stopping all mutex testing processes...${NC}"

# Check if we're inside a distrobox
if [ -f /run/.containerenv ]; then
    # We're inside the distrobox
    echo "Running inside distrobox, stopping local processes..."
    pkill -f "python.*main.py"
    pkill -f "mutex.MutexAdapter"
    pkill -f "tron.*centralized_mutex"
else
    # We're outside, stop processes in the distrobox
    echo "Stopping processes in distrobox: $DISTROBOX_NAME..."
    distrobox enter $DISTROBOX_NAME -- bash -c "pkill -f 'python.*main.py'; pkill -f 'mutex.MutexAdapter'; pkill -f 'tron.*centralized_mutex'"
fi

echo -e "${GREEN}All processes stopped${NC}"
