# Importing required functions for the client simulator
import requests
import random
import time
import threading
from threading import Thread, Lock

# Server configuration
SERVER_URL = "http://localhost:5000"
active_clients = []
waiting_clients = []
completed_clients = []
client_counter = 0
lock = Lock()

def make_request(client_id):
    """Make a request to the server and handle the response"""
    global active_clients

    try:
        response = requests.get(f"{SERVER_URL}/api/requesting/{client_id}")
        status = response.status_code
        data = response.json()
        message = data.get("message")
        print(f"Client {client_id} requested -> {message} ({status})")

        with lock:
            if client_id not in active_clients:
                active_clients.append(client_id)

        # Schedule a return after random time (2-10 seconds)
        return_delay = random.uniform(2, 10)
        print(f"Client {client_id} will return in {return_delay:.1f} seconds")

        # Start return timer in separate thread
        return_thread = Thread(target=schedule_return, args=(client_id, return_delay))
        return_thread.daemon = True
        return_thread.start()

    except requests.exceptions.ConnectionError:
        print(f"Client {client_id} failed to connect - is the server running?")
    except Exception as e:
        print(f"Client {client_id} encountered error: {e}")

def schedule_return(client_id, delay):
    """Wait and then make a return request"""
    global active_clients, waiting_clients, completed_clients

    time.sleep(delay)

    with lock:
        if client_id not in waiting_clients:
            waiting_clients.append(client_id)

    while True:
        try:
            response = requests.get(f"{SERVER_URL}/api/returning/{client_id}")
            status = response.status_code
            data = response.json() if response.text else {}
            message = data.get("message")
            #print(f"Client {client_id} returned: {response.text}")

            # Loop and keep retrying
            if status == 423: #"Not your turn yet" in response.text:
                print(f"Locked Client {client_id}: Not your turn yet (423), retrying...")
                time.sleep(5)
                continue

            elif status == 200:
                #data = response.json()
                next_client = data.get("next")
                print(f"Successful Client {client_id}: Returned sucesssfully, Next: {next_client}")
                finalize_client(client_id)
                return
            
            elif status == 204:
                print(f"Accomplished Client {client_id}: Queue is now empty")
                finalize_client(client_id)
                return

            else: # Successful return
                with lock:
                    active_clients.remove(client_id)
                    waiting_clients.remove(client_id)
                    completed_clients.append(client_id)
                return "Successfully returned"

        except requests.exceptions.ConnectionError:
            print(f"Client {client_id} failed to return - server connection lost")
        except Exception as e:
            print(f"Client {client_id} return error: {e}")

def finalize_client(client_id):
    with lock:
        if client_id in active_clients:
            active_clients.remove(client_id)
        if client_id in waiting_clients:
            waiting_clients.remove(client_id)
        completed_clients.append(client_id)

def simulate_random_clients():
    """Main simulation loop that creates random client requests"""
    global client_counter, active_clients

    print("Starting client simulation...")
    print("Press Ctrl+C to stop")

    try:
        while True:
            # Random delay between new requests (1-5 seconds)
            request_delay = random.uniform(1, 5)
            time.sleep(request_delay)

            # Generate new client
            client_counter += 1
            client_id = f"client_{client_counter}"

            print(f"\n--- New client {client_id} making request ---")
            print(f"Active clients: {len(active_clients)} - {active_clients}")

            # Make request in separate thread so we don't block
            request_thread = Thread(target=make_request, args=(client_id,))
            request_thread.daemon = True
            request_thread.start()

    except KeyboardInterrupt:
        print("\nSimulation stopped by user")
        print(f"Final active clients: {active_clients}")

def status_monitor():
    """Monitor and display status every 10 seconds"""
    while True:
        time.sleep(10)
        print(f"\n=== STATUS UPDATE ===")
        print(f"Total clients created: {client_counter}")
        print(f"Currently active: {len(active_clients)}")
        print(f"Currently waiting: {len(waiting_clients)}")
        print(f"Completed clients: {len(completed_clients)}")
        print("====================\n")

# Main Driver Function
if __name__ == '__main__':
    print("Client Simulator for The Breadening Queue System")
    print("Make sure the server is running on http://localhost:5000")

    # Start status monitor in background
    status_thread = Thread(target=status_monitor)
    status_thread.daemon = True
    status_thread.start()

    # Start the main simulation
    simulate_random_clients()
