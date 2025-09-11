{ pkgs, lib, stdenv, ... }:

let
  pythonPackages = pkgs.python3Packages;
in
pkgs.mkShell {
  buildInputs = [
    python
    python313Packages.flask

  ];
  packages = [ pkgs.poetry ];
  venvDir = "./.venv";
  postShellHook = ''
  '';
}
