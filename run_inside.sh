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

# ═══════════════════════════════════════════════════════════════════════════
# VERSION SELECTION
# ═══════════════════════════════════════════════════════════════════════════

echo -e "${CYAN}=== Mutex Testing Environment - Version Selection ===${NC}"
echo ""
echo "Select which version to run:"
echo -e "  ${GREEN}1${NC}) WebVersion (HTTP-based)"
echo -e "  ${GREEN}2${NC}) SocketVersion (WebSocket-based)"
echo ""
read -p "Enter your choice (1 or 2): " version_choice

case "$version_choice" in
    1)
        VERSION="web"
        DUT_PATH="dut/WebVersion"
        ADAPTER_PATH="src/mutex/WebMutex/MutexAdapter.java"
        ADAPTER_TYPE="HTTP"
        echo -e "${GREEN}✓ Selected: WebVersion (HTTP)${NC}"
        ;;
    2)
        VERSION="socket"
        DUT_PATH="dut/SocketVersion"
        ADAPTER_PATH="src/mutex/SocketMutex/MutexAdapter.java"
        ADAPTER_TYPE="WebSocket"
        echo -e "${GREEN}✓ Selected: SocketVersion (WebSocket)${NC}"
        ;;
    *)
        echo -e "${RED}Invalid choice! Please enter 1 or 2.${NC}"
        exit 1
        ;;
esac

echo ""

# Colors for each log source
SERVER_COLOR='\033[0;32m'    # Green
ADAPTER_COLOR='\033[0;36m'   # Cyan
TRON_COLOR='\033[0;35m'      # Magenta

# Create logs directory if it doesn't exist
mkdir -p logs

# Clean up old logs
rm -f logs/*.log

echo -e "${GREEN}=== Starting Mutex Testing Environment (${ADAPTER_TYPE}) ===${NC}"
echo "Version: ${VERSION}"
echo "DUT Path: ${DUT_PATH}"
echo "Adapter Path: ${ADAPTER_PATH}"
echo "Logs will be saved in ./logs/"
echo ""

# Check for required Python packages
echo -e "${BLUE}[0/5] Checking Python dependencies...${NC}"
if ! python3 -c "import flask_sock" 2>/dev/null; then
    echo -e "${YELLOW}flask-sock not found, installing...${NC}"
    pip3 install flask-sock
    if [ $? -ne 0 ]; then
        echo -e "${RED}Failed to install flask-sock!${NC}"
        echo "Please run: pip3 install flask-sock"
        exit 1
    fi
fi
echo -e "${GREEN}✓ Python dependencies OK${NC}"
echo ""

# Check for required Java libraries
echo -e "${BLUE}[1/5] Checking Java dependencies...${NC}"
mkdir -p lib

# Download Java-WebSocket
if [ ! -f "lib/Java-WebSocket-1.5.3.jar" ]; then
    echo -e "${YELLOW}Java-WebSocket library not found, downloading...${NC}"
    wget -q -O lib/Java-WebSocket-1.5.3.jar https://repo1.maven.org/maven2/org/java-websocket/Java-WebSocket/1.5.3/Java-WebSocket-1.5.3.jar
    if [ $? -ne 0 ]; then
        echo -e "${RED}Failed to download Java-WebSocket library!${NC}"
        exit 1
    fi
fi

# Download JSON library
if [ ! -f "lib/json-20230227.jar" ]; then
    echo -e "${YELLOW}JSON library not found, downloading...${NC}"
    wget -q -O lib/json-20230227.jar https://repo1.maven.org/maven2/org/json/json/20230227/json-20230227.jar
    if [ $? -ne 0 ]; then
        echo -e "${RED}Failed to download JSON library!${NC}"
        exit 1
    fi
fi

# Download SLF4J API (required by Java-WebSocket)
if [ ! -f "lib/slf4j-api-2.0.7.jar" ]; then
    echo -e "${YELLOW}SLF4J API library not found, downloading...${NC}"
    wget -q -O lib/slf4j-api-2.0.7.jar https://repo1.maven.org/maven2/org/slf4j/slf4j-api/2.0.7/slf4j-api-2.0.7.jar
    if [ $? -ne 0 ]; then
        echo -e "${RED}Failed to download SLF4J API library!${NC}"
        exit 1
    fi
fi

# Download SLF4J Simple (logging implementation)
if [ ! -f "lib/slf4j-simple-2.0.7.jar" ]; then
    echo -e "${YELLOW}SLF4J Simple library not found, downloading...${NC}"
    wget -q -O lib/slf4j-simple-2.0.7.jar https://repo1.maven.org/maven2/org/slf4j/slf4j-simple/2.0.7/slf4j-simple-2.0.7.jar
    if [ $? -ne 0 ]; then
        echo -e "${RED}Failed to download SLF4J Simple library!${NC}"
        exit 1
    fi
fi

echo -e "${GREEN}✓ Java dependencies OK${NC}"
echo ""

# Compile the adapter
echo -e "${BLUE}[2/5] Compiling MutexAdapter (${ADAPTER_PATH})...${NC}"
javac -cp ".:src:lib/Java-WebSocket-1.5.3.jar:lib/json-20230227.jar:lib/slf4j-api-2.0.7.jar" -d build src/com/uppaal/tron/Adapter.java src/com/uppaal/tron/Reporter.java "${ADAPTER_PATH}"
if [ $? -ne 0 ]; then
    echo -e "${RED}Failed to compile MutexAdapter!${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Compilation successful${NC}"
echo ""

# Function to log performance data to CSV
log_performance_data() {
    local server_pid=$1
    local adapter_pid=$2
    local tron_pid=$3

    # Create individual CSV files with headers
    echo "Timestamp,CPU%,Memory_MB,Memory%,Threads" > logs/server_performance.csv
    echo "Timestamp,CPU%,Memory_MB,Memory%,Threads" > logs/adapter_performance.csv
    echo "Timestamp,CPU%,Memory_MB,Memory%,Threads" > logs/tron_performance.csv

    # Create combined CSV file with all processes
    echo "Timestamp,Server_CPU%,Server_Memory_MB,Server_Threads,Adapter_CPU%,Adapter_Memory_MB,Adapter_Threads,TRON_CPU%,TRON_Memory_MB,TRON_Threads" > logs/combined_performance.csv

    while true; do
        # Get current timestamp
        timestamp=$(date '+%Y-%m-%d %H:%M:%S')

        # Initialize variables
        server_cpu="0" server_mem_mb="0" server_threads="0"
        adapter_cpu="0" adapter_mem_mb="0" adapter_threads="0"
        tron_cpu="0" tron_mem_mb="0" tron_threads="0"

        # Collect data for Server
        if ps -p $server_pid > /dev/null 2>&1; then
            server_data=$(ps -p $server_pid -o %cpu,%mem,rss,nlwp --no-headers 2>/dev/null)
            if [ -n "$server_data" ]; then
                server_cpu=$(echo $server_data | awk '{print $1}')
                mem_pct=$(echo $server_data | awk '{print $2}')
                server_mem_mb=$(echo $server_data | awk '{printf "%.2f", $3/1024}')
                server_threads=$(echo $server_data | awk '{print $4}')
                echo "$timestamp,$server_cpu,$server_mem_mb,$mem_pct,$server_threads" >> logs/server_performance.csv
            fi
        fi

        # Collect data for Adapter
        if ps -p $adapter_pid > /dev/null 2>&1; then
            adapter_data=$(ps -p $adapter_pid -o %cpu,%mem,rss,nlwp --no-headers 2>/dev/null)
            if [ -n "$adapter_data" ]; then
                adapter_cpu=$(echo $adapter_data | awk '{print $1}')
                mem_pct=$(echo $adapter_data | awk '{print $2}')
                adapter_mem_mb=$(echo $adapter_data | awk '{printf "%.2f", $3/1024}')
                adapter_threads=$(echo $adapter_data | awk '{print $4}')
                echo "$timestamp,$adapter_cpu,$adapter_mem_mb,$mem_pct,$adapter_threads" >> logs/adapter_performance.csv
            fi
        fi

        # Collect data for TRON
        if ps -p $tron_pid > /dev/null 2>&1; then
            tron_data=$(ps -p $tron_pid -o %cpu,%mem,rss,nlwp --no-headers 2>/dev/null)
            if [ -n "$tron_data" ]; then
                tron_cpu=$(echo $tron_data | awk '{print $1}')
                mem_pct=$(echo $tron_data | awk '{print $2}')
                tron_mem_mb=$(echo $tron_data | awk '{printf "%.2f", $3/1024}')
                tron_threads=$(echo $tron_data | awk '{print $4}')
                echo "$timestamp,$tron_cpu,$tron_mem_mb,$mem_pct,$tron_threads" >> logs/tron_performance.csv
            fi
        fi

        # Write combined data
        echo "$timestamp,$server_cpu,$server_mem_mb,$server_threads,$adapter_cpu,$adapter_mem_mb,$adapter_threads,$tron_cpu,$tron_mem_mb,$tron_threads" >> logs/combined_performance.csv

        # Sample every 1 second
        sleep 1
    done
}

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
echo -e "${BLUE}[3/5] Starting Python server (port 5000)...${NC}"
cd "${DUT_PATH}"
python3 main.py > ../../logs/server.log 2>&1 &
SERVER_PID=$!
cd ../..
echo "Python server PID: $SERVER_PID"
sleep 3  # Give the server time to start

# Check if server started successfully
if ! ps -p $SERVER_PID > /dev/null; then
    echo -e "${RED}Failed to start Python server!${NC}"
    echo "Check logs/server.log for details"
    exit 1
fi
echo -e "${GREEN}✓ Python server running${NC}"
echo ""

# 2. Start Java adapter
echo -e "${BLUE}[4/5] Starting MutexAdapter (port 9999)...${NC}"
# Set the correct base URL depending on the version
if [ "$VERSION" = "web" ]; then
    MUTEX_BASE_URL="http://localhost:5000"
else
    MUTEX_BASE_URL="ws://localhost:5000"
fi
java -DmutexServerBase="${MUTEX_BASE_URL}" -cp "build:lib/Java-WebSocket-1.5.3.jar:lib/json-20230227.jar:lib/slf4j-api-2.0.7.jar:lib/slf4j-simple-2.0.7.jar" mutex.MutexAdapter 9999 > logs/adapter.log 2>&1 &
ADAPTER_PID=$!
echo "Adapter PID: $ADAPTER_PID"
sleep 3  # Give the adapter time to start

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
echo -e "${BLUE}[5/5] Starting UPPAAL TRON...${NC}"
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
echo "Performance CSV files:"
echo -e "  ${SERVER_COLOR}Server:${NC}  logs/server_performance.csv"
echo -e "  ${ADAPTER_COLOR}Adapter:${NC} logs/adapter_performance.csv"
echo -e "  ${TRON_COLOR}TRON:${NC}    logs/tron_performance.csv"
echo -e "  ${CYAN}Combined:${NC} logs/combined_performance.csv (all processes in one file)"
echo ""
echo "WebSocket endpoint: ws://localhost:5000/ws"
echo "HTTP status page: http://localhost:5000/"
echo ""

# Start performance logging in background
echo -e "${BLUE}Starting performance data collection (1 second intervals)...${NC}"
log_performance_data "$SERVER_PID" "$ADAPTER_PID" "$TRON_PID" &
PERF_LOGGER_PID=$!
echo -e "${GREEN}✓ Performance logging active${NC}"
echo ""

echo -e "${YELLOW}Press Ctrl+C to stop all processes${NC}"
echo ""
echo "Performance data is being saved to CSV files for Excel plotting"
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
