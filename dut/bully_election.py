import threading
import time
from enum import Enum
from typing import Any, Dict, List, Optional

import requests
from flask import Flask, jsonify


class MessageType(Enum):
    ELECTION = "ELECTION"
    OK = "OK"
    COORDINATOR = "COORDINATOR"


class Message:
    def __init__(
        self,
        msg_type: MessageType,
        sender_id: int,
        payload: Optional[Dict[str, Any]] = None,
    ):
        self.type = msg_type
        self.sender_id = sender_id
        self.payload = payload or {}

    def to_dict(self) -> Dict[str, Any]:
        return {
            "type": self.type.value,
            "sender": int(self.sender_id),
            "payload": self.payload,
        }

    @staticmethod
    def from_dict(d: Dict[str, Any]) -> "Message":
        """Create message from dictionary."""
        msg_type = MessageType(d["type"])
        return Message(msg_type, int(d["sender"]), d.get("payload", {}))

    def __repr__(self) -> str:
        return f"Message({self.type}, from={self.sender_id}, payload={self.payload})"


class AdapterInterface:
    def send(
        self, receiver_id: int, message_dict: Dict[str, Any]
    ) -> Optional[Dict[str, Any]]:
        raise NotImplementedError(
            "Adapter must implement send(receiver_id, message_dict)"
        )

    def broadcast(self, message_dict: Dict[str, Any]) -> List[Dict[str, Any]]:
        raise NotImplementedError("Adapter must implement broadcast(message_dict)")


# Global list of known client IDs
# These represent the UPPAAL/Tron clients that this election process will communicate with
KNOWN_CLIENTS: List[int] = []


class BullyElectionProcess:
    def __init__(self, process_id: int, adapter: Optional[AdapterInterface] = None):
        self.id = int(process_id)
        self.is_alive = True
        self.coordinator_id: Optional[int] = None
        self.in_election = False

        # Communication adapter (to be implemented for UPPAAL/Tron)
        self.adapter = adapter

        # Message queue for incoming messages from clients
        self.message_queue: List[Message] = []
        self.lock = threading.Lock()

        # Event for waiting on election responses
        self.election_event = threading.Event()
        self.last_ok_from: Optional[int] = None

        # Start worker thread to process incoming messages
        self._worker_thread = threading.Thread(target=self._message_loop, daemon=True)
        self._worker_thread.start()

    def set_adapter(self, adapter: AdapterInterface):
        self.adapter = adapter

    # Message queue operations
    def receive_message(self, message: Message):
        with self.lock:
            self.message_queue.append(message)

    def inject_message(self, message_dict: Dict[str, Any]):
        try:
            msg = Message.from_dict(message_dict)
            self.receive_message(msg)
        except Exception as e:
            print(f"[Process {self.id}] Failed to inject message: {e}")

    def _pop_message(self) -> Optional[Message]:
        with self.lock:
            if not self.message_queue:
                return None
            return self.message_queue.pop(0)

    def _message_loop(self):
        while True:
            if not self.is_alive:
                time.sleep(0.05)
                continue

            msg = self._pop_message()
            if msg is None:
                time.sleep(0.01)
                continue

            # Handle different message types
            if msg.type == MessageType.ELECTION:
                self._handle_election_message(msg)
            elif msg.type == MessageType.OK:
                self._handle_ok_message(msg)
            elif msg.type == MessageType.COORDINATOR:
                self._handle_coordinator_message(msg)

    def _handle_election_message(self, message: Message):
        print(f"[Process {self.id}] Received ELECTION from {message.sender_id}")

        # Send OK response
        ok_msg = Message(MessageType.OK, self.id)
        self._send_message(message.sender_id, ok_msg)

        # If sender has lower ID and we're not in election, start our own election
        if not self.in_election and message.sender_id < self.id:
            threading.Thread(target=self.start_election, daemon=True).start()

    def _handle_ok_message(self, message: Message):
        if not self.is_alive:
            return
        print(f"[Process {self.id}] Received OK from {message.sender_id}")
        self.last_ok_from = message.sender_id
        self.in_election = False
        self.election_event.set()

    def _handle_coordinator_message(self, message: Message):
        if not self.is_alive:
            return
        print(
            f"[Process {self.id}] Process {message.sender_id} announced as COORDINATOR"
        )
        self.coordinator_id = message.sender_id
        self.in_election = False
        self.election_event.set()

    def _send_message(self, receiver_id: int, message: Message) -> Optional[Message]:
        if self.adapter is None:
            print(
                f"[Process {self.id}] No adapter configured; cannot send to {receiver_id}"
            )
            return None

        if receiver_id not in KNOWN_CLIENTS:
            print(
                f"[Process {self.id}] Unknown client {receiver_id}; not in KNOWN_CLIENTS"
            )
            return None

        msg_dict = message.to_dict()
        try:
            reply_dict = self.adapter.send(receiver_id, msg_dict)
            if reply_dict:
                return Message.from_dict(reply_dict)
        except Exception as e:
            print(f"[Process {self.id}] Adapter send failed: {e}")

        return None

    def _broadcast_message(self, message: Message) -> List[Message]:
        if self.adapter is None:
            print(f"[Process {self.id}] No adapter configured; cannot broadcast")
            return []

        msg_dict = message.to_dict()
        try:
            replies = self.adapter.broadcast(msg_dict)
            return [Message.from_dict(r) for r in replies if r]
        except Exception as e:
            print(f"[Process {self.id}] Adapter broadcast failed: {e}")
            return []

    def start_election(self) -> Optional[int]:
        if not self.is_alive:
            return None

        print(f"[Process {self.id}] Starting election")
        self.in_election = True
        self.last_ok_from = None
        self.election_event.clear()

        # Find clients with higher IDs
        higher_ids = [cid for cid in KNOWN_CLIENTS if cid > self.id]

        if not higher_ids:
            # No higher IDs -> become coordinator immediately
            self._become_coordinator()
            return self.id

        # Send ELECTION to all higher-ID clients
        responses: List[Message] = []
        for client_id in higher_ids:
            print(f"[Process {self.id}] Sending ELECTION to client {client_id}")
            election_msg = Message(MessageType.ELECTION, self.id)
            resp = self._send_message(client_id, election_msg)
            if resp and resp.type == MessageType.OK:
                responses.append(resp)
                self.last_ok_from = resp.sender_id
                self.election_event.set()

        if responses:
            # Got synchronous OK responses, wait for coordinator announcement
            print(
                f"[Process {self.id}] Got OK from {[r.sender_id for r in responses]}; waiting for COORDINATOR"
            )
            self.in_election = False
            waited = self.election_event.wait(timeout=2.0)

            if self.coordinator_id is not None:
                return self.coordinator_id

            if not waited:
                # Timeout - retry election
                print(f"[Process {self.id}] Timeout waiting for COORDINATOR; retrying")
                return self.start_election()

            return self.coordinator_id

        # No synchronous OKs; wait briefly for async OKs
        got_ok = self.election_event.wait(timeout=0.25)
        if got_ok and self.last_ok_from is not None:
            print(
                f"[Process {self.id}] Got async OK from {self.last_ok_from}; waiting for COORDINATOR"
            )
            self.in_election = False
            waited = self.election_event.wait(timeout=2.0)

            if self.coordinator_id is not None:
                return self.coordinator_id

            if not waited:
                print(f"[Process {self.id}] Timeout waiting for COORDINATOR; retrying")
                return self.start_election()

            return self.coordinator_id

        # No OKs at all -> become coordinator
        self._become_coordinator()
        return self.id

    def _become_coordinator(self):
        if not self.is_alive:
            return

        print(f"[Process {self.id}] I am the new COORDINATOR")
        self.coordinator_id = self.id
        self.in_election = False

        # Announce to all clients
        coordinator_msg = Message(MessageType.COORDINATOR, self.id)
        for client_id in KNOWN_CLIENTS:
            self._send_message(client_id, coordinator_msg)

    def get_coordinator(self) -> Optional[int]:
        return self.coordinator_id

    def is_coordinator(self) -> bool:
        return self.coordinator_id == self.id


def set_known_clients(client_ids: List[int]):
    global KNOWN_CLIENTS
    KNOWN_CLIENTS = [int(cid) for cid in client_ids]
    print(f"Set KNOWN_CLIENTS to: {KNOWN_CLIENTS}")


# Global Flask app for receiving messages from UPPAAL/Tron
app = Flask(__name__)

# Global process instance (set via run_server)
_global_process: Optional[BullyElectionProcess] = None

# Server configuration
SERVER_BASE_URL = "http://localhost:5000"
REQUEST_TIMEOUT = 3.0


def send_http_message(
    receiver_id: int, message_dict: Dict[str, Any]
) -> Optional[Dict[str, Any]]:
    msg_type = message_dict.get("type")
    if msg_type is None:
        print("[HTTP] Message missing 'type' field")
        return None

    try:
        # Construct endpoint based on message type
        # Pattern: /api/{message_type}/{receiver_id}
        endpoint = f"{SERVER_BASE_URL}/api/{msg_type.lower()}/{receiver_id}"

        # Send message with JSON payload
        response = requests.post(endpoint, json=message_dict, timeout=REQUEST_TIMEOUT)

        status = response.status_code
        print(f"[HTTP] Sent {msg_type} to client {receiver_id}, status: {status}")

        # Handle response based on status code (similar to Java adapter)
        if status == 200:
            # Synchronous response available
            try:
                return response.json()
            except Exception:
                return None
        elif status == 202:
            # Accepted, will respond asynchronously
            print(f"[HTTP] Client {receiver_id} will respond asynchronously")
            return None
        else:
            print(f"[HTTP] Unexpected status {status} for client {receiver_id}")
            return None

    except requests.exceptions.Timeout:
        print(f"[HTTP] Request to client {receiver_id} timed out")
        return None
    except requests.exceptions.ConnectionError:
        print(f"[HTTP] Connection failed to client {receiver_id}")
        return None
    except Exception as e:
        print(f"[HTTP] Error sending to client {receiver_id}: {e}")
        return None


# -------------------------
# Flask endpoints for receiving messages from UPPAAL/Tron
# -------------------------


@app.route("/api/election/<int:sender_id>", methods=["POST"])
def receive_election(sender_id: int):
    """
    Receive ELECTION message from a UPPAAL/Tron client.
    This is called when a UPPAAL model sends election(id) via Tron adapter.
    """
    if _global_process is None:
        return jsonify({"error": "Process not initialized"}), 500

    print(f"[FLASK] Received ELECTION request from client {sender_id}")

    # Inject the ELECTION message into the process
    message_dict = {"type": "ELECTION", "sender": sender_id, "payload": {}}
    _global_process.inject_message(message_dict)

    # Respond with OK (synchronous response pattern)
    # The process will also send OK via HTTP, but this is for immediate feedback
    ok_response = {"type": "OK", "sender": _global_process.id, "payload": {}}
    return jsonify(ok_response), 200


@app.route("/api/ok/<int:sender_id>", methods=["POST"])
def receive_ok(sender_id: int):
    """
    Receive OK message from a UPPAAL/Tron client.
    This is the response to an ELECTION message we sent.
    """
    if _global_process is None:
        return jsonify({"error": "Process not initialized"}), 500

    print(f"[FLASK] Received OK from client {sender_id}")

    message_dict = {"type": "OK", "sender": sender_id, "payload": {}}
    _global_process.inject_message(message_dict)

    return jsonify({"message": "OK received"}), 200


@app.route("/api/coordinator/<int:sender_id>", methods=["POST"])
def receive_coordinator(sender_id: int):
    """
    Receive COORDINATOR announcement from a UPPAAL/Tron client.
    """
    if _global_process is None:
        return jsonify({"error": "Process not initialized"}), 500

    print(f"[FLASK] Received COORDINATOR announcement from client {sender_id}")

    message_dict = {"type": "COORDINATOR", "sender": sender_id, "payload": {}}
    _global_process.inject_message(message_dict)

    return jsonify({"message": "Coordinator acknowledged"}), 200


@app.route("/api/start_election", methods=["POST"])
def trigger_election():
    """
    Trigger an election from this process.
    This can be called externally to initiate an election.
    """
    if _global_process is None:
        return jsonify({"error": "Process not initialized"}), 500

    print(f"[FLASK] Starting election on process {_global_process.id}")

    # Capture the process reference for the closure
    process = _global_process

    # Start election in background thread
    def run_election():
        coordinator = process.start_election()
        print(f"[FLASK] Election completed, coordinator: {coordinator}")

    threading.Thread(target=run_election, daemon=True).start()

    return jsonify(
        {"message": "Election started", "process_id": _global_process.id}
    ), 200


@app.route("/api/status", methods=["GET"])
def get_status():
    if _global_process is None:
        return jsonify({"error": "Process not initialized"}), 500

    return jsonify(
        {
            "process_id": _global_process.id,
            "is_alive": _global_process.is_alive,
            "coordinator_id": _global_process.coordinator_id,
            "in_election": _global_process.in_election,
            "is_coordinator": _global_process.is_coordinator(),
            "known_clients": KNOWN_CLIENTS,
        }
    ), 200


# -------------------------
# Simple HTTP Adapter (placeholder for Tron adapter)
# -------------------------


class SimpleHTTPAdapter(AdapterInterface):
    """
    Simple HTTP adapter that sends messages via HTTP POST.
    This is a placeholder - replace with actual Tron adapter implementation.
    """

    def send(
        self, receiver_id: int, message_dict: Dict[str, Any]
    ) -> Optional[Dict[str, Any]]:
        return send_http_message(receiver_id, message_dict)

    def broadcast(self, message_dict: Dict[str, Any]) -> List[Dict[str, Any]]:
        responses = []
        for client_id in KNOWN_CLIENTS:
            resp = send_http_message(client_id, message_dict)
            if resp:
                responses.append(resp)
        return responses


# -------------------------
# Server runner
# -------------------------


def run_server(
    process_id: int, client_ids: List[int], host: str = "0.0.0.0", port: int = 5000
):
    global _global_process

    # Set up known clients
    set_known_clients(client_ids)

    # Create election process with HTTP adapter
    adapter = SimpleHTTPAdapter()
    _global_process = BullyElectionProcess(process_id, adapter)

    print(f"\n{'=' * 60}")
    print("Bully Election Process Started")
    print(f"{'=' * 60}")
    print(f"Process ID: {process_id}")
    print(f"Known Clients: {client_ids}")
    print(f"Server running on http://{host}:{port}")
    print("\nEndpoints:")
    print("  POST /api/election/<sender_id>    - Receive ELECTION from client")
    print("  POST /api/ok/<sender_id>          - Receive OK from client")
    print("  POST /api/coordinator/<sender_id> - Receive COORDINATOR from client")
    print("  POST /api/start_election          - Trigger election manually")
    print("  GET  /api/status                  - Get process status")
    print(f"{'=' * 60}\n")

    # Run Flask app
    app.run(host=host, port=port, debug=False)


def run_example():
    """
    Example showing how to run the server.

    Usage:
        python bully_election.py

    Then from another terminal or UPPAAL/Tron:
        curl -X POST http://localhost:5000/api/start_election
        curl http://localhost:5000/api/status
    """
    # This process has ID 5, knows about clients 0,1,2,3,4,6,7,8,9
    run_server(
        process_id=5, client_ids=[0, 1, 2, 3, 4, 6, 7, 8, 9], host="0.0.0.0", port=5000
    )


if __name__ == "__main__":
    run_example()
