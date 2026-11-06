#!/usr/bin/env python3
"""Verifica se cada ameaça do modelo STRIDE tem dono, mitigação e risco residual.

Uso:
    python3 scripts/check-threat-model.py docs/security/modelo-ameacas.md

A tabela de ameaças deve ter as colunas: ID, Ameaça, Dono, Mitigação e
Risco residual aceito. O script falha se alguma linha estiver incompleta.
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

REQUIRED = ("ID", "Dono", "Mitigação", "Risco residual aceito")
ID = re.compile(r"^T-\d{2}$")


def cells(line: str) -> list[str]:
    return [cell.strip() for cell in line.strip().strip("|").split("|")]


def main() -> int:
    path = Path(sys.argv[1] if len(sys.argv) > 1 else "docs/security/modelo-ameacas.md")
    lines = path.read_text(encoding="utf-8").splitlines()
    threats: list[tuple[list[str], list[str]]] = []

    index = 0
    while index < len(lines):
        header = cells(lines[index])
        if all(name in header for name in REQUIRED):
            index += 2  # header + separator
            while index < len(lines) and lines[index].strip().startswith("|"):
                threats.append((header, cells(lines[index])))
                index += 1
            continue
        index += 1

    problems: list[str] = []
    if not threats:
        problems.append(f"nenhuma tabela de ameaças encontrada em {path}")
    for header, row in threats:
        position = {name: header.index(name) for name in REQUIRED if name in header}
        threat_id = row[position["ID"]] if len(row) > position["ID"] else "?"
        if not ID.match(threat_id):
            problems.append(f"{threat_id}: ID fora do padrão T-NN")
            continue
        if len(row) != len(header):
            problems.append(f"{threat_id}: {len(row)} colunas (esperado {len(header)})")
        for name in REQUIRED:
            value = row[position[name]] if len(row) > position[name] else ""
            if not value:
                problems.append(f"{threat_id}: coluna obrigatória vazia ({name})")

    if problems:
        print(f"{len(problems)} problema(s) no modelo de ameaças:", file=sys.stderr)
        for problem in problems:
            print(f"  - {problem}", file=sys.stderr)
        return 1

    print(f"OK: {len(threats)} ameaças com dono, mitigação e risco residual aceito.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
