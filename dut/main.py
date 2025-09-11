# Importing required functions
from flask import Flask, request

# Flask constructor
app = Flask(__name__)


isAvailable = True
queue = []



@app.get('/api/requesting/<clientno>')
def requesting(clientno):
    global isAvailable, queue
    isAvailable = False
    if not queue:
        queue.append(clientno)
        return "okay go right straight totally ahead"

    queue.append(clientno)

    # needs to ping the actual ip instead of returning in the same request (maybe)
    return "you have now been queued, i will return when " + str(queue[len(queue)-2]) +  "  has returned"



@app.get('/api/returning/<clientno>')
def returning(clientno):
    global isAvailable, queue
    if not queue:
        return "something seriously went wrong go fix anders"

    if clientno == queue[0]:
        queue.pop(0)
        if not queue:
            isAvailable = True
    else:
        return "something seriously went wrong go fix anders"


    return "Returning: " + str(clientno) + "now " + str(queue[0]) + " is revieving the token i guess"



# Main Driver Function
if __name__ == '__main__':
    # Run the application on the local development server
    app.run(debug=True)
