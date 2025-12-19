from flask import Flask, request, jsonify
import argparse
import threading
import time
import requests
from typing import Dict, Any

HTTP_TIMEOUT = 0.1

class BullyProcess:
    def __init__(
        self,
        process_id: int,
        processes: Dict[int, str],
        round_trip_time: float,
        adapter_url: str | None = None,
    ):
        self.id = process_id
        self.adapter_url = adapter_url
        self.known_processes = processes  # id -> base URL (without path)
        self.processes = sorted(self.known_processes.keys())
        self.N = len(self.processes)

        if self.id not in self.processes:
            raise ValueError(f"My id {self.id} is not in processes list {self.processes}")

        self.index = self.processes.index(self.id)

        # Start with the highest ID as initial leader (typical Bully assumption)
        self.leader = -1

        self.round_trip_time = round_trip_time
        self.shut_up = False
        self.coordinator_alive = False
        self.electing = False

        # Used to implement wait_until(coordinator_alive == true, timeout)
        self.coord_event = threading.Event()
        self.election_cancel = threading.Event()
        self.lock = threading.Lock()

    def log(self, msg: str) -> None:
        print(f"[Process {self.id}] {msg}", flush=True)

    def send(self, msg_type: str, receiver_id: int, parameters: Dict[str, Any] | None = None) -> None:
        if parameters is None:
            parameters = {}

        payload: dict[str, object] = {
            "sender": self.id,
            "receiver": receiver_id,
            "type": msg_type,
            "parameters": parameters,
        }

        receiver_url = self.known_processes.get(receiver_id)

        # Always notify adapter if configured (so TRON sees the message)
        if self.adapter_url:
            try:
                adapter_endpoint = f"{self.adapter_url}/api/message"
                requests.post(adapter_endpoint, json=payload, timeout=HTTP_TIMEOUT)
            except Exception as e:
                self.log(f"Warning: failed to notify adapter at {self.adapter_url}: {e}")

        # Real peer?
        if receiver_url is not None:
            try:
                self.log(f"Sending {msg_type} to {receiver_id} at {receiver_url}")
                requests.post(
                    f"{receiver_url}/api/message",
                    json=payload,
                    timeout=HTTP_TIMEOUT,
                )
            except Exception as e:
                self.log(f"Error sending {msg_type} to {receiver_id} at {receiver_url}: {e}")
        else:
            # No real HTTP peer for this id (e.g. virtual id)
            self.log(f"No real peer for receiver {receiver_id}, will only notify adapter")

    # --- Main loop: run() --------------------------------------------------

    def run_forever(self) -> None:
        # index already found in __init__
        self.log(f"Known processes: {self.processes}, initial leader assumed: {self.leader}")

        time.sleep(self.round_trip_time +1.5) # wait a bit for all processes to start

        while True:
            try:
                with self.lock:
                    current_leader = self.leader
                    i_am_leader = (self.id == current_leader)

                if not i_am_leader:
                    # check_coordinator(); may raise TimeoutError
                    self.check_coordinator(current_leader)
            except TimeoutError:
                with self.lock:
                    should_start = not self.shut_up
                if should_start:
                    self.log("Leader timeout detected, starting election")
                    self.start_election()

            # wait(round_trip_time);
            time.sleep(self.round_trip_time)


    # --- start_election() --------------------------------------------------

    def start_election(self) -> None:
        self.log("Starting election")
        # shut_up = false;   # I am initiating an election
        with self.lock:
            self.election_cancel.clear()
            my_index = self.index
            processes_copy = list(self.processes)

        # Ask all higher-ID processes if they are alive
        # for i = index + 1; i < N; i++ {
        for pid in processes_copy[my_index + 1 :]:
            # time.sleep(self.round_trip_time)
            if self.shut_up or self.election_cancel.is_set():
                self.log("Election cancelled while contacting higher-ID processes")
                break
            self.log(f"Asking higher-id process {pid} via SHUT_UP")
            self.send("shut_up", pid)

        # Wait to see if any higher-ID process bullies me back
        self.election_cancel.wait(timeout=self.round_trip_time)

        if self.election_cancel.is_set():
            self.log("Election cancelled while waiting for higher-ID replies")
            with self.lock:
                self.electing = False
            return

        # Nobody higher told me to shut up → I am the new leader
        with self.lock:
            self.leader = self.id
            processes_copy = list(self.processes)
            self.electing = False

        self.log("No higher-id bully; I am the new leader, broadcasting ELECTED")
        for pid in processes_copy:
            self.send("elected", pid)
        
        self.electing = False

    # --- receives(request) -------------------------------------------------

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

        if msg_type == "shut_up":
            self._handle_shut_up(sender_id)
        elif msg_type == "elected":
            self._handle_elected(sender_id)
        elif msg_type == "check":
            self._handle_check(sender_id)
        elif msg_type == "answer":
            self._handle_answer(sender_id)
        else:
            self.log(f"Unknown message type: {msg_type}")

    def _handle_shut_up(self, sender_id: int) -> None:
        # case shut_up:
        with self.lock:
            my_id = self.id

        if sender_id < my_id and not self.shut_up:
            # A lower-ID process is asking me to shut up → I bully them
            self.log(
                f"Received SHUT_UP from lower-id {sender_id}, bullying back and starting my own election"
            )
            self.send("shut_up", sender_id)
            # Start election in a background thread so we don't block the HTTP handler
            if not self.electing:
                self.electing = True
                threading.Thread(target=self.start_election, daemon=True).start()
        else:
            # A higher-ID process told me to shut up → I comply
            self.log(f"Received SHUT_UP from higher-id {sender_id}, complying")
            with self.lock:
                self.shut_up = True
                self.election_cancel.set() 

    def _handle_elected(self, sender_id: int) -> None:
        # case elected: leader = request.sender_id; shut_up = false;
        self.log(f"Received ELECTED from {sender_id}, accepting as new leader")
        with self.lock:
            self.leader = sender_id
            self.shut_up = False
            self.election_cancel.set()

    def _handle_check(self, sender_id: int) -> None:
        # case check: only the leader should respond
        with self.lock:
            is_leader = (self.id == self.leader)

        if is_leader:
            self.log(f"Received CHECK from {sender_id}, replying ANSWER as I am leader")
            self.send("answer", sender_id)
        else:
            self.log(
                f"Received CHECK from {sender_id}, but I am not leader (leader is {self.leader})"
            )

    def _handle_answer(self, sender_id: int) -> None:
        # case answer: coordinator_alive = true;
        self.log(f"Received ANSWER from {sender_id}, marking coordinator as alive")
        with self.lock:
            self.coordinator_alive = True
            self.coord_event.set()

    # --- check_coordinator() ----------------------------------------------

    def check_coordinator(self, leader_id: int) -> None:
        self.log(f"Checking if leader {leader_id} is alive")
        # coordinator_alive = false;
        with self.lock:
            self.coordinator_alive = False
            self.coord_event.clear()

        # send(request{type: "check", receiver: leader, sender: id});
        self.send("check", leader_id)

        # wait_until(coordinator_alive == true, timeout = round_trip_time);
        alive = self.coord_event.wait(timeout=self.round_trip_time)

        with self.lock:
            if not alive or not self.coordinator_alive:
                # if coordinator_alive == false: raise timeOut;
                self.log("Leader did not respond in time – timeout")
                raise TimeoutError("Coordinator did not respond")
            else:
                self.log("Leader responded to CHECK")


# --- Flask wiring -----------------------------------------------------------

app = Flask(__name__)
bully_process: BullyProcess | None = None


@app.route("/api/message", methods=["POST"])
def api_message():
    """
    Generic message endpoint that accepts the JSON request package:
    {
      "type": "...",
      "sender": <int>,
      "receiver": <int>,
      "parameters": { ... }
    }
    """
    global bully_process
    if bully_process is None:
        return jsonify({"error": "Process not initialized"}), 500

    data = request.get_json(force=True, silent=True) or {}
    bully_process.receive(data)
    return jsonify({"status": "ok"})


@app.route("/status", methods=["GET"])
def status():
    """
    Simple debug endpoint to inspect local state.
    """
    global bully_process
    if bully_process is None:
        return jsonify({"error": "Process not initialized"}), 500

    with bully_process.lock:
        state = {
            "id": bully_process.id,
            "processes": bully_process.processes,
            "leader": bully_process.leader,
            "shut_up": bully_process.shut_up,
            "round_trip_time": bully_process.round_trip_time,
        }
    return jsonify(state)


def parse_args():
    parser = argparse.ArgumentParser(description="Flask Bully Algorithm Process")
    parser.add_argument("--id", type=int, required=True, help="ID of this process")
    parser.add_argument(
        "--peer-map",
        required=True,
        type=str,
        help=(
            "Explicit mapping of process IDs to URLs, e.g. "
            '"0=10.0.0.1:6000,1=http://node1:7000,2=10.0.0.2:6000". '
            "If provided, overrides --host/--base-port for known peers."
        ),
    )
    parser.add_argument(
        "--adapter-url",
        type=str,
        default=None,
        help="Base URL of the TRON adapter (e.g. http://host.docker.internal:6000). "
            "If omitted, no TRON notifications are sent.",
    )
    parser.add_argument(
        "--round-trip-time",
        type=float,
        default=1.0,
        help="Estimated round-trip time in seconds",
    )
    parser.add_argument(
        "--auto-loop",
        action="store_true",
        help=(
            "Enable the background leader-liveness loop (check_coordinator + "
            "automatic elections). Leave this OFF for TRON conformance tests."
        ),
    )
    
    # Flask server binding options
    parser.add_argument(
        "--bind-port",
        type=int,
        default=5000,
        help="Base port; each process listens on base-port + id "
             "(used if --peer-map is not given)",
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



if __name__ == "__main__":
    args = parse_args()

    # Choose mapping strategy
    known = parse_peer_map(args.peer_map)

    bully_process = BullyProcess(
        process_id=args.id,
        processes=known,
        round_trip_time=args.round_trip_time,
        adapter_url=args.adapter_url
    )

    # Start the main bully loop in a background thread
    if args.auto_loop:
        loop_thread = threading.Thread(target=bully_process.run_forever, daemon=True)
        loop_thread.start()
        bully_process.log("Auto loop ENABLED (will check coordinator and start elections automatically)")
    else:
        bully_process.log("Auto loop DISABLED (TRON-friendly mode: only reacts to /api/start_election + messages)")


    # Run Flask app on port base-port + id (still configurable per-node)
    app.run(
        host=args.flask_host,
        port=args.bind_port,
        debug=False,
        use_reloader=False,
        threaded=True,
    )