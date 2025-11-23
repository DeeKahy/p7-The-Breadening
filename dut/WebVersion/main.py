# Importing required functions
from flask import Flask, request, jsonify

# Flask constructor
app = Flask(__name__)


isAvailable = True
queue = []


@app.get('/api/requesting/<clientno>')
def requesting(clientno):
    global isAvailable, queue

    if clientno not in queue:
        queue.append(clientno)

    if queue.index(clientno) == 0 and isAvailable:
        isAvailable = False
        return jsonify({
            "message": "proceed",
            "position": queue.index(clientno)
        }), 200 #"okay go right straight totally ahead"
    elif queue.index(clientno) > 0:
        return jsonify({
            "message": "queued",
            "position": queue.index(clientno)
        }), 202 #"you have now been queued, i will return when client[" + str(queue.index(clientno) - 1 ) +  "] has returned"
    else:
        return jsonify({
            "message": "already_in_queue",
            "position": queue.index(clientno)
        }), 409 #"you are already in the queue, please wait until client[" + str(queue.index(clientno) - 1 ) + "] has returned"


@app.get('/api/returning/<clientno>')
def returning(clientno):
    global isAvailable, queue

    if not queue:
        return jsonify({"message": "queue_empty_error"}), 500 #"something seriously went wrong go fix anders"

    if clientno != queue[0]:
        if clientno in queue:
            return jsonify({
                "message": "not_your_turn",
                "position": queue.index(clientno)
            }), 423 #"Not your turn yet, please wait until client[" + str(queue.index(clientno) - 1 ) + "] has returned"
        else:
            return jsonify({
                "message":"not_in_queue"
            }), 409 #"You are not in the queue"
    else:
        queue.pop(0)

        if queue:
            isAvailable = False
            return jsonify({
                "message": "returned",
                "next": queue[0]
            }), 200 #"Returning: " + str(clientno) + " next is " + str(queue[0])
        else:
            isAvailable = True
            return jsonify({
                "message": "queue_empty"
            }), 204 #"Queue is now empty."

# Main Driver Function
if __name__ == '__main__':
    # Run the application on the local development server
    app.run(debug=True)
