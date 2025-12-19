# Mutex Testing Environment with UPPAAL TRON

This project provides an automated testing environment for mutex implementations using UPPAAL TRON. The startup script handles all the setup and orchestration for you.

## Prerequisites

### 1. Install UPPAAL TRON

Download UPPAAL TRON and place it **one folder above** this repository (at the same level as the repository folder).

Your directory structure should look like this:

```text
parent-directory/
├── uppaal-tron-1.5-linux/          # UPPAAL installation
│   └── tron                         # TRON executable
└── p7-The-Breadening/               # This repository
    ├── run_inside.sh
    ├── src/
    ├── dut/
    └── ...
```

**Download UPPAAL TRON:**
- Visit the UPPAAL website to download the appropriate version for your system
- Extract the archive to the parent directory (one level above this repo)
- Ensure the `tron` executable is at `../uppaal-tron-1.5-linux/tron` relative to this repo

**For Linux users:** You may need to install 32-bit libraries:

```bash
sudo dpkg --add-architecture i386
sudo apt update
sudo apt install libc6:i386 libstdc++6:i386 zlib1g:i386
```

### 2. Install Python Dependencies

```bash
pip3 install flask flask-sock
```

### 3. Java Development Kit

Ensure you have Java installed (JDK 8 or higher):

```bash
java -version
javac -version
```

## Running the Tests

### Quick Start

Simply run the startup script from the repository root:

```bash
./run_inside.sh
```

### What the Startup Script Does

The `run_inside.sh` script automatically:

1. **Prompts for version selection:**
   - WebVersion (HTTP-based mutex implementation)
   - SocketVersion (WebSocket-based mutex implementation)

2. **Checks and installs dependencies:**
   - Verifies Python packages (flask-sock)
   - Downloads required Java libraries if missing:
     - Java-WebSocket (1.5.3)
     - JSON library (20230227)
     - SLF4J API and Simple (2.0.7)

3. **Compiles the Java adapter:**
   - Automatically compiles the appropriate adapter based on your version selection
   - Places compiled classes in the `build/` directory

4. **Starts all components in the correct order:**
   - **Python server(s):** Your mutex implementation
     - WebVersion: 3 processes on ports 4999, 5000, 5001
     - SocketVersion: 1 process on port 5000
   - **Java adapter:** Bridges between your server and TRON (port 9999)
   - **UPPAAL TRON:** Model-based testing engine

5. **Displays color-coded live output:**
   - Green: Server messages
   - Cyan: Adapter messages
   - Magenta: TRON messages

6. **Saves logs:**
   - `logs/server.log` - Server output
   - `logs/adapter.log` - Adapter output
   - `logs/tron.log` - TRON output

### Stopping the Tests

Press `Ctrl+C` to stop all processes. The script will automatically clean up all running components.

## Project Structure

```text
p7-The-Breadening/
├── run_inside.sh                    # Main startup script
├── src/
│   ├── com/uppaal/tron/             # TRON adapter base classes
│   └── mutex/                       # Mutex models and adapters
│       ├── centralized_mutex.xml    # UPPAAL model
│       ├── WebMutex/
│       │   └── MutexAdapter.java    # HTTP adapter
│       └── SocketMutex/
│           └── MutexAdapter.java    # WebSocket adapter
├── dut/                             # Device Under Test (your implementations)
│   ├── WebVersion/
│   │   └── main.py                  # HTTP-based mutex server
│   └── SocketVersion/
│       └── main.py                  # WebSocket-based mutex server
├── build/                           # Compiled Java classes (auto-generated)
├── lib/                             # Java libraries (auto-downloaded)
└── logs/                            # Log files (auto-generated)
```

## Understanding the Output

When running, you'll see color-coded output from all three components:

- **[SERVER]** (Green): Your mutex server handling client requests
- **[ADAPTER]** (Cyan): The bridge translating between TRON and your server
- **[TRON]** (Magenta): UPPAAL TRON executing test cases from the model

TRON will generate test cases based on the UPPAAL model and verify that your implementation behaves correctly.

## Troubleshooting

### "No such file or directory" when starting TRON

Make sure UPPAAL TRON is installed one folder above the repository:

```bash
ls ../uppaal-tron-1.5-linux/tron
```

If this fails, check your UPPAAL installation location.

### Python server fails to start

Check `logs/server.log` for details. Common issues:
- Missing dependencies: `pip3 install flask flask-sock`
- Port already in use: Kill processes using ports 4999, 5000, 5001

### Adapter compilation fails

Ensure you have Java JDK installed:
```bash
javac -version
```

### Adapter fails to connect

Check `logs/adapter.log`. The adapter needs the server to be running first. The startup script handles this automatically, but if you're running components manually, start the server before the adapter.

## Manual Execution (Advanced)

If you need to run components separately instead of using the startup script, see the individual component documentation. However, the startup script is the recommended way to run tests as it handles all dependencies and timing automatically.

## UPPAAL TRON Flags

The startup script uses these TRON configuration flags (see `run_inside.sh` for details):

| Flag                    | Description                               |
| ----------------------- | ----------------------------------------- |
| `-u 4000,4000`          | Input/output observation uncertainties    |
| `-P 10,200`             | Delay probability distribution            |
| `-F 300`                | Look-ahead window                         |
| `-I SocketAdapter`      | Use the generic socket adapter            |
| `-v 9`                  | Verbose logging level                     |
| `centralized_mutex.xml` | Your UPPAAL model                         |
| `-- localhost 9999`     | TRON connects to your adapter on port 9999|

These can be customized by editing `run_inside.sh` if needed.
