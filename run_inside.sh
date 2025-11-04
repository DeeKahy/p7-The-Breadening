#!/bin/bash

# This script should be run from INSIDE the distrobox
# Run this if you're already in: distrobox enter breadening

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
MAGENTA='\033[0;35m'
NC='\033[0m' # No Color

# Colors for each log source
SERVER_COLOR='\033[0;32m'    # Green
ADAPTER_COLOR='\033[0;36m'   # Cyan
TRON_COLOR='\033[0;35m'      # Magenta

# Create logs directory if it doesn't exist
mkdir -p logs

# Clean up old logs
rm -f logs/*.log

echo -e "${GREEN}=== Starting Mutex Testing Environment ===${NC}"
echo "Logs will be saved in ./logs/"
echo ""

# Function to cleanup on exit
cleanup() {
    echo -e "\n${YELLOW}Shutting down all processes...${NC}"
    kill $(jobs -p) 2>/dev/null
    wait
    echo -e "${GREEN}All processes stopped${NC}"
    exit 0
}

trap cleanup SIGINT SIGTERM

# 1. Start Flask server
echo -e "${BLUE}[1/3] Starting Flask server (port 5000)...${NC}"
cd dut
python main.py > ../logs/server.log 2>&1 &
SERVER_PID=$!
cd ..
echo "Flask server PID: $SERVER_PID"
sleep 2  # Give the server time to start

# Check if server started successfully
if ! ps -p $SERVER_PID > /dev/null; then
    echo -e "${RED}Failed to start Flask server!${NC}"
    echo "Check logs/server.log for details"
    exit 1
fi
echo -e "${GREEN}✓ Flask server running${NC}"
echo ""

# 2. Start Java adapter
echo -e "${BLUE}[2/3] Starting MutexAdapter (port 9999)...${NC}"
java -DmutexServerBase="http://localhost:5000" -cp build mutex.MutexAdapter 9999 > logs/adapter.log 2>&1 &
ADAPTER_PID=$!
echo "Adapter PID: $ADAPTER_PID"
sleep 2  # Give the adapter time to start

# Check if adapter started successfully
if ! ps -p $ADAPTER_PID > /dev/null; then
    echo -e "${RED}Failed to start adapter!${NC}"
    echo "Check logs/adapter.log for details"
    kill $SERVER_PID 2>/dev/null
    exit 1
fi
echo -e "${GREEN}✓ MutexAdapter running${NC}"
echo ""

# 3. Start TRON
echo -e "${BLUE}[3/3] Starting UPPAAL TRON...${NC}"
../uppaal-tron-1.5-linux/tron -u 4000,4000 -P 10,200 -F 300 -I SocketAdapter -v 9 src/mutex/centralized_mutex.xml -- localhost 9999 > logs/tron.log 2>&1 &
TRON_PID=$!
echo "TRON PID: $TRON_PID"
echo -e "${GREEN}✓ TRON running${NC}"
echo ""

echo -e "${GREEN}=== All processes started successfully! ===${NC}"
echo ""
echo "Process IDs:"
echo -e "  ${SERVER_COLOR}Server:${NC}  $SERVER_PID"
echo -e "  ${ADAPTER_COLOR}Adapter:${NC} $ADAPTER_PID"
echo -e "  ${TRON_COLOR}TRON:${NC}    $TRON_PID"
echo ""
echo "Log files:"
echo -e "  ${SERVER_COLOR}Server:${NC}  logs/server.log"
echo -e "  ${ADAPTER_COLOR}Adapter:${NC} logs/adapter.log"
echo -e "  ${TRON_COLOR}TRON:${NC}    logs/tron.log"
echo ""
echo -e "${YELLOW}Press Ctrl+C to stop all processes${NC}"
echo ""
echo "=== Live Output (color-coded by source) ==="
echo ""

# Function to tail a log with colored prefix
tail_with_color() {
    local logfile=$1
    local color=$2
    local prefix=$3

    tail -f "$logfile" 2>/dev/null | while IFS= read -r line; do
        echo -e "${color}[${prefix}]${NC} $line"
    done
}

# Tail all three logs with different colors
tail_with_color "logs/server.log" "$SERVER_COLOR" "SERVER" &
tail_with_color "logs/adapter.log" "$ADAPTER_COLOR" "ADAPTER" &
tail_with_color "logs/tron.log" "$TRON_COLOR" "TRON" &

# Wait for all background jobs
wait
