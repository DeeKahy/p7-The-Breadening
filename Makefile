# Flask Queueing System Makefile
.PHONY: help dev server client tests clean check install build run-server run-client run-tests format lint

# Default target
help:
	@echo "🍞 Flask Queueing System - Available Commands:"
	@echo ""
	@echo "Development:"
	@echo "  make dev          - Start development server with debug mode"
	@echo "  make server       - Start production server"
	@echo "  make client       - Run example client"
	@echo "  make tests        - Run test suite"
	@echo ""
	@echo "Code Quality:"
	@echo "  make format       - Format code with black"
	@echo "  make lint         - Run linting checks"
	@echo "  make check        - Run all checks (tests, lint, format)"
	@echo ""
	@echo "Nix Commands:"
	@echo "  make install      - Install dependencies via nix"
	@echo "  make build        - Build the application"
	@echo "  make clean        - Clean build artifacts"
	@echo ""
	@echo "Direct Python Commands:"
	@echo "  make run-server   - Run server directly with python"
	@echo "  make run-client   - Run client directly with python"
	@echo "  make run-tests    - Run tests directly with python"
	@echo ""

# Development commands
dev:
	@echo "Starting development server..."
	cd dut && FLASK_ENV=development FLASK_DEBUG=1 python queue_server.py

server:
	@echo "Starting production server..."
	cd dut && python queue_server.py

client:
	@echo "Running example client..."
	cd dut && python example_client.py

tests:
	@echo "Running test suite..."
	cd dut && python -m pytest test_queue_system.py -v

# Nix-based commands (if in nix environment)
run-server:
	@if command -v nix > /dev/null 2>&1; then \
		echo "Using nix to run server..."; \
		nix run .#server; \
	else \
		echo "Nix not available, using python directly..."; \
		make server; \
	fi

run-client:
	@if command -v nix > /dev/null 2>&1; then \
		echo "Using nix to run client..."; \
		nix run .#client; \
	else \
		echo "Nix not available, using python directly..."; \
		make client; \
	fi

run-tests:
	@if command -v nix > /dev/null 2>&1; then \
		echo "Using nix to run tests..."; \
		nix run .#tests; \
	else \
		echo "Nix not available, using python directly..."; \
		make tests; \
	fi

# Code quality
format:
	@echo "Formatting code with black..."
	@if command -v black > /dev/null 2>&1; then \
		black dut/*.py; \
	else \
		echo "black not found, skipping formatting"; \
	fi

lint:
	@echo "Running linting checks..."
	@if command -v flake8 > /dev/null 2>&1; then \
		flake8 dut/*.py --max-line-length=88 --ignore=E203,W503; \
	else \
		echo "flake8 not found, skipping linting"; \
	fi
	@if command -v mypy > /dev/null 2>&1; then \
		mypy dut/*.py --ignore-missing-imports; \
	else \
		echo "mypy not found, skipping type checking"; \
	fi

check: format lint tests
	@echo "All checks completed!"

# Nix commands
install:
	@echo "Installing dependencies via nix..."
	nix develop

build:
	@echo "Building application..."
	nix build

clean:
	@echo "Cleaning build artifacts..."
	rm -rf result
	rm -rf dut/__pycache__
	rm -rf dut/.pytest_cache
	find . -name "*.pyc" -delete
	find . -name "*.pyo" -delete

# Health checks
health:
	@echo "Checking server health..."
	@curl -s http://localhost:5000/health | jq . || echo "Server not running or jq not available"

status:
	@echo "Checking server status..."
	@curl -s http://localhost:5000/status | jq . || echo "Server not running or jq not available"

# Development workflow
start: dev

stop:
	@echo "Stopping any running flask processes..."
	@pkill -f "queue_server.py" || echo "No flask processes found"

restart: stop start

# Demo commands
demo: client

demo-concurrent:
	@echo "Running concurrent client demo..."
	@for i in {1..3}; do \
		(cd dut && python example_client.py) & \
	done; \
	wait

# Quick test of the system
quick-test:
	@echo "Running quick system test..."
	@echo "1. Starting server in background..."
	@cd dut && python queue_server.py & SERVER_PID=$$!; \
	sleep 2; \
	echo "2. Testing health endpoint..."; \
	curl -s http://localhost:5000/health > /dev/null && echo "✓ Health check passed" || echo "✗ Health check failed"; \
	echo "3. Testing status endpoint..."; \
	curl -s http://localhost:5000/status > /dev/null && echo "✓ Status check passed" || echo "✗ Status check failed"; \
	echo "4. Stopping server..."; \
	kill $$SERVER_PID 2>/dev/null || true

# Documentation
docs:
	@echo "Generating documentation..."
	@if command -v pandoc > /dev/null 2>&1; then \
		pandoc dut/README.md -o dut/README.html; \
		echo "Documentation generated: dut/README.html"; \
	else \
		echo "pandoc not found, skipping documentation generation"; \
	fi
