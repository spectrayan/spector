# Copyright 2026 Spectrayan — Apache 2.0
"""
Synchronous REST transport using Python's standard urllib (zero external dependencies).
"""

from __future__ import annotations
import json
import logging
from typing import Any, Dict, Iterator, Optional
import urllib.error
import urllib.parse
import urllib.request

from spector_client.config import ClientConfig
from spector_client.exceptions import (
    MemoryNotFoundError,
    SpectorAuthError,
    SpectorClientError,
    SpectorServerError,
    SpectorValidationError,
    TransportError,
)
from spector_client.models import SseEvent
from spector_client.transports.base import BaseTransport

logger = logging.getLogger("spector_client.transport.rest")


class RestTransport(BaseTransport):
    """Zero-dependency HTTP REST transport for Spector Synapse."""

    def __init__(self, config: ClientConfig):
        self.config = config
        self._base_url = config.base_url.rstrip("/")

    def request(
        self,
        method: str,
        path: str,
        query_params: Optional[Dict[str, Any]] = None,
        body: Optional[Any] = None,
    ) -> Any:
        url = f"{self._base_url}/{path.lstrip('/')}"
        if query_params:
            filtered_params = {k: v for k, v in query_params.items() if v is not None}
            if filtered_params:
                url += "?" + urllib.parse.urlencode(filtered_params)

        headers = self.config.get_auth_headers()
        headers["Accept"] = "application/json"
        data: Optional[bytes] = None

        if body is not None:
            headers["Content-Type"] = "application/json; charset=utf-8"
            data = json.dumps(body).encode("utf-8")

        req = urllib.request.Request(url, data=data, headers=headers, method=method.upper())

        try:
            with urllib.request.urlopen(req, timeout=self.config.read_timeout) as response:
                status = response.status
                content_type = response.headers.get("Content-Type", "")
                resp_bytes = response.read()

                if not resp_bytes or status == 204:
                    return None

                if "application/json" in content_type:
                    return json.loads(resp_bytes.decode("utf-8"))
                return resp_bytes.decode("utf-8")

        except urllib.error.HTTPError as e:
            error_body = ""
            details: Dict[str, Any] = {}
            try:
                error_body = e.read().decode("utf-8")
                if error_body:
                    details = json.loads(error_body)
            except Exception:
                pass

            msg = details.get("message") or details.get("detail") or error_body or str(e)

            if e.code == 404:
                mem_id = query_params.get("id") if query_params else None
                if not mem_id and "/" in path:
                    mem_id = path.split("/")[-1]
                raise MemoryNotFoundError(memory_id=mem_id or "unknown", message=msg)
            elif e.code in (401, 403):
                raise SpectorAuthError(message=msg, status_code=e.code)
            elif e.code == 400:
                raise SpectorValidationError(message=msg, details=details)
            elif e.code >= 500:
                raise SpectorServerError(message=msg, status_code=e.code, details=details)
            else:
                raise SpectorClientError(message=msg, status_code=e.code, details=details)

        except urllib.error.URLError as e:
            raise TransportError(f"Failed to connect to Spector server at {self._base_url}: {e.reason}")
        except Exception as e:
            raise TransportError(f"Unexpected transport failure: {e}")

    def stream_events(
        self,
        path: str,
        query_params: Optional[Dict[str, Any]] = None,
    ) -> Iterator[SseEvent]:
        url = f"{self._base_url}/{path.lstrip('/')}"
        if query_params:
            filtered_params = {k: v for k, v in query_params.items() if v is not None}
            if filtered_params:
                url += "?" + urllib.parse.urlencode(filtered_params)

        headers = self.config.get_auth_headers()
        headers["Accept"] = "text/event-stream"
        headers["Cache-Control"] = "no-cache"

        req = urllib.request.Request(url, headers=headers, method="GET")

        try:
            response = urllib.request.urlopen(req, timeout=None)
        except Exception as e:
            raise TransportError(f"Failed to open event stream at {url}: {e}")

        event_name = "message"
        data_lines: list[str] = []
        event_id: Optional[str] = None
        retry_val: Optional[int] = None

        try:
            for raw_line in response:
                line = raw_line.decode("utf-8").rstrip("\r\n")

                if not line:
                    # Dispatch event on empty line delimiter
                    if data_lines:
                        full_data = "\n".join(data_lines)
                        parsed_data: Any = full_data
                        try:
                            parsed_data = json.loads(full_data)
                        except Exception:
                            pass

                        yield SseEvent(
                            event=event_name,
                            data=parsed_data,
                            id=event_id,
                            retry=retry_val,
                        )
                    # Reset event state
                    event_name = "message"
                    data_lines = []
                    event_id = None
                    retry_val = None
                    continue

                if line.startswith(":"):
                    # Comment / keepalive
                    continue

                if ":" in line:
                    field_name, field_val = line.split(":", 1)
                    if field_val.startswith(" "):
                        field_val = field_val[1:]

                    if field_name == "event":
                        event_name = field_val
                    elif field_name == "data":
                        data_lines.append(field_val)
                    elif field_name == "id":
                        event_id = field_val
                    elif field_name == "retry":
                        try:
                            retry_val = int(field_val)
                        except ValueError:
                            pass
        finally:
            try:
                response.close()
            except Exception:
                pass

    def close(self) -> None:
        """RestTransport is stateless and does not maintain persistent pool locks."""
        pass
