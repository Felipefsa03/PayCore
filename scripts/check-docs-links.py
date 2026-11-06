#!/usr/bin/env python3
"""Valida links relativos e âncoras de arquivos Markdown do repositório.

Uso:
    python3 scripts/check-docs-links.py [raiz]

Ignora links externos (http/https/mailto/tel), blocos de código cercados e imagens.
Retorna código 1 quando encontra um arquivo ou âncora inexistente.
"""

from __future__ import annotations

import re
import sys
from pathlib import Path
from urllib.parse import unquote

LINK = re.compile(r"!?\[[^\]]*\]\(([^)]+)\)")
REFERENCE = re.compile(r"^\s*\[[^\]]+\]:\s*(\S+)")
FENCE = re.compile(r"^\s*(```|~~~)")
EXCLUDED_DIRS = {".git", "node_modules", "target", "dist", "build"}


def strip_code_fences(text: str) -> str:
    lines: list[str] = []
    inside = False
    for line in text.splitlines():
        if FENCE.match(line):
            inside = not inside
            lines.append("")
            continue
        lines.append("" if inside else line)
    return "\n".join(lines)


def slugify(heading: str) -> str:
    text = heading.strip().lower()
    text = re.sub(r"[^\w\s-]", "", text, flags=re.UNICODE)
    text = re.sub(r"\s+", "-", text)
    return re.sub(r"-+", "-", text).strip("-")


def anchors(path: Path) -> set[str]:
    result: set[str] = set()
    counts: dict[str, int] = {}
    for line in strip_code_fences(path.read_text(encoding="utf-8")).splitlines():
        match = re.match(r"^(#{1,6})\s+(.*)$", line)
        if not match:
            continue
        slug = slugify(match.group(2))
        if slug in counts:
            counts[slug] += 1
            result.add(f"{slug}-{counts[slug]}")
        else:
            counts[slug] = 0
            result.add(slug)
    return result


def local_target(raw: str) -> tuple[str, str] | None:
    target = raw.strip()
    if target.startswith("<") and target.endswith(">"):
        target = target[1:-1]
    if not target or target.startswith(("http://", "https://", "mailto:", "tel:", "data:")):
        return None
    if target.startswith("#"):
        return "", unquote(target[1:])
    path, _, anchor = target.partition("#")
    return unquote(path), unquote(anchor)


def main() -> int:
    root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
    broken: list[str] = []
    checked = 0

    files = sorted(
        path
        for path in root.rglob("*.md")
        if not any(part in EXCLUDED_DIRS for part in path.relative_to(root).parts)
    )

    for file in files:
        checked += 1
        text = strip_code_fences(file.read_text(encoding="utf-8"))
        targets = [match.group(1) for match in LINK.finditer(text)]
        targets += [
            match.group(1)
            for match in (REFERENCE.match(line) for line in text.splitlines())
            if match
        ]
        for raw in targets:
            parsed = local_target(raw)
            if parsed is None:
                continue
            relative, anchor = parsed
            destination = file if relative == "" else (file.parent / relative)
            destination = destination.resolve()
            if not destination.exists():
                broken.append(f"{file.relative_to(root)} -> {raw} (arquivo não existe)")
                continue
            if anchor and destination.is_file() and destination.suffix == ".md":
                if anchor not in anchors(destination):
                    broken.append(f"{file.relative_to(root)} -> {raw} (âncora não existe)")

    if broken:
        print(f"{len(broken)} link(s) quebrado(s) em {checked} arquivo(s):", file=sys.stderr)
        for item in broken:
            print(f"  - {item}", file=sys.stderr)
        return 1

    print(f"OK: {checked} arquivo(s) Markdown, todos os links relativos resolvem.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
