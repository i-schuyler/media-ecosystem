#!/usr/bin/env python3
"""Bootstrap the disposable harness without installing a package."""

from __future__ import annotations

from pathlib import Path
import sys


SPIKE_ROOT = Path(__file__).resolve().parents[1]
sys.dont_write_bytecode = True
sys.path.insert(0, str(SPIKE_ROOT / "src"))

from shared_core_spike.cli import main  # noqa: E402


if __name__ == "__main__":
    sys.exit(main())
