# infra/modules/app/placeholder_src/handler.py
"""Placeholder Lambda handlers so Terraform can plan before the real app exists (Phase 1)."""
from __future__ import annotations

import json
import logging
import os
from typing import Any

logger = logging.getLogger()
logger.setLevel(os.environ.get("LOG_LEVEL", "INFO"))


def lambda_handler(event: dict[str, Any], context: Any) -> dict[str, Any]:
    """API entry point (Function URL). Returns a health response only."""
    logger.info("placeholder api invoked")
    return {
        "statusCode": 200,
        "headers": {"content-type": "application/json"},
        "body": json.dumps({"status": "placeholder", "service": "weekend2-api"}),
    }


def worker_handler(event: dict[str, Any], context: Any) -> dict[str, Any]:
    """Scheduled jobs entry point (EventBridge Scheduler)."""
    job = event.get("job", "unknown") if isinstance(event, dict) else "unknown"
    logger.info("placeholder worker job=%s", job)
    return {"job": job, "status": "placeholder"}
