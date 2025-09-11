from flask import Flask, request, jsonify
import threading
import time
from collections import deque
from datetime import datetime
import uuid

app = Flask(__name__)

# Global state
available = True
current_server_id = None
request_queue = deque()
queue_lock = threading.Lock()
availability_lock = threading.Lock()

# Store for tracking request status
request_status = {}

class QueueRequest:
    def __init__(self, server_id):
        self.server_id = server_id
        self.request_id = str(uuid.uuid4())
        self.timestamp = datetime.now()
        self.event = threading.Event()
        self.granted = False

@app.route('/request_service', methods=['POST'])
def request_service():
    """
    Endpoint for servers to request access to the service
    Expects JSON: {"server_id": "unique_server_identifier"}
    """
    global available, current_server_id

    data = request.get_json()
    if not data or 'server_id' not in data:
        return jsonify({"error": "server_id is required"}), 400

    server_id = data['server_id']

    with availability_lock:
        if available:
            # Service is available, grant immediately
            available = False
            current_server_id = server_id

            app.logger.info(f"Service granted immediately to server {server_id}")
            return jsonify({
                "status": "granted",
                "server_id": server_id,
                "message": "Service access granted immediately"
            }), 200
        else:
            # Service is busy, add to queue
            queue_request = QueueRequest(server_id)

            with queue_lock:
                request_queue.append(queue_request)
                request_status[queue_request.request_id] = queue_request
                queue_position = len(request_queue)

            app.logger.info(f"Server {server_id} added to queue at position {queue_position}")

            return jsonify({
                "status": "queued",
                "server_id": server_id,
                "request_id": queue_request.request_id,
                "queue_position": queue_position,
                "message": f"Service is busy. You are in position {queue_position} in the queue."
            }), 202

@app.route('/release_service', methods=['POST'])
def release_service():
    """
    Endpoint for servers to release the service when they're done
    Expects JSON: {"server_id": "unique_server_identifier"}
    """
    global available, current_server_id

    data = request.get_json()
    if not data or 'server_id' not in data:
        return jsonify({"error": "server_id is required"}), 400

    server_id = data['server_id']

    with availability_lock:
        if current_server_id != server_id:
            return jsonify({
                "error": f"Server {server_id} does not currently have access to the service"
            }), 403

        app.logger.info(f"Service released by server {server_id}")

        # Process the next request in queue
        next_request = None
        with queue_lock:
            if request_queue:
                next_request = request_queue.popleft()

        if next_request:
            # Grant access to next server in queue
            current_server_id = next_request.server_id
            next_request.granted = True
            next_request.event.set()

            app.logger.info(f"Service granted to next server in queue: {next_request.server_id}")

            return jsonify({
                "status": "released",
                "server_id": server_id,
                "next_server": next_request.server_id,
                "message": f"Service released and granted to next server: {next_request.server_id}"
            }), 200
        else:
            # No one in queue, service becomes available
            available = True
            current_server_id = None

            return jsonify({
                "status": "released",
                "server_id": server_id,
                "message": "Service released and is now available"
            }), 200

@app.route('/check_request_status/<request_id>', methods=['GET'])
def check_request_status(request_id):
    """
    Endpoint to check the status of a queued request
    """
    if request_id not in request_status:
        return jsonify({"error": "Request ID not found"}), 404

    queue_request = request_status[request_id]

    if queue_request.granted:
        # Clean up the request from status tracking
        del request_status[request_id]
        return jsonify({
            "status": "granted",
            "server_id": queue_request.server_id,
            "request_id": request_id,
            "message": "Service access has been granted"
        }), 200
    else:
        # Still in queue, calculate current position
        with queue_lock:
            position = 1
            for req in request_queue:
                if req.request_id == request_id:
                    break
                position += 1
            else:
                # Request not found in queue (shouldn't happen)
                return jsonify({"error": "Request not found in queue"}), 404

        return jsonify({
            "status": "queued",
            "server_id": queue_request.server_id,
            "request_id": request_id,
            "queue_position": position,
            "message": f"Still in queue at position {position}"
        }), 200

@app.route('/status', methods=['GET'])
def get_system_status():
    """
    Endpoint to get the current system status
    """
    with queue_lock:
        queue_length = len(request_queue)
        queue_info = [
            {
                "server_id": req.server_id,
                "request_id": req.request_id,
                "timestamp": req.timestamp.isoformat()
            }
            for req in request_queue
        ]

    return jsonify({
        "available": available,
        "current_server": current_server_id,
        "queue_length": queue_length,
        "queue": queue_info
    }), 200

@app.route('/wait_for_access/<request_id>', methods=['GET'])
def wait_for_access(request_id):
    """
    Blocking endpoint that waits until the request is granted
    This allows servers to wait instead of polling
    """
    if request_id not in request_status:
        return jsonify({"error": "Request ID not found"}), 404

    queue_request = request_status[request_id]

    # Wait for the event to be set (with timeout)
    if queue_request.event.wait(timeout=300):  # 5 minute timeout
        if queue_request.granted:
            # Clean up the request from status tracking
            del request_status[request_id]
            return jsonify({
                "status": "granted",
                "server_id": queue_request.server_id,
                "request_id": request_id,
                "message": "Service access has been granted"
            }), 200
        else:
            return jsonify({
                "status": "error",
                "message": "Request was processed but not granted"
            }), 500
    else:
        return jsonify({
            "status": "timeout",
            "message": "Request timed out after 5 minutes"
        }), 408

@app.route('/health', methods=['GET'])
def health_check():
    """
    Simple health check endpoint
    """
    return jsonify({"status": "healthy", "timestamp": datetime.now().isoformat()}), 200

if __name__ == '__main__':
    app.run(debug=True, host='0.0.0.0', port=5000)
