# Importing required functions
from typing import Any, Dict
import requests
import argparse
import threading
import time


from flask import Flask, jsonify, request

# Flask constructor
app = Flask(__name__)


class Centralized:
    def __init__(
        self,
        processes: Dict[int, str],
        process_id: int,
        isAvailable: bool,
        queue: list[Dict[str, Any]],
        granted_id: int,
        parameters: Dict[str, Any],
        round_trip_time: float,
    ):
        self.isAvailable = True
        self.queue = []
        self.granted_id = None

    def log(self, msg: str) -> None:
        print(f"[Process {self.id}] {msg}", flush=True)

    def send(
        self, msg_type: str, receiver: int, parameters: Dict[str, Any] | None = None
    ) -> None:
        """
        Send an HTTP POST to the receiver's /api/message endpoint with the required JSON format:
        {
          "type": "<msg_type>",
          "sender": <my id>,
          "receiver": <receiver id>,
          "parameters": { ... }
        }
        """
        if parameters is None:
            parameters = {}

        payload = {
            "type": msg_type,
            "sender": self.id,
            "receiver": receiver,
            "parameters": parameters,
        }

        url = self.known_processes.get(receiver)

        if url is None:
            # Special case: UPPAAL can send CHECK with leader = -1.
            # We still want TRON to see this message, so route it via ANY fake node port,
            # but keep receiver = -1 in the JSON.
            if receiver < 0 and self.known_processes:
                # Just use the first known URL (e.g. node 0 at port 5000)
                url = next(iter(self.known_processes.values()))
                self.log(f"Routing {msg_type} to virtual receiver {receiver} via {url}")
            else:
                self.log(f"Unknown receiver {receiver}, cannot send {msg_type}")
                return

        try:
            self.log(f"Sending {msg_type} to {receiver} at {url}")
            requests.post(
                f"{url}/api/message",
                json=payload,
                timeout=self.round_trip_time / 2,
            )
        except requests.RequestException as e:
            self.log(f"Failed to send {msg_type} to {receiver}: {e}")


    def handle_request(self, clientno):
        if clientno not in self.queue:
            self.queue.append(clientno)

        if self.queue.index(clientno) == 0 and self.isAvailable == True:
            self.isAvailable = False
            return jsonify(
                {"message": "proceed", "position": 0}
            ), 200  # "okay go right straight totally ahead"
        else:  # queue.index(clientno) > 0:
            return (
                jsonify({"message": "queued", "position": self.queue.index(clientno)}),
                202,
            )  # "you have now been queued, i will return when client[" + str(queue.index(clientno) - 1 ) +  "] has returned"

    def handle_done(self, request):
        if (
            request.get("message") == "done"
            and request.get("sender") == self.granted_id
        ):
            self.queue.pop(0)
            self.isAvailable = True
            if self.queue.length > 0:
                self.sendGrant()
                self.isAvailable = False

    def send_grant(self):
        self.log(f"Giving head of queue {self.queue.index(0)} GRANT")
        self.send("grant", self.queue.index(0))

    def receive(self, request_json: Dict[str, Any]) -> None:
        """
        Handle an incoming request according to the pseudocode's 'receives(request)'.
        """
        msg_type = request_json.get("type")
        sender_id = int(request_json.get("sender"))
        receiver_id = int(request_json.get("receiver"))

        # Drop messages not intended for us
        if receiver_id != self.id:
            self.log(
                f"Ignoring message type={msg_type} from {sender_id} intended for {receiver_id}"
            )
            return

        self.log(f"Received {msg_type} from {sender_id}")

        if msg_type == "request":
            self.handle_request(sender_id)
        elif msg_type == "done":
            self.handle_done(sender_id)

# --- Flask wiring -----------------------------------------------------------

app = Flask(__name__)
centralized: Centralized | None = None


@app.route("/api/returning", methods=["POST"])
def api_returning():
    """
    Generic message endpoint that accepts the JSON request package:
    {
      "type": "...",
      "sender": <int>,
      "receiver": <int>,
      "parameters": { ... }
    }
    """
    global centralized
    if centralized is None:
        return jsonify({"error": "Process not initialized"}), 500

    data = request.get_json(force=True, silent=True) or {}
    centralized.receive(data)
    return jsonify({"status": "ok"})


@app.route("/api/request", methods=["POST"])
def api_request():
    """
    Generic message endpoint that accepts the JSON request package:
    {
      "type": "...",
      "sender": <int>,
      "receiver": <int>,
      "parameters": { ... }
    }
    """
    global centralized
    if centralized is None:
        return jsonify({"error": "Process not initialized"}), 500

    data = request.get_json(force=True, silent=True) or {}
    centralized.receive(data)
    return jsonify({"status": "ok"})


def parse_args():
    parser = argparse.ArgumentParser(description="Centralized Mutex Algorithm")
    parser.add_argument("--id", type=int, required=True, help="ID of this process")
    parser.add_argument(
        "--processes",
        type=str,
        required=True,
        help="Comma-separated list of all process IDs, e.g. 0,1,2,3",
    )
    parser.add_argument(
        "--base-port",
        type=int,
        default=5000,
        help="Base port; each process listens on base-port + id "
        "(used if --peer-map is not given)",
    )
    parser.add_argument(
        "--host",
        type=str,
        default="127.0.0.1",
        help="Default host for building URLs when --peer-map is not given",
    )
    parser.add_argument(
        "--peer-map",
        type=str,
        help=(
            "Explicit mapping of process IDs to URLs, e.g. "
            '"0=10.0.0.1:6000,1=http://node1:7000,2=10.0.0.2:6000". '
            "If provided, overrides --host/--base-port for known peers."
        ),
    )
    parser.add_argument(
        "--round-trip-time",
        type=float,
        default=1.0,
        help="Estimated round-trip time in seconds",
    )
    parser.add_argument(
        "--flask-host",
        type=str,
        default="0.0.0.0",
        help="Flask bind host",
    )
    return parser.parse_args()


def parse_peer_map(spec: str) -> Dict[int, str]:
    """
    Parse a specification like:
      "0=10.0.0.1:6000,1=http://node1:7000,2=10.0.0.2:6000"
    into a dict {0: "http://10.0.0.1:6000", 1: "http://node1:7000", ...}.
    """
    mapping: Dict[int, str] = {}
    if not spec:
        return mapping

    for entry in spec.split(","):
        entry = entry.strip()
        if not entry:
            continue
        if "=" not in entry:
            raise ValueError(f"Invalid peer-map entry (no '='): {entry!r}")
        k, v = entry.split("=", 1)
        k = k.strip()
        v = v.strip()
        if not k or not v:
            raise ValueError(f"Invalid peer-map entry: {entry!r}")
        pid = int(k)
        # If user gave just "host:port", prefix "http://"
        if "://" in v:
            url = v
        else:
            url = f"http://{v}"
        mapping[pid] = url

    return mapping


def build_known_processes(
    host: str, base_port: int, process_ids: list[int]
) -> Dict[int, str]:
    """
    Build a mapping id -> base URL, e.g. 0 -> "http://127.0.0.1:5000".
    Used when --peer-map is not specified.
    """
    mapping: Dict[int, str] = {}
    for pid in process_ids:
        mapping[pid] = f"http://{host}:{base_port + pid}"
    return mapping


# Main Driver Function
if __name__ == "__main__":
    args = parse_args()

    all_ids = [int(x) for x in args.processes.split(",") if x.strip() != ""]

    # Choose mapping strategy
    if args.peer_map:
        known = parse_peer_map(args.peer_map)
        # Optional sanity check: ensure all_ids are present
        missing = [pid for pid in all_ids if pid not in known]
        if missing:
            raise SystemExit(f"--peer-map missing entries for process IDs: {missing}")
    else:
        known = build_known_processes(args.host, args.base_port, all_ids)

    centralized = Centralized(
        process_id=args.id, processes=known, round_trip_time=args.round_trip_time
    )

    # loop_thread = threading.Thread(target=centralized.run_forever, daemon=True)
    # loop_thread.start()

    # Run Flask app on port base-port + id (still configurable per-node)
    port = args.base_port + args.id
    centralized.log(f"Starting Flask server on port {port}")
    app.run(
        host=args.flask_host,
        port=port,
        debug=False,
        use_reloader=False,
        threaded=True,
    )
