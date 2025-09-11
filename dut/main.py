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
    isAvailable = False
    if not queue:
        return "okay go right straight totally ahead"


    return "You tried accessing 'single_converter' \
    endpoint with value of 'menu' as " + str(clientno)



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


    return "Returning: " + str(clientno)



# Main Driver Function
if __name__ == '__main__':
    # Run the application on the local development server
    app.run(debug=True)
