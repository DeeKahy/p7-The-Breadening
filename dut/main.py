# Importing required functions
from flask import Flask, request

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
        return "okay go right straight totally ahead"
    elif queue.index(clientno) > 0:
        return "you have now been queued, i will return when client[" + str(queue.index(clientno) - 1 ) +  "] has returned"
    else:
        return "you are already in the queue, please wait until client[" + str(queue.index(clientno) - 1 ) + "] has returned"


@app.get('/api/returning/<clientno>')
def returning(clientno):
    global isAvailable, queue

    if not queue:
        return "something seriously went wrong go fix anders"

    if clientno != queue[0]:
        if clientno in queue:
            return "Not your turn yet, please wait until client[" + str(queue.index(clientno) - 1 ) + "] has returned"
        else:
            return "You are not in the queue"
    else:
        queue.pop(0)

        if queue:
            isAvailable = False
            return "Returning: " + str(clientno) + " next is " + str(queue[0])
        else:
            isAvailable = True
            return "Queue is now empty."

# Main Driver Function
if __name__ == '__main__':
    # Run the application on the local development server
    app.run(debug=True)
