from flask import Flask
from flask_sock import Sock
import json

# Flask constructor
app = Flask(__name__)
sock = Sock(app)

isAvailable = True
queue = []

webSocketConnections = {}

def send_token(clientno):
    message = {
        "type": "grant",
        "client_id": clientno,
        "status": 200,
        "message": "proceed",
        "position": 0
    }
    
    if clientno in webSocketConnections:
        try:
            webSocketConnections[clientno].send(json.dumps(message))
            print(f"200: Sent token to client {clientno}")
        except Exception as e:
            print(f"noCode?: Error sending token to client {clientno}: {e}")
    else:
        # Broadcast if we don't have a specific connection
        for ws in webSocketConnections.values():
            try:
                ws.send(json.dumps(message))
            except:
                pass


def handle_request(clientno, ws):
    global isAvailable, queue
    
    print(f"Request from client {clientno}")
    
    # Check if already in queue
    if clientno in queue:
        position = queue.index(clientno)
        print(f"409: Client {clientno} already in queue at position {position}")
        
        response = {
            "type": "response",
            "action": "request",
            "status": 409,
            "client_id": clientno,
            "message": "already_in_queue",
            "position": position
        }
        ws.send(json.dumps(response))
        return
    
    # Add to queue
    queue.append(clientno)
    print(f"202: Client {clientno} added to queue. Queue: {queue}")
    
    if queue.index(clientno) == 0:
        # Client is first in queue - grant immediately
        isAvailable = False
        print(f"200: Granted access to client {clientno}")
        send_token(clientno)
        
        response = {
            "type": "response",
            "action": "request",
            "status": 200,
            "client_id": clientno,
            "message": "proceed",
            "position": 0
        }
        ws.send(json.dumps(response))
    else:
        # Client queued
        position = queue.index(clientno)
        print(f"202: Client {clientno} queued at position {position}")
        
        response = {
            "type": "response",
            "action": "request",
            "status": 202,
            "client_id": clientno,
            "message": "queued",
            "position": position
        }
        ws.send(json.dumps(response))


def handle_done(clientno, ws):
    global isAvailable, queue
    
    print(f"Done from client {clientno}")
    
    # 500: Queue empty
    if not queue:
        print(f"500: Queue empty when client {clientno} tried to return")
        error = {
            "type": "response",
            "action": "done",
            "status": 500,
            "message": "queue_empty",
            "client_id": clientno
        }
        ws.send(json.dumps(error))
        return
    
    # 409: Not in queue
    if clientno not in queue:
        print(f"409: Client {clientno} is not in queue")
        error = {
            "type": "response",
            "action": "done",
            "status": 409,
            "message": "not_in_queue",
            "client_id": clientno
        }
        ws.send(json.dumps(error))
        return
    
    # 423: Not your turn
    if clientno != queue[0]:
        position = queue.index(clientno)
        print(f"423: Not client {clientno}'s turn (position {position})")
        error = {
            "type": "response",
            "action": "done",
            "status": 423,
            "message": "not_your_turn",
            "client_id": clientno,
            "position": position
        }
        ws.send(json.dumps(error))
        return
    
    # Remove client from queue
    queue.pop(0)
    print(f"Client {clientno} removed from queue. Queue: {queue}")
    
    if queue:
        # 200: client has returned, go to next client
        isAvailable = False
        next_client = queue[0]
        print(f"200: Client {clientno} returned, granting access token to next client {next_client}")
        send_token(next_client)
        
        response = {
            "type": "response",
            "action": "done",
            "status": 200,
            "client_id": clientno,
            "message": "returned",
            "next": next_client
        }
        ws.send(json.dumps(response))
    else:
        # 204: queue is empty
        isAvailable = True
        print(f"204: Client {clientno} has returned, queue is now empty")
        
        response = {
            "type": "response",
            "action": "done",
            "status": 204,
            "client_id": clientno,
            "message": "queue_empty"
        }
        ws.send(json.dumps(response))


@sock.route('/ws')
def websocket(ws):
    client_id = None
    
    print("New WebSocket connection established")
    
    try:
        while True:
            message = ws.receive()
            
            if message is None:
                break
            
            try:
                data = json.loads(message)
                msg_type = data.get('type')
                client_id = data.get('client_id')
                
                # Store connection
                if client_id:
                    webSocketConnections[client_id] = ws
                
                print(f"Received message: {data}")
                
                if msg_type == 'request':
                    handle_request(client_id, ws)
                elif msg_type == 'done':
                    handle_done(client_id, ws)
                else:
                    error = {
                        "type": "error",
                        "message": f"Unknown message type: {msg_type}"
                    }
                    ws.send(json.dumps(error))
                    
            except json.JSONDecodeError as e:
                error = {
                    "type": "error",
                    "message": f"Invalid JSON: {str(e)}"
                }
                ws.send(json.dumps(error))
            except Exception as e:
                error = {
                    "type": "error",
                    "message": f"Server error: {str(e)}"
                }
                ws.send(json.dumps(error))
                print(f"Error handling message: {e}")
    
    except Exception as e:
        print(f"WebSocket error: {e}")
    finally:
        # Clean up connection
        if client_id and client_id in webSocketConnections:
            del webSocketConnections[client_id]
        print(f"WebSocket connection closed for client {client_id}")

if __name__ == '__main__':
    print("Starting Flask WebSocket server on port 5000")
    print("WebSocket endpoint: ws://localhost:5000/ws")
    print("\nStatus Codes:")
    print("  200: proceed / returned")
    print("  202: queued")
    print("  204: queue_empty")
    print("  409: already_in_queue / not_in_queue")
    print("  423: not_your_turn")
    print("  500: queue_empty_error")
    print()
    app.run(host='0.0.0.0', port=5000, debug=True)