#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Render PlantUML .puml sources to PNG via the public PlantUML server.

No local JDK/Graphviz required: the source text is deflate-compressed and
base64-encoded with PlantUML's own alphabet, then fetched as an image from
https://www.plantuml.com/plantuml/{fmt}/{encoded}.

Usage:
    python puml_render.py                 # render every ../puml/*.puml -> ../png/
    python puml_render.py a.puml b.puml   # render selected files
    python puml_render.py --fmt svg       # also supports svg
"""
import sys
import os
import time
import zlib
import urllib.request
import urllib.error

SERVER = "https://www.plantuml.com/plantuml"
HERE = os.path.dirname(os.path.abspath(__file__))
PUML_DIR = os.path.normpath(os.path.join(HERE, "..", "puml"))
PNG_DIR = os.path.normpath(os.path.join(HERE, "..", "png"))


def encode6bit(b: int) -> str:
    if b < 10:
        return chr(48 + b)
    b -= 10
    if b < 26:
        return chr(65 + b)
    b -= 26
    if b < 26:
        return chr(97 + b)
    b -= 26
    if b == 0:
        return "-"
    if b == 1:
        return "_"
    return "?"


def deflate_and_encode(text: str) -> str:
    data = zlib.compress(text.encode("utf-8"), 9)[2:-4]  # raw deflate
    out = []
    i = 0
    n = len(data)
    while i < n:
        b1 = data[i]
        b2 = data[i + 1] if i + 1 < n else 0
        b3 = data[i + 2] if i + 2 < n else 0
        out.append(encode6bit(b1 >> 2))
        out.append(encode6bit(((b1 & 0x3) << 4) | (b2 >> 4)))
        out.append(encode6bit(((b2 & 0xF) << 2) | (b3 >> 6)))
        out.append(encode6bit(b3 & 0x3F))
        i += 3
    return "".join(out)


def render(path: str, fmt: str = "png") -> str:
    with open(path, "r", encoding="utf-8") as fh:
        source = fh.read()
    encoded = deflate_and_encode(source)
    url = f"{SERVER}/{fmt}/{encoded}"
    os.makedirs(PNG_DIR, exist_ok=True)
    out = os.path.join(PNG_DIR, os.path.splitext(os.path.basename(path))[0] + "." + fmt)
    req = urllib.request.Request(url, headers={"User-Agent": "puml-render/1.0"})
    last_err = None
    for attempt in range(3):
        try:
            with urllib.request.urlopen(req, timeout=90) as resp:
                payload = resp.read()
            if not payload.startswith(b"\x89PNG") and fmt == "png":
                raise RuntimeError("server did not return a PNG: " + payload[:300].decode("utf-8", "replace"))
            with open(out, "wb") as fh:
                fh.write(payload)
            return out
        except (urllib.error.URLError, RuntimeError, TimeoutError) as exc:
            last_err = exc
            time.sleep(2 * (attempt + 1))
    raise RuntimeError(f"render failed for {os.path.basename(path)}: {last_err}")


def main(argv):
    fmt = "png"
    files = []
    i = 0
    while i < len(argv):
        if argv[i] == "--fmt":
            i += 1
            fmt = argv[i]
        else:
            files.append(argv[i])
        i += 1
    if not files:
        files = sorted(
            os.path.join(PUML_DIR, f) for f in os.listdir(PUML_DIR) if f.endswith(".puml")
        )
    ok = 0
    for f in files:
        out = render(f, fmt)
        size = os.path.getsize(out)
        print(f"[OK] {os.path.basename(f)} -> {os.path.basename(out)} ({size/1024:.1f} KB)")
        ok += 1
    print(f"{ok} diagram(s) rendered into {PNG_DIR}")


if __name__ == "__main__":
    main(sys.argv[1:])
