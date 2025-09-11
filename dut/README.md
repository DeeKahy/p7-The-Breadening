# Flask Queueing System

A Flask-based queueing system that manages server availability and queues incoming requests when a service is busy.

## Overview

This system implements a simple queueing mechanism where:
1. Servers request access to a shared service
2. If the service is available, access is granted immediately
3. If the service is busy, the request is queued
4. When the current server releases the service, the next server in queue gets access automatically
5. Servers can either poll for status or use blocking waits

## Files

- `queue_server.py` - Main Flask application with the queueing logic
- `example_client.py` - Example client demonstrating how to use the system
- `requirements.txt` - Python dependencies

## Installation

1. Install dependencies:
```bash
pip install -r requirements.txt
```

2. Run the server:
```bash
python queue_server.py
```

The server will start on `http://localhost:5000`

## API Endpoints

### POST `/request_service`
Request access to the service.

**Request body:**
```json
{
  "server_id": "unique_server_identifier"
}
```

**Response (immediate access):**
```json
{
  "status": "granted",
  "server_id": "server_001",
  "message": "Service access granted immediately"
}
```

**Response (queued):**
```json
{
  "status": "queued",
  "server_id": "server_001",
  "request_id": "uuid-string",
  "queue_position": 2,
  "message": "Service is busy. You are in position 2 in the queue."
}
```

### POST `/release_service`
Release the service when done.

**Request body:**
```json
{
  "server_id": "unique_server_identifier"
}
```

**Response:**
```json
{
  "status": "released",
  "server_id": "server_001",
  "next_server": "server_002",
  "message": "Service released and granted to next server: server_002"
}
```

### GET `/check_request_status/<request_id>`
Check the status of a queued request.

**Response (still queued):**
```json
{
  "status": "queued",
  "server_id": "server_001",
  "request_id": "uuid-string",
  "queue_position": 1,
  "message": "Still in queue at position 1"
}
```

**Response (granted):**
```json
{
  "status": "granted",
  "server_id": "server_001",
  "request_id": "uuid-string",
  "message": "Service access has been granted"
}
```

### GET `/wait_for_access/<request_id>`
Blocking endpoint that waits until access is granted (5-minute timeout).

### GET `/status`
Get current system status including queue information.

### GET `/health`
Health check endpoint.

## Usage Examples

### Basic Usage Pattern

1. **Request Service Access:**
```python
import requests

response = requests.post('http://localhost:5000/request_service', 
                        json={'server_id': 'my_server'})
result = response.json()

if result['status'] == 'granted':
    # You have immediate access
    print("Access granted immediately!")
elif result['status'] == 'queued':
    # You're in the queue
    request_id = result['request_id']
    print(f"Queued at position {result['queue_position']}")
```

2. **Wait for Access (Polling Method):**
```python
while True:
    time.sleep(2)  # Poll every 2 seconds
    response = requests.get(f'http://localhost:5000/check_request_status/{request_id}')
    result = response.json()
    
    if result['status'] == 'granted':
        print("Access granted!")
        break
    else:
        print(f"Still waiting at position {result['queue_position']}")
```

3. **Wait for Access (Blocking Method):**
```python
response = requests.get(f'http://localhost:5000/wait_for_access/{request_id}')
result = response.json()

if result['status'] == 'granted':
    print("Access granted!")
```

4. **Release Service:**
```python
# When done with your work
response = requests.post('http://localhost:5000/release_service',
                        json={'server_id': 'my_server'})
print("Service released")
```

### Running the Example

Run the example client to see the system in action:

```bash
python example_client.py
```

This will simulate multiple servers requesting access concurrently and demonstrate both polling and blocking wait methods.

## System Behavior

- **Thread-safe**: Uses locks to ensure thread safety
- **FIFO Queue**: Requests are processed in first-in-first-out order
- **Automatic Handoff**: When a server releases the service, the next server in queue automatically gets access
- **Request Tracking**: Each queued request gets a unique ID for status checking
- **Timeout Protection**: Blocking waits have a 5-minute timeout to prevent indefinite blocking

## Architecture

The system maintains:
- A boolean `available` flag indicating service availability
- A `current_server_id` tracking which server currently has access
- A FIFO queue of pending requests
- Thread locks for concurrent access safety
- Request status tracking for polling support

## Error Handling

- Returns appropriate HTTP status codes
- Validates server IDs for release requests
- Handles missing or invalid request parameters
- Provides descriptive error messages

## Production Considerations

For production use, consider:
- Adding authentication/authorization
- Implementing request persistence (database)
- Adding monitoring and metrics
- Configuring proper logging
- Setting up load balancing for multiple queue server instances
- Adding request expiration/cleanup mechanisms