# client_sim.py
import requests
import random
import time
from threading import Thread, Lock

SERVER_URL = "http://localhost:5000"

lock = Lock()
client_states = {}


def client_loop(client_id: str):
    """
    Simulerer UPPAAL Client(id):
      begin -> request! -> vent på grant -> using (2-10s) -> done! -> begin ...
    """

    while True:
        # BEGIN: vent op til 5 sek (delay <= 5)
        think_delay = random.uniform(0, 5)
        with lock:
            client_states[client_id] = f"begin (sleep {think_delay:.1f}s)"
        time.sleep(think_delay)

        # REQUEST-FASEN: bliv ved til vi får grant (proceed)
        while True:
            try:
                r = requests.get(f"{SERVER_URL}/api/requesting/{client_id}")
            except requests.exceptions.ConnectionError:
                print(f"{client_id}: kunne ikke forbinde til serveren")
                time.sleep(1)
                continue

            status = r.status_code
            data = r.json() if r.text else {}
            msg = data.get("message")

            if status == 200 and msg == "proceed":
                print(f"{client_id}: GRANTED (proceed)")
                break
            elif status == 202:
                pos = data.get("position")
                print(f"{client_id}: queued på position {pos}, venter...")
                time.sleep(1)  # poll efter 1 sek
            elif status in (409, 500):
                print(f"{client_id}: fejl i request-fase: {status} {msg}")
                time.sleep(1)
            else:
                print(f"{client_id}: uventet svar i request-fase: {status} {msg}")
                time.sleep(1)

        # USING: brug ressourcen 2-10 sek (delay mellem 2 og 10)
        use_delay = random.uniform(2, 10)
        with lock:
            client_states[client_id] = f"using (sleep {use_delay:.1f}s)"
        print(f"{client_id}: USING i {use_delay:.1f} sek.")
        time.sleep(use_delay)

        # DONE-FASEN: send done! til serveren (returning)
        while True:
            try:
                r = requests.get(f"{SERVER_URL}/api/returning/{client_id}")
            except requests.exceptions.ConnectionError:
                print(f"{client_id}: kunne ikke sende return (forbindelsesfejl)")
                time.sleep(1)
                continue

            status = r.status_code
            data = r.json() if r.text else {}
            msg = data.get("message")

            if status in (200, 204):
                # 200: returned + evt next, 204: queue_empty
                print(f"{client_id}: DONE ({status}, {msg})")
                break
            elif status == 423:
                # "not_your_turn" – svarer til at vi ikke er forrest i køen
                print(f"{client_id}: ikke min tur endnu (423), prøver igen...")
                time.sleep(1)
            elif status in (409, 500):
                print(f"{client_id}: fejl i return-fase: {status} {msg}")
                time.sleep(1)
            else:
                print(f"{client_id}: uventet svar i return-fase: {status} {msg}")
                time.sleep(1)

        # Herefter går vi tilbage til BEGIN (loopet fortsætter)


def status_monitor():
    while True:
        time.sleep(10)
        with lock:
            print("\n=== STATUS UPDATE ===")
            for cid, st in client_states.items():
                print(f"{cid}: {st}")
            print("====================\n")


if __name__ == "__main__":
    print("Client Simulator for The Breadening Queue System")
    print("Starter 3 klienter: client_0, client_1, client_2\n")

    # Start status-monitor
    Thread(target=status_monitor, daemon=True).start()

    # Start 3 klienter (som i UPPAAL-modellen med N = 3)
    for i in range(3):
        cid = f"client_{i}"
        client_states[cid] = "starting"
        t = Thread(target=client_loop, args=(cid,), daemon=True)
        t.start()

    # Hold main-tråd i live
    while True:
        time.sleep(1)
