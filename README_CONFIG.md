# Configuration File Guide

## Overview
The `run_inside.sh` script now supports loading configuration from a JSON file. This allows you to easily switch between different test scenarios without modifying the script.

## Usage

### Default Configuration
By default, the script looks for `config.json` in the project root:
```bash
./run_inside.sh
```

### Custom Configuration File
You can specify a different config file using the `CONFIG_FILE` environment variable:
```bash
CONFIG_FILE=config.custom.json ./run_inside.sh
```

## Configuration Options

### DUT (Device Under Test) Settings
```json
"dut": {
  "project_path": "dut",        // Path to the DUT project directory
  "main_file": "main.py",       // Main Python file to run
  "python_cmd": "python3"       // Python command to use
}
```

### UPPAAL TRON Settings
```json
"uppaal": {
  "model_file": "src/mutex/centralized_mutex.xml",  // Path to UPPAAL model file
  "tron_path": "../uppaal-tron-1.5-linux/tron",     // Path to TRON executable
  "options": {
    "u": "4000,4000",           // Time unit and steps
    "P": "10,200",              // Random delays
    "F": "300",                 // Max test length
    "I": "SocketAdapter",       // Interface type
    "v": "9"                    // Verbosity level
  }
}
```

### Adapter Settings
```json
"adapter": {
  "port": "9999",                           // Port for adapter to listen on
  "server_base": "http://localhost:5000"    // Base URL for DUT server
}
```

### Server Settings
```json
"server": {
  "port": "5000"              // Port for Flask server
}
```

## Example Configurations

### config.json (default)
The standard configuration with default values.

### config.example.json
A template showing all available options with comments.

## Creating Custom Configurations

1. Copy `config.example.json` to a new file (e.g., `config.mytest.json`)
2. Modify the values as needed
3. Run with: `CONFIG_FILE=config.mytest.json ./run_inside.sh`

## Requirements

The script requires `jq` to be installed for JSON parsing:
```bash
sudo apt-get install jq
```

## Fallback Behavior

If a config file is not found or a specific value is missing, the script will use default values that match the original hardcoded behavior.
