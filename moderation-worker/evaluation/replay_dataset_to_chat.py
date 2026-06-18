import argparse
import csv
import json
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

from websocket import create_connection


EVALUATION_DIR = Path(__file__).resolve().parent
DEFAULT_DATASET = EVALUATION_DIR / "moderation_dataset_200.tsv"


def parse_args():
    parser = argparse.ArgumentParser(
        description="Replay moderation dataset messages into a real ChatGuard chat room."
    )
    parser.add_argument(
        "--dataset",
        type=Path,
        default=DEFAULT_DATASET,
        help="TSV dataset path.",
    )
    parser.add_argument(
        "--backend",
        default="http://localhost:8080",
        help="Backend base URL.",
    )
    parser.add_argument(
        "--ws",
        default=None,
        help="WebSocket URL. Default is derived from --backend.",
    )
    parser.add_argument("--username", required=True)
    parser.add_argument("--password", required=True)
    parser.add_argument("--room-id", type=int, default=1)
    parser.add_argument(
        "--delay",
        type=float,
        default=0.5,
        help="Seconds between messages. Use 0 for fast replay.",
    )
    parser.add_argument(
        "--limit",
        type=int,
        default=0,
        help="Maximum number of rows to replay. 0 means all rows.",
    )
    parser.add_argument(
        "--category",
        default=None,
        help="Replay only one category exactly matching the TSV category value.",
    )
    parser.add_argument(
        "--prefix",
        default="",
        help="Optional text prefix added to every message.",
    )
    return parser.parse_args()


def read_dataset(path, category, limit):
    with path.open("r", encoding="utf-8", newline="") as file:
        rows = list(csv.DictReader(file, delimiter="\t"))

    if category:
        rows = [row for row in rows if row["category"] == category]
    if limit and limit > 0:
        rows = rows[:limit]
    return rows


def login(backend, username, password):
    url = urllib.parse.urljoin(backend.rstrip("/") + "/", "api/login")
    body = json.dumps({"username": username, "password": password}).encode("utf-8")
    request = urllib.request.Request(
        url,
        data=body,
        headers={"Content-Type": "application/json"},
        method="POST",
    )

    try:
        with urllib.request.urlopen(request, timeout=10) as response:
            payload = json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as exc:
        detail = exc.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"Login failed: HTTP {exc.code} {detail}") from exc

    token = payload.get("token")
    if not token:
        raise RuntimeError(f"Login response does not contain token: {payload}")
    return payload


def build_ws_url(backend, explicit_ws, token, room_id):
    if explicit_ws:
        base = explicit_ws
    else:
        parsed = urllib.parse.urlparse(backend)
        scheme = "wss" if parsed.scheme == "https" else "ws"
        netloc = parsed.netloc or "localhost:8080"
        base = f"{scheme}://{netloc}/ws"

    query = urllib.parse.urlencode({"token": token, "room_id": room_id})
    return f"{base}?{query}"


def send_chat(ws, room_id, content):
    payload = {
        "type": "chat.send",
        "payload": {
            "room_id": room_id,
            "content": content,
        },
    }
    ws.send(json.dumps(payload, ensure_ascii=False))


def main():
    args = parse_args()
    rows = read_dataset(args.dataset, args.category, args.limit)
    if not rows:
        raise RuntimeError("No dataset rows to replay.")

    auth = login(args.backend, args.username, args.password)
    ws_url = build_ws_url(args.backend, args.ws, auth["token"], args.room_id)

    print(f"dataset={args.dataset}")
    print(f"backend={args.backend}")
    print(f"room_id={args.room_id}")
    print(f"user={auth.get('display_name', args.username)}")
    print(f"rows={len(rows)}")
    print(f"delay={args.delay}")

    ws = create_connection(ws_url, timeout=10)
    try:
        for index, row in enumerate(rows, start=1):
            message = f"{args.prefix}{row['message']}"
            send_chat(ws, args.room_id, message)
            print(
                f"[{index:03d}/{len(rows):03d}] "
                f"expected={row['expected_action']} "
                f"category={row['category']} "
                f"message={message}"
            )
            if args.delay > 0:
                time.sleep(args.delay)
    finally:
        ws.close()


if __name__ == "__main__":
    main()
