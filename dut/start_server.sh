#!/bin/bash

# Flask Queue Server Startup Script

echo "Starting Flask Queue Server..."

# Check if Python is available
if ! command -v python3 &> /dev/null; then
    if ! command -v python &> /dev/null; then
        echo "Error: Python is not installed or not in PATH"
        exit 1
    else
        PYTHON_CMD="python"
    fi
else
    PYTHON_CMD="python3"
fi

# Check if requirements are installed
if ! $PYTHON_CMD -c "import flask" &> /dev/null; then
    echo "Installing requirements..."
    $PYTHON_CMD -m pip install -r requirements.txt
    if [ $? -ne 0 ]; then
        echo "Error: Failed to install requirements"
        exit 1
    fi
fi

# Set environment variables
export FLASK_APP=queue_server.py
export FLASK_ENV=development

echo "Starting server on http://localhost:5000"
echo "Press Ctrl+C to stop the server"
echo "---"

# Start the server
$PYTHON_CMD queue_server.py
