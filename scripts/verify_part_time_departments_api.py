#!/usr/bin/env python3
"""Verify the part-time department contract returned by GET /api/v1/users.

The script is read-only. It authenticates, lists users, and validates either
the legacy code/name arrays or the extended structured department contract.
"""

from __future__ import annotations

import argparse
import getpass
import json
import sys
from collections.abc import Mapping
from dataclasses import asdict, dataclass
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.parse import urlsplit
from urllib.request import Request, urlopen


DEFAULT_API_BASE_URL = "http://127.0.0.1:8083"
LEGACY_REQUIRED_FIELDS = ("partTimeDeptCodes", "partTimeDeptNames")
STRUCTURED_DEPARTMENTS_FIELD = "partTimeDepartments"
LEGACY_CONTRACT = "legacy-arrays"
EXTENDED_CONTRACT = "extended-arrays-and-objects"
MIXED_CONTRACT = "mixed-response-shape"
MAX_DISPLAYED_VIOLATIONS = 50


def configure_console_encoding() -> None:
    """Keep Chinese diagnostics readable in Windows terminals and pipelines."""
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    if hasattr(sys.stderr, "reconfigure"):
        sys.stderr.reconfigure(encoding="utf-8")


@dataclass(frozen=True)
class ContractViolation:
    user_id: str
    rule: str
    detail: str


class ApiRequestError(RuntimeError):
    """Raised when an HTTP request cannot produce a successful API response."""


def parse_arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="校验用户 API 的兼职部门旧数组接口或新版结构化接口。"
    )
    parser.add_argument("--api-base-url", default=DEFAULT_API_BASE_URL, help="API 地址")
    parser.add_argument("--login-id", default="admin", help="登录账号，默认 admin")
    parser.add_argument(
        "--password-stdin",
        action="store_true",
        help="从标准输入读取密码，适用于自动化；密码不能含换行。",
    )
    parser.add_argument(
        "--timeout-seconds",
        type=int,
        default=30,
        help="单个 HTTP 请求超时秒数，默认 30。",
    )
    parser.add_argument(
        "--max-details",
        type=int,
        default=50,
        help="最多输出多少个含兼职部门的用户明细，默认 50；传 0 输出全部。",
    )
    arguments = parser.parse_args()
    if arguments.timeout_seconds < 1 or arguments.timeout_seconds > 300:
        parser.error("--timeout-seconds 必须在 1 到 300 之间。")
    if arguments.max_details < 0:
        parser.error("--max-details 不能小于 0。")
    return arguments


def normalize_base_url(value: str) -> str:
    normalized = value.rstrip("/")
    parsed = urlsplit(normalized)
    if parsed.scheme not in {"http", "https"} or not parsed.netloc:
        raise ValueError("--api-base-url 必须是完整的 http 或 https 地址。")
    return normalized


def read_password(from_stdin: bool) -> str:
    password = sys.stdin.readline().rstrip("\r\n") if from_stdin else getpass.getpass("密码: ")
    if not password:
        raise ValueError("密码不能为空。")
    return password


def request_json(
    url: str,
    method: str,
    timeout_seconds: int,
    payload: Mapping[str, Any] | None = None,
    access_token: str | None = None,
) -> Mapping[str, Any]:
    body = None if payload is None else json.dumps(payload, ensure_ascii=False).encode("utf-8")
    headers = {"Accept": "application/json"}
    if body is not None:
        headers["Content-Type"] = "application/json"
    if access_token:
        headers["Authorization"] = f"Bearer {access_token}"

    request = Request(url, data=body, headers=headers, method=method)
    try:
        with urlopen(request, timeout=timeout_seconds) as response:
            raw_response = response.read().decode("utf-8")
    except HTTPError as error:
        response_body = error.read().decode("utf-8", errors="replace")
        raise ApiRequestError(f"{method} {url} 返回 HTTP {error.code}: {response_body}") from error
    except URLError as error:
        raise ApiRequestError(f"无法访问 {url}: {error.reason}") from error

    try:
        response_data = json.loads(raw_response)
    except json.JSONDecodeError as error:
        raise ApiRequestError(f"{method} {url} 未返回合法 JSON。") from error
    if not isinstance(response_data, Mapping):
        raise ApiRequestError(f"{method} {url} 返回的 JSON 根节点不是对象。")
    return response_data


def require_success(response: Mapping[str, Any], operation: str) -> Any:
    if response.get("success") is not True:
        message = response.get("message", "接口未返回成功结果")
        raise ApiRequestError(f"{operation}失败: {message}")
    return response.get("data")


def detect_contract(users: list[Any]) -> str:
    structured_field_presence = [
        isinstance(user, Mapping) and STRUCTURED_DEPARTMENTS_FIELD in user for user in users
    ]
    if not any(structured_field_presence):
        return LEGACY_CONTRACT
    if all(structured_field_presence):
        return EXTENDED_CONTRACT
    return MIXED_CONTRACT


def validate_part_time_departments(
    users: list[Any], contract: str
) -> list[ContractViolation]:
    violations: list[ContractViolation] = []
    required_fields = LEGACY_REQUIRED_FIELDS
    if contract != LEGACY_CONTRACT:
        required_fields = (*LEGACY_REQUIRED_FIELDS, STRUCTURED_DEPARTMENTS_FIELD)

    for user in users:
        if not isinstance(user, Mapping):
            violations.append(ContractViolation("<unknown>", "INVALID_USER", "用户项不是对象"))
            continue

        user_id = str(user.get("userId", "<unknown>"))
        missing_fields = [field for field in required_fields if field not in user]
        if missing_fields:
            violations.append(
                ContractViolation(user_id, "MISSING_FIELD", ", ".join(missing_fields))
            )
            continue

        codes = user["partTimeDeptCodes"]
        names = user["partTimeDeptNames"]
        departments = user.get(STRUCTURED_DEPARTMENTS_FIELD)
        values_to_validate = (codes, names) if contract == LEGACY_CONTRACT else (codes, names, departments)
        if not all(isinstance(value, list) for value in values_to_validate):
            violations.append(
                ContractViolation(user_id, "INVALID_FIELD_TYPE", "兼职部门字段必须都是数组")
            )
            continue
        if len(codes) != len(names):
            violations.append(
                ContractViolation(
                    user_id,
                    "LENGTH_MISMATCH",
                    f"codes={len(codes)}, names={len(names)}",
                )
            )
            continue
        if contract != LEGACY_CONTRACT and len(codes) != len(departments):
            violations.append(
                ContractViolation(
                    user_id,
                    "LENGTH_MISMATCH",
                    f"codes={len(codes)}, names={len(names)}, departments={len(departments)}",
                )
            )
            continue

        seen_codes: set[str] = set()
        for index, code in enumerate(codes):
            name = names[index]
            if not isinstance(code, str) or not code or code != code.strip():
                violations.append(
                    ContractViolation(user_id, "INVALID_CODE", f"index={index}, code={code!r}")
                )
                continue
            if code in seen_codes:
                violations.append(
                    ContractViolation(user_id, "DUPLICATE_CODE", f"index={index}, code={code!r}")
                )
            seen_codes.add(code)
            if not isinstance(name, str):
                violations.append(
                    ContractViolation(user_id, "INVALID_NAME", f"index={index}, name={name!r}")
                )

            if contract == LEGACY_CONTRACT:
                continue

            department = departments[index]
            if not isinstance(department, Mapping):
                violations.append(
                    ContractViolation(user_id, "INVALID_DEPARTMENT", f"index={index} 不是对象")
                )
                continue

            department_code = department.get("deptCode")
            department_name = department.get("deptName")
            if code != department_code:
                violations.append(
                    ContractViolation(
                        user_id,
                        "ORDER_OR_CODE_MISMATCH",
                        f"index={index}, legacyCode={code!r}, objectCode={department_code!r}",
                    )
                )
            expected_name = department_name if isinstance(department_name, str) and department_name.strip() else department_code
            if name != expected_name:
                violations.append(
                    ContractViolation(
                        user_id,
                        "NAME_MISMATCH",
                        f"index={index}, legacyName={name!r}, expectedName={expected_name!r}",
                    )
                )
    return violations


def build_part_time_department_details(
    users: list[Any], contract: str, max_details: int
) -> tuple[int, list[dict[str, Any]], bool]:
    matching_users: list[dict[str, Any]] = []
    for user in users:
        if not isinstance(user, Mapping):
            continue
        codes = user.get("partTimeDeptCodes")
        if not isinstance(codes, list) or not codes:
            continue
        names = user.get("partTimeDeptNames")
        detail = {
            "userId": user.get("userId"),
            "realName": user.get("realName"),
            "partTimeDeptCodes": codes,
            "partTimeDeptNames": names,
            "inspectionCommaJoinedCodes": ",".join(map(str, codes)),
            "inspectionCommaJoinedNames": ",".join(map(str, names)) if isinstance(names, list) else None,
        }
        if contract == EXTENDED_CONTRACT:
            detail[STRUCTURED_DEPARTMENTS_FIELD] = user.get(STRUCTURED_DEPARTMENTS_FIELD)
        matching_users.append(detail)

    details = matching_users if max_details == 0 else matching_users[:max_details]
    return len(matching_users), details, len(details) < len(matching_users)


def main() -> int:
    configure_console_encoding()
    arguments = parse_arguments()
    try:
        base_url = normalize_base_url(arguments.api_base_url)
        password = read_password(arguments.password_stdin)
        login_data = require_success(
            request_json(
                f"{base_url}/api/v1/auth/login",
                "POST",
                arguments.timeout_seconds,
                payload={"loginId": arguments.login_id, "password": password},
            ),
            "登录",
        )
        if not isinstance(login_data, Mapping) or not isinstance(login_data.get("accessToken"), str):
            raise ApiRequestError("登录成功响应缺少访问令牌。")
        users_data = require_success(
            request_json(
                f"{base_url}/api/v1/users",
                "GET",
                arguments.timeout_seconds,
                access_token=login_data["accessToken"],
            ),
            "获取用户列表",
        )
        if not isinstance(users_data, list):
            raise ApiRequestError("用户列表响应 data 不是数组。")
    except (ApiRequestError, ValueError) as error:
        print(f"校验无法执行: {error}", file=sys.stderr)
        return 2

    contract = detect_contract(users_data)
    violations = validate_part_time_departments(users_data, contract)
    if contract == MIXED_CONTRACT:
        violations.append(
            ContractViolation(
                "<response>",
                "MIXED_CONTRACT",
                "同一用户列表响应中，部分用户返回了 partTimeDepartments，部分用户未返回。",
            )
        )
    users_with_part_time_departments, details, details_truncated = build_part_time_department_details(
        users_data, contract, arguments.max_details
    )
    response_format = (
        "legacy JSON arrays; partTimeDepartments is not part of this API version"
        if contract == LEGACY_CONTRACT
        else "extended JSON arrays and structured objects; the API does not use a delimiter"
    )
    summary = {
        "apiBaseUrl": base_url,
        "userCount": len(users_data),
        "usersWithPartTimeDepartments": users_with_part_time_departments,
        "contractViolationCount": len(violations),
        "detectedContract": contract,
        "partTimeDepartmentResponseFormat": response_format,
        "alignmentRule": (
            "partTimeDeptCodes[i] and partTimeDeptNames[i] describe the same department"
            if contract == LEGACY_CONTRACT
            else "partTimeDeptCodes[i], partTimeDeptNames[i], and partTimeDepartments[i] describe the same department"
        ),
        "detailCount": len(details),
        "detailsTruncated": details_truncated,
    }
    print(json.dumps(summary, ensure_ascii=False, indent=2))
    if contract == LEGACY_CONTRACT:
        print("兼职部门明细（旧接口不返回 partTimeDepartments；逗号拼接字段仅用于人工核对）:")
    else:
        print("兼职部门明细（逗号拼接字段仅用于人工核对，非 API 返回字段）:")
    print(json.dumps(details, ensure_ascii=False, indent=2))

    if violations:
        print("兼职部门 API 契约校验失败。", file=sys.stderr)
        print(
            json.dumps(
                [asdict(violation) for violation in violations[:MAX_DISPLAYED_VIOLATIONS]],
                ensure_ascii=False,
                indent=2,
            ),
            file=sys.stderr,
        )
        if len(violations) > MAX_DISPLAYED_VIOLATIONS:
            print(f"仅显示前 {MAX_DISPLAYED_VIOLATIONS} 项，共 {len(violations)} 项异常。", file=sys.stderr)
        return 1

    print("兼职部门 API 契约校验通过。")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
