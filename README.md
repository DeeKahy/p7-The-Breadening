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

In a third terminal, run:

```bash
cd "your tron directory"
mutex_folder="path to your centralized_mutex.xml"
tron -u 4000,4000 -P 10,200 -F 300 -I SocketAdapter -v 9 $mutex_folder -- localhost 9999
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
