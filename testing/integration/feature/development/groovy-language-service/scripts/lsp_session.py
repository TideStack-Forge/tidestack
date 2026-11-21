"""Small stdio LSP test client, scoped to the Groovy semantic qualification scenario."""
import json
from pathlib import Path
import queue
import subprocess
import threading
import time


class LspSession:
    def __init__(self, command: list[str], evidence: Path, cwd: Path):
        self._stderr = (evidence / "server.stderr.log").open("w")
        self._trace = (evidence / "protocol.jsonl").open("w")
        self._trace_lock = threading.Lock()
        self.process = subprocess.Popen(command, cwd=cwd, stdin=subprocess.PIPE,
                                        stdout=subprocess.PIPE, stderr=self._stderr)
        self._messages: queue.Queue = queue.Queue()
        self.notifications: list[dict] = []
        self._next_id = 0
        self._reader = threading.Thread(target=self._read, daemon=True)
        self._reader.start()

    def _record(self, direction: str, message: dict) -> None:
        with self._trace_lock:
            self._trace.write(json.dumps({"direction": direction, "at": time.monotonic(),
                                          "message": message}, ensure_ascii=False) + "\n")
            self._trace.flush()

    def _read(self) -> None:
        try:
            while True:
                headers = {}
                while True:
                    line = self.process.stdout.readline()
                    if not line:
                        self._messages.put(EOFError("Language server stdout closed"))
                        return
                    if line in (b"\r\n", b"\n"):
                        break
                    name, value = line.decode("ascii").split(":", 1)
                    headers[name.lower()] = value.strip()
                length = int(headers["content-length"])
                content = self.process.stdout.read(length)
                if len(content) != length:
                    raise EOFError("Truncated LSP frame")
                message = json.loads(content)
                self._record("server", message)
                self._messages.put(message)
        except (OSError, ValueError, KeyError, EOFError) as error:
            self._messages.put(error)

    def send(self, message: dict) -> None:
        self._record("client", message)
        body = json.dumps({"jsonrpc": "2.0", **message}, ensure_ascii=False).encode()
        self.process.stdin.write(f"Content-Length: {len(body)}\r\n\r\n".encode() + body)
        self.process.stdin.flush()

    def notify(self, method: str, params: dict) -> None:
        self.send({"method": method, "params": params})

    def _receive(self, deadline: float) -> dict:
        try:
            message = self._messages.get(timeout=max(.001, deadline - time.monotonic()))
        except queue.Empty as error:
            raise TimeoutError("Timed out waiting for language server") from error
        if isinstance(message, BaseException):
            raise message
        if "method" in message and "id" in message:
            self.send({"id": message["id"], "error": {"code": -32601,
                       "message": "Client request unsupported by qualification harness"}})
        elif "method" in message:
            self.notifications.append(message)
        return message

    def request(self, method: str, params: dict, timeout: float = 30) -> object:
        self._next_id += 1
        request_id = self._next_id
        self.send({"id": request_id, "method": method, "params": params})
        deadline = time.monotonic() + timeout
        while True:
            message = self._receive(deadline)
            if message.get("id") == request_id and "method" not in message:
                if "error" in message:
                    raise RuntimeError(f"{method}: {message['error']}")
                return message.get("result")

    def diagnostics(self, uri: str, after: int = 0, timeout: float = 30) -> list:
        deadline = time.monotonic() + timeout
        while True:
            for message in self.notifications[after:]:
                if (message.get("method") == "textDocument/publishDiagnostics"
                        and message["params"]["uri"] == uri):
                    return message["params"]["diagnostics"]
            self._receive(deadline)

    def close(self) -> None:
        if self.process.poll() is None:
            try:
                self.request("shutdown", {}, timeout=10)
                self.notify("exit", {})
                self.process.wait(timeout=10)
            except (TimeoutError, EOFError, BrokenPipeError) as error:
                self._record("harness", {"shutdownFailure": str(error)})
                self.process.kill()
                self.process.wait(timeout=10)
        self._reader.join(timeout=5)
        self._trace.close()
        self._stderr.close()
