# Running MutexAdapter with UPPAAL TRON

## Project structure
```text
project/
├── src/
│   ├── com/uppaal/tron/Adapter.java
│   ├── com/uppaal/tron/Reporter.java
│   ├── mutex/MutexAdapter.java
│   └── mutex/centralized_mutex.xml
```

## 1. Build the adapter

Whenever you have made changes to the adapter, remember to compile before you run the test.

```bash
cd "your project root folder"
javac -cp ".;src" -d build src/com/uppaal/tron/Adapter.java src/com/uppaal/tron/Reporter.java src/mutex/MutexAdapter.java
```

This produces compiled classes in the `build/` directory.

## 2. Start the server (System Under Test)

Ensure your mutex server exposes the following endpoints:

* `GET /api/requesting/<id>` — client `<id>` requests access.
* `GET /api/returning/<id>` — client `<id>` signals completion.

Example (Python):

```bash
python main.py
```

By default, the server runs at `http://localhost:5000`.

## 3. Start the adapter

In a new terminal, run:

```bash
cd "your project root folder"
java -DmutexServerBase="http://localhost:5000" -cp build mutex.MutexAdapter 9999
```

* `mutexServerBase` points to your server’s base URL.
* `9999` is the port the adapter will expose for TRON.

## 4. Run TRON
Remember to add tron to your path or use the full path of tron such as this `../uppaal-tron-1.5-linux/tron`

In a third terminal, run this in the projet root:


`no such file or directory`. this means you dont have tron properly set up and installed.

sudo dpkg --add-architecture i386
sudo apt update
sudo apt install libc6:i386 libstdc++6:i386 zlib1g:i386



```bash
tron -u 4000,4000 -P 10,200 -F 300 -I SocketAdapter -v 9 src/mutex/centralized_mutex.xml -- localhost 9999
```


| Flag                    | Description                               |
| ----------------------- | ----------------------------------------- |
| `-u 4000,4000`          | Input/output observation uncertainties    |
| `-P 10,200`             | Delay probability distribution (optional) |
| `-F 300`                | Look‑ahead window                         |
| `-I SocketAdapter`      | Use the generic socket adapter            |
| `-v 9`                  | Verbose logging level                     |
| `centralized_mutex.xml` | Your UPPAAL model                         |
| `-- localhost 9999`     | TRON connects to your adapter             |




# config file example (this one works, so if you fuck up you can revert it from here.)
```
{
  "_comment": "Example configuration file for the Mutex Testing Environment",
  "_note": "All paths are relative to the project root (where run_inside.sh is located)",
  "logs": {
    "path": "logs/"
  },
  "dut": {
    "project_path": "dut",
    "project_path": "dut/centralized_mutex",
    "main_file": "main.py",
    "python_cmd": "python3"
  },
  "uppaal": {
    "model_file": "src/mutex/centralized_mutex.xml",
    "tron_path": "../uppaal-tron-1.5-linux/tron",
    "options": {
      "u": "4000,4000",
      "P": "10,200",
      "F": "300",
      "I": "SocketAdapter",
      "v": "9"
    }
  },
  "adapter": {
    "port": "9999",
    "server_base": "http://localhost:5000"
  },
  "server": {
    "port": "5000"
  }
}
```