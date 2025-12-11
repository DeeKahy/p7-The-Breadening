# server.py
from flask import Flask, jsonify

app = Flask(__name__)

# Kø: første element er den, der har/skal have adgang
queue = []


@app.get("/api/requesting/<clientno>")
def requesting(clientno):
    """
    request! fra klienten.
    UPPAAL-logik:
      - hvis klienten ikke er i køen → enqueue
      - hvis den står forrest → den må gå i 'using' (grant)
      - ellers → den er i køen, men må vente
    """
    global queue

    # Enqueue kun hvis ikke allerede i kø
    if clientno not in queue:
        queue.append(clientno)

    pos = queue.index(clientno)

    if pos == 0:
        # grant! til denne klient
        return jsonify({
            "message": "proceed",   # svarer til grant!
            "position": 0
        }), 200
    else:
        # Klienten er i kø, men ikke forrest
        return jsonify({
            "message": "queued",
            "position": pos
        }), 202


@app.get("/api/returning/<clientno>")
def returning(clientno):
    """
    done! fra klienten.
    UPPAAL-logik:
      - kun første i kø må lave done!
      - efter dequeue: hvis køen ikke er tom → grant til næste
    """
    global queue

    if not queue:
        # I modellen kan done! ikke ske på tom kø; vi svarer med fejl
        return jsonify({"message": "queue_empty_error"}), 500

    if clientno != queue[0]:
        if clientno in queue:
            # Klienten er i køen, men ikke forrest → ikke din tur
            return jsonify({
                "message": "not_your_turn",
                "position": queue.index(clientno)
            }), 423
        else:
            # Klienten er slet ikke i køen
            return jsonify({
                "message": "not_in_queue"
            }), 409

    # Her er clientno == queue[0] → lovligt done!
    queue.pop(0)

    if queue:
        # Der står stadig nogen i køen → grant til næste
        next_client = queue[0]
        return jsonify({
            "message": "returned",
            "next": next_client
        }), 200
    else:
        # Køen er tom
        return jsonify({
            "message": "queue_empty"
        }), 204


if __name__ == "__main__":
    app.run(debug=True)
