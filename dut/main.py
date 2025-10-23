from flask import Flask, Response

app = Flask(__name__)

isAvailable = True
queue = []


@app.get("/api/requesting/<clientno>")
def requesting(clientno):
    global isAvailable, queue

    if clientno not in queue:
        queue.append(clientno)

    if queue and queue[0] == clientno and isAvailable:
        isAvailable = False
        return Response(f"grant({clientno})\n", mimetype="text/plain")

    return Response(status=204)


@app.get("/api/returning/<clientno>")
def returning(clientno):
    global isAvailable, queue

    if not queue or clientno != queue[0]:
        return Response(status=204)

    queue.pop(0)
    if queue:
        isAvailable = False
        return Response(f"grant({queue[0]})\n", mimetype="text/plain")

    isAvailable = True
    return Response(status=204)


if __name__ == "__main__":
    app.run(debug=False, use_reloader=False, threaded=False)
