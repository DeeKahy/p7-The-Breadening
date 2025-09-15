# Importing required functions
from flask import Flask, request

# Flask constructor
app = Flask(__name__)


isAvailable = True
queue = []


@app.get('/api/requesting/<clientno>')
def requesting(clientno):
    global isAvailable, queue

    queue.append(clientno)

    if len(queue) == 1 and isAvailable:
        isAvailable = False
        return "okay go right straight totally ahead"
    elif (len(queue) - 1) > 0:
        return "you have now been queued, i will return when " + str(queue[len(queue)-2]) +  "  has returned"


@app.get('/api/returning/<clientno>')
def returning(clientno):
    global isAvailable, queue

    if not queue:
        return "something seriously went wrong go fix anders"

    if clientno !=  queue[0]:
        return "Not your turn yet, please wait until " + str(queue[0]) + " has returned"
    else:
        queue.pop(0)
        isAvailable = True
        return "Returning: " + str(clientno) + " next is " + str(queue[0])

# Main Driver Function
if __name__ == '__main__':
    # Run the application on the local development server
    app.run(debug=True)
