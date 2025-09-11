import requests
import json
import time
import threading
from concurrent.futures import ThreadPoolExecutor

class QueueClient:
    def __init__(self, base_url="http://localhost:5000"):
        self.base_url = base_url
        self.server_id = None

    def request_service(self, server_id):
        """Request access to the service"""
        url = f"{self.base_url}/request_service"
        data = {"server_id": server_id}

        try:
            response = requests.post(url, json=data)
            return response.json(), response.status_code
        except requests.exceptions.RequestException as e:
            return {"error": str(e)}, 500

    def release_service(self, server_id):
        """Release the service when done"""
        url = f"{self.base_url}/release_service"
        data = {"server_id": server_id}

        try:
            response = requests.post(url, json=data)
            return response.json(), response.status_code
        except requests.exceptions.RequestException as e:
            return {"error": str(e)}, 500

    def check_request_status(self, request_id):
        """Check the status of a queued request"""
        url = f"{self.base_url}/check_request_status/{request_id}"

        try:
            response = requests.get(url)
            return response.json(), response.status_code
        except requests.exceptions.RequestException as e:
            return {"error": str(e)}, 500

    def wait_for_access(self, request_id):
        """Wait for access to be granted (blocking call)"""
        url = f"{self.base_url}/wait_for_access/{request_id}"

        try:
            response = requests.get(url)
            return response.json(), response.status_code
        except requests.exceptions.RequestException as e:
            return {"error": str(e)}, 500

    def get_system_status(self):
        """Get current system status"""
        url = f"{self.base_url}/status"

        try:
            response = requests.get(url)
            return response.json(), response.status_code
        except requests.exceptions.RequestException as e:
            return {"error": str(e)}, 500

def simulate_server_work(server_id, work_duration=5):
    """Simulate a server doing work for a certain duration"""
    print(f"[{server_id}] Starting work simulation...")
    client = QueueClient()

    # Request service access
    print(f"[{server_id}] Requesting service access...")
    response, status_code = client.request_service(server_id)
    print(f"[{server_id}] Request response: {response}")

    if status_code == 200 and response.get("status") == "granted":
        # Access granted immediately
        print(f"[{server_id}] Access granted immediately! Working for {work_duration} seconds...")
        time.sleep(work_duration)

        # Release the service
        print(f"[{server_id}] Work completed. Releasing service...")
        release_response, _ = client.release_service(server_id)
        print(f"[{server_id}] Release response: {release_response}")

    elif status_code == 202 and response.get("status") == "queued":
        # Added to queue
        request_id = response.get("request_id")
        queue_position = response.get("queue_position")
        print(f"[{server_id}] Added to queue at position {queue_position}. Request ID: {request_id}")

        # Option 1: Poll for status
        print(f"[{server_id}] Polling for access...")
        while True:
            time.sleep(2)  # Poll every 2 seconds
            status_response, status_code = client.check_request_status(request_id)

            if status_code == 200 and status_response.get("status") == "granted":
                print(f"[{server_id}] Access granted! Working for {work_duration} seconds...")
                time.sleep(work_duration)

                # Release the service
                print(f"[{server_id}] Work completed. Releasing service...")
                release_response, _ = client.release_service(server_id)
                print(f"[{server_id}] Release response: {release_response}")
                break
            else:
                position = status_response.get("queue_position", "unknown")
                print(f"[{server_id}] Still waiting... Position in queue: {position}")
    else:
        print(f"[{server_id}] Error requesting service: {response}")

def simulate_server_work_blocking(server_id, work_duration=5):
    """Simulate a server using the blocking wait endpoint"""
    print(f"[{server_id}] Starting work simulation (blocking mode)...")
    client = QueueClient()

    # Request service access
    print(f"[{server_id}] Requesting service access...")
    response, status_code = client.request_service(server_id)
    print(f"[{server_id}] Request response: {response}")

    if status_code == 200 and response.get("status") == "granted":
        # Access granted immediately
        print(f"[{server_id}] Access granted immediately! Working for {work_duration} seconds...")
        time.sleep(work_duration)

        # Release the service
        print(f"[{server_id}] Work completed. Releasing service...")
        release_response, _ = client.release_service(server_id)
        print(f"[{server_id}] Release response: {release_response}")

    elif status_code == 202 and response.get("status") == "queued":
        # Added to queue, use blocking wait
        request_id = response.get("request_id")
        print(f"[{server_id}] Waiting for access (blocking)... Request ID: {request_id}")

        wait_response, wait_status = client.wait_for_access(request_id)

        if wait_status == 200 and wait_response.get("status") == "granted":
            print(f"[{server_id}] Access granted! Working for {work_duration} seconds...")
            time.sleep(work_duration)

            # Release the service
            print(f"[{server_id}] Work completed. Releasing service...")
            release_response, _ = client.release_service(server_id)
            print(f"[{server_id}] Release response: {release_response}")
        else:
            print(f"[{server_id}] Failed to get access: {wait_response}")
    else:
        print(f"[{server_id}] Error requesting service: {response}")

def main():
    print("=== Queue System Demo ===\n")

    client = QueueClient()

    # Check initial system status
    print("Initial system status:")
    status, _ = client.get_system_status()
    print(json.dumps(status, indent=2))
    print()

    # Simulate multiple servers requesting access concurrently
    print("Starting concurrent server simulations...")

    with ThreadPoolExecutor(max_workers=5) as executor:
        # Start multiple servers with different work durations
        futures = []

        # Using polling method
        futures.append(executor.submit(simulate_server_work, "server_001", 3))
        futures.append(executor.submit(simulate_server_work, "server_002", 4))
        futures.append(executor.submit(simulate_server_work, "server_003", 2))

        # Using blocking method
        futures.append(executor.submit(simulate_server_work_blocking, "server_004", 3))
        futures.append(executor.submit(simulate_server_work_blocking, "server_005", 2))

        # Wait for all servers to complete
        for future in futures:
            future.result()

    print("\n=== Demo completed ===")

    # Final system status
    print("Final system status:")
    status, _ = client.get_system_status()
    print(json.dumps(status, indent=2))

if __name__ == "__main__":
    main()
