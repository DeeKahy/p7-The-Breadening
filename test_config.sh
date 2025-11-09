#!/bin/bash
# Test script to validate config loading
# Run this inside the distrobox: distrobox enter breadening

echo "=== Testing Config Loading ==="
echo ""

# Default config file
CONFIG_FILE="${CONFIG_FILE:-config.json}"

# Function to load config value using jq
get_config() {
    local path="$1"
    local default="$2"

    if [ ! -f "$CONFIG_FILE" ]; then
        echo "$default"
        return
    fi

    local value=$(jq -r "$path // \"$default\"" "$CONFIG_FILE" 2>/dev/null)
    if [ $? -ne 0 ] || [ -z "$value" ] || [ "$value" = "null" ]; then
        echo "$default"
    else
        echo "$value"
    fi
}

# Check if jq is installed
if ! command -v jq &> /dev/null; then
    echo "❌ Error: jq is not installed."
    echo "Run: sudo apt-get install jq"
    exit 1
fi

echo "✓ jq is installed"
echo ""

# Check if config file exists
if [ ! -f "$CONFIG_FILE" ]; then
    echo "❌ Config file not found: $CONFIG_FILE"
    exit 1
fi

echo "✓ Config file found: $CONFIG_FILE"
echo ""

# Load and display configuration
echo "Loading configuration values:"
echo "----------------------------"
echo "DUT Project Path:    $(get_config ".dut.project_path" "dut")"
echo "DUT Main File:       $(get_config ".dut.main_file" "main.py")"
echo "DUT Python Command:  $(get_config ".dut.python_cmd" "python3")"
echo ""
echo "UPPAAL Model:        $(get_config ".uppaal.model_file" "src/mutex/centralized_mutex.xml")"
echo "UPPAAL TRON Path:    $(get_config ".uppaal.tron_path" "../uppaal-tron-1.5-linux/tron")"
echo "UPPAAL Options -u:   $(get_config ".uppaal.options.u" "4000,4000")"
echo "UPPAAL Options -P:   $(get_config ".uppaal.options.P" "10,200")"
echo "UPPAAL Options -F:   $(get_config ".uppaal.options.F" "300")"
echo "UPPAAL Options -I:   $(get_config ".uppaal.options.I" "SocketAdapter")"
echo "UPPAAL Options -v:   $(get_config ".uppaal.options.v" "9")"
echo ""
echo "Adapter Port:        $(get_config ".adapter.port" "9999")"
echo "Adapter Server Base: $(get_config ".adapter.server_base" "http://localhost:5000")"
echo ""
echo "Server Port:         $(get_config ".server.port" "5000")"
echo ""
echo "✓ All configuration values loaded successfully!"
