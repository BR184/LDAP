#!/usr/bin/env python3
"""Benchmark JWT and PAT authorization without persisting credential material."""

from __future__ import annotations

import argparse
import concurrent.futures
import getpass
import json
import statistics
import subprocess
import time
import urllib.error
import urllib.request
from datetime import datetime, timedelta
from typing import Any


def api_request(base_url: str, path: str, method: str = "GET", token: str | None = None,
                payload: dict[str, Any] | None = None) -> Any:
    headers = {"Accept": "application/json"}
    body = None
    if token:
        headers["Authorization"] = f"Bearer {token}"
    if payload is not None:
        headers["Content-Type"] = "application/json"
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    request = urllib.request.Request(
        f"{base_url.rstrip('/')}{path}",
        data=body,
        headers=headers,
        method=method,
    )
    try:
        with urllib.request.urlopen(request, timeout=20) as response:
            envelope = json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as error:
        detail = error.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"HTTP {error.code}: {detail}") from error
    if isinstance(envelope, dict) and envelope.get("success") is False:
        raise RuntimeError(envelope.get("message") or envelope.get("code") or "API request failed")
    return envelope.get("data") if isinstance(envelope, dict) and "success" in envelope else envelope


def mysql_status(container: str | None) -> dict[str, int] | None:
    if not container:
        return None
    command = [
        "docker", "exec", container, "sh", "-c",
        "mysql -u\"$MYSQL_USER\" -p\"$MYSQL_PASSWORD\" \"$MYSQL_DATABASE\" "
        "--batch --skip-column-names -e \"SHOW GLOBAL STATUS WHERE Variable_name "
        "IN ('Questions', 'Com_update')\" 2>/dev/null",
    ]
    completed = subprocess.run(command, check=True, capture_output=True, text=True)
    result: dict[str, int] = {}
    for line in completed.stdout.splitlines():
        fields = line.split()
        if len(fields) == 2:
            result[fields[0]] = int(fields[1])
    return result


def percentile(values: list[float], percentage: float) -> float:
    ordered = sorted(values)
    index = min(len(ordered) - 1, max(0, int(round((len(ordered) - 1) * percentage))))
    return ordered[index]


def benchmark(base_url: str, token: str, iterations: int, concurrency: int,
              mysql_container: str | None) -> dict[str, Any]:
    status_before = mysql_status(mysql_container)

    def invoke(_: int) -> float:
        started = time.perf_counter()
        api_request(base_url, "/api/v1/auth/me", token=token)
        return (time.perf_counter() - started) * 1000

    with concurrent.futures.ThreadPoolExecutor(max_workers=concurrency) as executor:
        latencies = list(executor.map(invoke, range(iterations)))

    status_after = mysql_status(mysql_container)
    question_delta = None
    update_delta = None
    if status_before is not None and status_after is not None:
        question_delta = max(0, status_after["Questions"] - status_before["Questions"] - 1)
        update_delta = max(0, status_after["Com_update"] - status_before["Com_update"])
    return {
        "requestCount": iterations,
        "concurrency": concurrency,
        "latencyMs": {
            "min": round(min(latencies), 2),
            "mean": round(statistics.fmean(latencies), 2),
            "p50": round(percentile(latencies, 0.50), 2),
            "p95": round(percentile(latencies, 0.95), 2),
            "max": round(max(latencies), 2),
        },
        "databaseQuestionsDelta": question_delta,
        "databaseQuestionsPerRequest": (
            round(question_delta / iterations, 2) if question_delta is not None else None
        ),
        "databaseUpdatesDelta": update_delta,
    }


def main() -> None:
    parser = argparse.ArgumentParser(description="Benchmark JWT and personal access token API authentication.")
    parser.add_argument("--base-url", default="http://127.0.0.1:8083")
    parser.add_argument("--username", default="admin")
    parser.add_argument("--password")
    parser.add_argument("--iterations", type=int, default=100)
    parser.add_argument("--concurrency", type=int, default=10)
    parser.add_argument("--mysql-container", default="corp-idm-mysql")
    args = parser.parse_args()
    password = args.password or getpass.getpass("密码: ")

    login = api_request(
        args.base_url,
        "/api/v1/auth/login",
        method="POST",
        payload={"loginId": args.username, "password": password},
    )
    jwt_token = login["accessToken"]
    permissions = api_request(
        args.base_url,
        "/api/v1/personal-access-tokens/available-permissions",
        token=jwt_token,
    )
    auth_me = next(
        (permission for permission in permissions if permission["permissionCode"] == "AUTH_ME"),
        None,
    )
    if auth_me is None:
        raise RuntimeError("Current account does not own AUTH_ME permission")

    created = api_request(
        args.base_url,
        "/api/v1/personal-access-tokens",
        method="POST",
        token=jwt_token,
        payload={
            "name": f"benchmark-{datetime.now().strftime('%Y%m%d-%H%M%S')}",
            "description": "temporary authentication benchmark",
            "expiresAt": (datetime.now() + timedelta(hours=1)).isoformat(timespec="seconds"),
            "permissionIds": [auth_me["id"]],
            "scopeMode": "FIXED",
        },
    )
    pat_id = created["token"]["id"]
    pat_secret = created["secret"]

    try:
        before = api_request(
            args.base_url,
            "/api/v1/personal-access-tokens",
            token=jwt_token,
        )
        jwt_result = benchmark(
            args.base_url, jwt_token, args.iterations, args.concurrency, args.mysql_container
        )
        pat_result = benchmark(
            args.base_url, pat_secret, args.iterations, args.concurrency, args.mysql_container
        )
        after = api_request(
            args.base_url,
            "/api/v1/personal-access-tokens",
            token=jwt_token,
        )
        before_item = next(item for item in before["items"] if item["id"] == pat_id)
        after_item = next(item for item in after["items"] if item["id"] == pat_id)
        print(json.dumps({
            "baseUrl": args.base_url,
            "jwt": jwt_result,
            "personalAccessToken": pat_result,
            "usageMetadataTransitionCount": int(
                before_item.get("lastUsedAt") != after_item.get("lastUsedAt")
            ),
            "usageWriteIntervalSeconds": 300,
        }, ensure_ascii=False, indent=2))
    finally:
        api_request(
            args.base_url,
            f"/api/v1/personal-access-tokens/{pat_id}",
            method="DELETE",
            token=jwt_token,
        )


if __name__ == "__main__":
    main()
