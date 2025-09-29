{ pkgs ? import <nixpkgs> {} }:

let
  # Python environment with Flask and other dependencies
  pythonEnv = pkgs.python3.withPackages (ps: with ps; [
    flask
    requests
    werkzeug
    pytest
    pytest-cov
    black
    flake8
    mypy
  ]);

in
pkgs.mkShell {
  buildInputs = with pkgs; [
    pythonEnv
    curl
    jq
    httpie
    watchman

    # Development tools
    git
    gnumake

    # Documentation
    pandoc
  ];

  shellHook = ''
    echo "🍞 Flask Queueing System Development Environment"
    echo ""
    echo "Available Python packages:"
    echo "  - Flask $(python -c 'import flask; print(flask.__version__)' 2>/dev/null || echo 'not found')"
    echo "  - Requests $(python -c 'import requests; print(requests.__version__)' 2>/dev/null || echo 'not found')"
    echo "  - Werkzeug $(python -c 'import werkzeug; print(werkzeug.__version__)' 2>/dev/null || echo 'not found')"
    echo ""
    echo "Quick commands:"
    echo "  python dut/queue_server.py     - Start the server"
    echo "  python dut/example_client.py   - Run example client"
    echo "  python -m pytest dut/test_queue_system.py -v  - Run tests"
    echo ""
    echo "Server will be available at: http://localhost:5000"
    echo "Health check: curl http://localhost:5000/health"
    echo ""

    # Set up environment variables
    export FLASK_APP=dut/queue_server.py
    export PYTHONPATH="$PWD/dut:$PYTHONPATH"

    # Create convenient aliases
    alias server='python dut/queue_server.py'
    alias client='python dut/example_client.py'
    alias tests='python -m pytest dut/test_queue_system.py -v'
    alias dev='FLASK_ENV=development FLASK_DEBUG=1 python dut/queue_server.py'
  '';
}
