{
  description = "Flask Queueing System";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs = { self, nixpkgs, flake-utils }:
    flake-utils.lib.eachDefaultSystem (system:
      let
        pkgs = nixpkgs.legacyPackages.${system};

        pythonEnv = pkgs.python3.withPackages (ps: with ps; [
          flask
          requests
          werkzeug
        ]);

      in {
        devShells.default = pkgs.mkShell {
          buildInputs = with pkgs; [
            pythonEnv
            curl
            jq
          ];

          shellHook = ''
            echo "🍞 Flask Queueing System Development Environment"
            echo ""
            echo "Python: $(python --version)"
            echo "Flask: $(python -c 'import flask; print(flask.__version__)')"
            echo ""
            echo "Quick commands:"
            echo "  cd dut && python queue_server.py     # Start server"
            echo "  cd dut && python example_client.py   # Run client"
            echo "  curl http://localhost:5000/health     # Health check"
            echo ""

            export FLASK_APP=queue_server.py
            export PYTHONPATH="$PWD/dut:$PYTHONPATH"
          '';
        };

        packages.default = pkgs.writeShellScriptBin "flask-queue-server" ''
          cd ${self}/dut
          exec ${pythonEnv}/bin/python queue_server.py
        '';

        apps.default = flake-utils.lib.mkApp {
          drv = self.packages.${system}.default;
        };
      }
    );
}
