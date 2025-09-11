import unittest
import requests
import json
import time
import threading
from concurrent.futures import ThreadPoolExecutor
import subprocess
import sys
import os
import signal

class TestQueueSystem(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        """Start the Flask server before running tests"""
        cls.base_url = "http://localhost:5000"
        cls.server_process = None

        # Start the Flask server in a separate process
        try:
            cls.server_process = subprocess.Popen(
                [sys.executable, "queue_server.py"],
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                cwd=os.path.dirname(os.path.abspath(__file__))
            )

            # Wait for server to start
            max_retries = 10
            for i in range(max_retries):
                try:
                    response = requests.get(f"{cls.base_url}/health", timeout=2)
                    if response.status_code == 200:
                        print("Server started successfully")
                        break
                except requests.exceptions.RequestException:
                    if i < max_retries - 1:
                        time.sleep(1)
                        continue
                    else:
                        raise Exception("Failed to start server")
        except Exception as e:
            print(f"Error starting server: {e}")
            raise

    @classmethod
    def tearDownClass(cls):
        """Stop the Flask server after tests"""
        if cls.server_process:
            cls.server_process.terminate()
            cls.server_process.wait()

    def setUp(self):
        """Reset system state before each test"""
        # Clear any existing state by checking status and releasing if needed
        try:
            response = requests.get(f"{self.base_url}/status")
            if response.status_code == 200:
                status = response.json()
                if not status.get("available") and status.get("current_server"):
                    # Try to release the service
                    requests.post(f"{self.base_url}/release_service",
                                json={"server_id": status["current_server"]})
        except:
            pass

    def test_health_check(self):
        """Test the health check endpoint"""
        response = requests.get(f"{self.base_url}/health")
        self.assertEqual(response.status_code, 200)
        data = response.json()
        self.assertEqual(data["status"], "healthy")
        self.assertIn("timestamp", data)

    def test_system_status(self):
        """Test the system status endpoint"""
        response = requests.get(f"{self.base_url}/status")
        self.assertEqual(response.status_code, 200)
        data = response.json()

        # Check required fields
        self.assertIn("available", data)
        self.assertIn("current_server", data)
        self.assertIn("queue_length", data)
        self.assertIn("queue", data)
        self.assertIsInstance(data["available"], bool)
        self.assertIsInstance(data["queue_length"], int)
        self.assertIsInstance(data["queue"], list)

    def test_immediate_access(self):
        """Test immediate service access when available"""
        server_id = "test_server_001"

        # Request service
        response = requests.post(f"{self.base_url}/request_service",
                               json={"server_id": server_id})
        self.assertEqual(response.status_code, 200)
        data = response.json()
        self.assertEqual(data["status"], "granted")
        self.assertEqual(data["server_id"], server_id)

        # Check system status
        status_response = requests.get(f"{self.base_url}/status")
        status_data = status_response.json()
        self.assertFalse(status_data["available"])
        self.assertEqual(status_data["current_server"], server_id)

        # Release service
        release_response = requests.post(f"{self.base_url}/release_service",
                                       json={"server_id": server_id})
        self.assertEqual(release_response.status_code, 200)
        release_data = release_response.json()
        self.assertEqual(release_data["status"], "released")

    def test_queueing_system(self):
        """Test the queueing system with multiple requests"""
        server1 = "test_server_001"
        server2 = "test_server_002"
        server3 = "test_server_003"

        # First request should get immediate access
        response1 = requests.post(f"{self.base_url}/request_service",
                                json={"server_id": server1})
        self.assertEqual(response1.status_code, 200)
        self.assertEqual(response1.json()["status"], "granted")

        # Second request should be queued
        response2 = requests.post(f"{self.base_url}/request_service",
                                json={"server_id": server2})
        self.assertEqual(response2.status_code, 202)
        data2 = response2.json()
        self.assertEqual(data2["status"], "queued")
        self.assertEqual(data2["queue_position"], 1)
        self.assertIn("request_id", data2)
        request_id2 = data2["request_id"]

        # Third request should also be queued
        response3 = requests.post(f"{self.base_url}/request_service",
                                json={"server_id": server3})
        self.assertEqual(response3.status_code, 202)
        data3 = response3.json()
        self.assertEqual(data3["status"], "queued")
        self.assertEqual(data3["queue_position"], 2)

        # Check queue status
        status_response = requests.get(f"{self.base_url}/status")
        status_data = status_response.json()
        self.assertFalse(status_data["available"])
        self.assertEqual(status_data["current_server"], server1)
        self.assertEqual(status_data["queue_length"], 2)

        # Release first server
        release_response = requests.post(f"{self.base_url}/release_service",
                                       json={"server_id": server1})
        self.assertEqual(release_response.status_code, 200)
        release_data = release_response.json()
        self.assertEqual(release_data["next_server"], server2)

        # Check that second server now has access
        time.sleep(0.1)  # Small delay for processing
        status_check = requests.get(f"{self.base_url}/check_request_status/{request_id2}")
        self.assertEqual(status_check.status_code, 200)
        status_data = status_check.json()
        self.assertEqual(status_data["status"], "granted")

        # Release second server
        requests.post(f"{self.base_url}/release_service",
                     json={"server_id": server2})

        # Release third server (should get access automatically)
        time.sleep(0.1)
        requests.post(f"{self.base_url}/release_service",
                     json={"server_id": server3})

    def test_invalid_requests(self):
        """Test error handling for invalid requests"""
        # Missing server_id in request
        response = requests.post(f"{self.base_url}/request_service", json={})
        self.assertEqual(response.status_code, 400)

        # Invalid release request
        response = requests.post(f"{self.base_url}/release_service",
                               json={"server_id": "non_existent_server"})
        self.assertEqual(response.status_code, 403)

        # Invalid request ID
        response = requests.get(f"{self.base_url}/check_request_status/invalid_id")
        self.assertEqual(response.status_code, 404)

    def test_concurrent_requests(self):
        """Test system behavior with concurrent requests"""
        def make_request(server_id):
            response = requests.post(f"{self.base_url}/request_service",
                                   json={"server_id": server_id})
            return response.json(), response.status_code

        # Make multiple concurrent requests
        server_ids = [f"concurrent_server_{i:03d}" for i in range(5)]

        with ThreadPoolExecutor(max_workers=5) as executor:
            futures = [executor.submit(make_request, server_id)
                      for server_id in server_ids]
            results = [future.result() for future in futures]

        # One should get immediate access, others should be queued
        granted_count = sum(1 for result, status in results
                           if status == 200 and result.get("status") == "granted")
        queued_count = sum(1 for result, status in results
                          if status == 202 and result.get("status") == "queued")

        self.assertEqual(granted_count, 1)
        self.assertEqual(queued_count, 4)

        # Clean up - find which server got access and release it
        for result, status in results:
            if status == 200 and result.get("status") == "granted":
                requests.post(f"{self.base_url}/release_service",
                            json={"server_id": result["server_id"]})
                break

    def test_status_polling(self):
        """Test status polling functionality"""
        server1 = "polling_server_001"
        server2 = "polling_server_002"

        # First server gets access
        requests.post(f"{self.base_url}/request_service",
                     json={"server_id": server1})

        # Second server gets queued
        response2 = requests.post(f"{self.base_url}/request_service",
                                json={"server_id": server2})
        request_id = response2.json()["request_id"]

        # Check initial status
        status_response = requests.get(f"{self.base_url}/check_request_status/{request_id}")
        self.assertEqual(status_response.status_code, 200)
        status_data = status_response.json()
        self.assertEqual(status_data["status"], "queued")
        self.assertEqual(status_data["queue_position"], 1)

        # Release first server
        requests.post(f"{self.base_url}/release_service",
                     json={"server_id": server1})

        # Check status again - should be granted
        time.sleep(0.1)
        status_response = requests.get(f"{self.base_url}/check_request_status/{request_id}")
        self.assertEqual(status_response.status_code, 200)
        status_data = status_response.json()
        self.assertEqual(status_data["status"], "granted")

        # Clean up
        requests.post(f"{self.base_url}/release_service",
                     json={"server_id": server2})

if __name__ == "__main__":
    # Run tests
    unittest.main(verbosity=2)
