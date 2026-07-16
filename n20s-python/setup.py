"""Shim for editable installs (`pip install -e`) with pip < 21.3.

All real metadata lives in pyproject.toml; older pips just need a
setuptools entry point to fall back to legacy editable mode.
"""

from setuptools import setup

setup()
