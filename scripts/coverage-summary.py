#!/usr/bin/env python3
"""Resume os relatórios JaCoCo por módulo.

Uso:
    python3 scripts/coverage-summary.py [raiz]

Lê todos os `target/site/jacoco/jacoco.csv` sob a raiz e imprime linhas e
branches cobertas por grupo (módulo Maven) e o total do build.
"""

from __future__ import annotations

import csv
import sys
from collections import defaultdict
from pathlib import Path

EXCLUDED = {".git", "node_modules"}


def percentage(covered: int, missed: int) -> str:
    total = covered + missed
    return "n/a" if total == 0 else f"{100 * covered / total:.1f}%"


def main() -> int:
    root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
    totals: dict[str, dict[str, int]] = defaultdict(
        lambda: {"line_covered": 0, "line_missed": 0, "branch_covered": 0, "branch_missed": 0}
    )

    reports = [
        path
        for path in root.rglob("jacoco.csv")
        if not any(part in EXCLUDED for part in path.relative_to(root).parts)
    ]
    if not reports:
        print(f"nenhum jacoco.csv encontrado sob {root}", file=sys.stderr)
        return 1

    for report in reports:
        with report.open(newline="", encoding="utf-8") as handle:
            for row in csv.DictReader(handle):
                group = row["GROUP"]
                totals[group]["line_covered"] += int(row["LINE_COVERED"])
                totals[group]["line_missed"] += int(row["LINE_MISSED"])
                totals[group]["branch_covered"] += int(row["BRANCH_COVERED"])
                totals[group]["branch_missed"] += int(row["BRANCH_MISSED"])

    print(f"{'Módulo':32} {'Linhas':>18} {'Branches':>18}")
    aggregate = {"line_covered": 0, "line_missed": 0, "branch_covered": 0, "branch_missed": 0}
    for group in sorted(totals):
        data = totals[group]
        for key in aggregate:
            aggregate[key] += data[key]
        print(
            f"{group:32} "
            f"{data['line_covered']:>4}/{data['line_covered'] + data['line_missed']:<4} "
            f"{percentage(data['line_covered'], data['line_missed']):>6}   "
            f"{data['branch_covered']:>4}/{data['branch_covered'] + data['branch_missed']:<4} "
            f"{percentage(data['branch_covered'], data['branch_missed']):>6}"
        )
    print(
        f"{'TOTAL':32} "
        f"{aggregate['line_covered']:>4}/{aggregate['line_covered'] + aggregate['line_missed']:<4} "
        f"{percentage(aggregate['line_covered'], aggregate['line_missed']):>6}   "
        f"{aggregate['branch_covered']:>4}/{aggregate['branch_covered'] + aggregate['branch_missed']:<4} "
        f"{percentage(aggregate['branch_covered'], aggregate['branch_missed']):>6}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
