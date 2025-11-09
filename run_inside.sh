#!/bin/bash

# This script should be run from INSIDE the distrobox
# Run this if you're already in: distrobox enter breadening

# Establish project root directory (where this script is located)
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$PROJECT_ROOT"

# Default config file
CONFIG_FILE="$PROJECT_ROOT/config.json"

get_config() {
    local path="$1"
    local default="$2"

    local value=$(jq -r "$path // \"$default\"" "$CONFIG_FILE" 2>/dev/null)
    if [ $? -ne 0 ] || [ -z "$value" ] || [ "$value" = "null" ]; then
        echo "$default"
    else
        echo "$value"
    fi
}

# Check if jq is installed
if ! command -v jq &> /dev/null; then
    echo "Error: jq is not installed. Please install it to use config files."
    echo "Run: sudo apt-get install jq"
    exit 1
fi

# Load configuration (all paths will be relative to PROJECT_ROOT)
LOGS_PATH=$(get_config ".logs.path" "logs")
DUT_PROJECT_PATH=$(get_config ".dut.project_path" "dut")
DUT_MAIN_FILE=$(get_config ".dut.main_file" "main.py")
DUT_PYTHON_CMD=$(get_config ".dut.python_cmd" "python3")
UPPAAL_MODEL=$(get_config ".uppaal.model_file" "src/mutex/centralized_mutex.xml")
UPPAAL_TRON_PATH=$(get_config ".uppaal.tron_path" "../uppaal-tron-1.5-linux/tron")
UPPAAL_OPT_U=$(get_config ".uppaal.options.u" "4000,4000")
UPPAAL_OPT_P=$(get_config ".uppaal.options.P" "10,200")
UPPAAL_OPT_F=$(get_config ".uppaal.options.F" "300")
UPPAAL_OPT_I=$(get_config ".uppaal.options.I" "SocketAdapter")
UPPAAL_OPT_V=$(get_config ".uppaal.options.v" "9")
ADAPTER_PORT=$(get_config ".adapter.port" "9999")
ADAPTER_SERVER_BASE=$(get_config ".adapter.server_base" "http://localhost:5000")
SERVER_PORT=$(get_config ".server.port" "5000")

# Convert relative paths to absolute paths based on PROJECT_ROOT
LOGS_PATH="$PROJECT_ROOT/$LOGS_PATH"
DUT_PROJECT_PATH="$PROJECT_ROOT/$DUT_PROJECT_PATH"
UPPAAL_MODEL="$PROJECT_ROOT/$UPPAAL_MODEL"
# UPPAAL_TRON_PATH is already relative to parent directory, resolve it
UPPAAL_TRON_PATH="$(cd "$PROJECT_ROOT" && cd "$(dirname "$UPPAAL_TRON_PATH")" && pwd)/$(basename "$UPPAAL_TRON_PATH")"

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
mkdir -p "$LOGS_PATH"

# Clean up old logs
rm -f "$LOGS_PATH"/*.log

echo -e "${GREEN}=== Starting Mutex Testing Environment ===${NC}"
echo "Logs will be saved in $LOGS_PATH"
echo ""

# 0. Compile the adapter
echo -e "${BLUE}[0/3] Compiling MutexAdapter...${NC}"
javac -cp ".:src" -d build src/com/uppaal/tron/Adapter.java src/com/uppaal/tron/Reporter.java src/mutex/MutexAdapter.java
if [ $? -ne 0 ]; then
    echo -e "${RED}Failed to compile MutexAdapter!${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Compilation successful${NC}"
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
echo -e "${BLUE}[1/3] Starting Flask server (port $SERVER_PORT)...${NC}"
cd "$DUT_PROJECT_PATH"
$DUT_PYTHON_CMD $DUT_MAIN_FILE > "$LOGS_PATH/server.log" 2>&1 &
SERVER_PID=$!
cd "$PROJECT_ROOT"
echo "Flask server PID: $SERVER_PID"
sleep 2  # Give the server time to start

# Check if server started successfully
if ! ps -p $SERVER_PID > /dev/null; then
    echo -e "${RED}Failed to start Flask server!${NC}"
    echo "Check $LOGS_PATH/server.log for details"
    exit 1
fi
echo -e "${GREEN}✓ Flask server running${NC}"
echo ""

# 2. Start Java adapter
echo -e "${BLUE}[2/3] Starting MutexAdapter (port $ADAPTER_PORT)...${NC}"
java -DmutexServerBase="$ADAPTER_SERVER_BASE" -cp build mutex.MutexAdapter $ADAPTER_PORT > "$LOGS_PATH/adapter.log" 2>&1 &
ADAPTER_PID=$!
echo "Adapter PID: $ADAPTER_PID"
sleep 2  # Give the adapter time to start

# Check if adapter started successfully
if ! ps -p $ADAPTER_PID > /dev/null; then
    echo -e "${RED}Failed to start adapter!${NC}"
    echo "Check $LOGS_PATH/adapter.log for details"
    kill $SERVER_PID 2>/dev/null
    exit 1
fi
echo -e "${GREEN}✓ MutexAdapter running${NC}"
echo ""

# 3. Start TRON
echo -e "${BLUE}[3/3] Starting UPPAAL TRON...${NC}"
$UPPAAL_TRON_PATH -u $UPPAAL_OPT_U -P $UPPAAL_OPT_P -F $UPPAAL_OPT_F -I $UPPAAL_OPT_I -v $UPPAAL_OPT_V "$UPPAAL_MODEL" -- localhost $ADAPTER_PORT > "$LOGS_PATH/tron.log" 2>&1 &
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
echo -e "  ${SERVER_COLOR}Server:${NC}  $LOGS_PATH/server.log"
echo -e "  ${ADAPTER_COLOR}Adapter:${NC} $LOGS_PATH/adapter.log"
echo -e "  ${TRON_COLOR}TRON:${NC}    $LOGS_PATH/tron.log"
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
tail_with_color "$LOGS_PATH/server.log" "$SERVER_COLOR" "SERVER" &
tail_with_color "$LOGS_PATH/adapter.log" "$ADAPTER_COLOR" "ADAPTER" &
tail_with_color "$LOGS_PATH/tron.log" "$TRON_COLOR" "TRON" &

# Wait for all background jobs
wait
