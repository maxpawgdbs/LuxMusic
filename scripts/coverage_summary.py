#!/usr/bin/env python3
"""Merge Android Gradle's unit and instrumentation JaCoCo XML line coverage."""

from __future__ import annotations

import os
from pathlib import Path
import xml.etree.ElementTree as ET


def merged_line_counts(report_paths: list[Path]) -> tuple[int, int]:
    hits: dict[tuple[str, str, str], bool] = {}
    for report_path in report_paths:
        root = ET.parse(report_path).getroot()
        for package in root.findall("package"):
            package_name = package.attrib["name"]
            for source in package.findall("sourcefile"):
                source_name = source.attrib["name"]
                for line in source.findall("line"):
                    key = (package_name, source_name, line.attrib["nr"])
                    hits[key] = hits.get(key, False) or int(line.attrib.get("ci", "0")) > 0

    covered = sum(hits.values())
    return covered, len(hits)


def main() -> None:
    root = Path(__file__).resolve().parents[1]
    reports = [
        root / "app/build/reports/coverage/test/debug/report.xml",
        root / "app/build/reports/coverage/androidTest/debug/connected/report.xml",
    ]
    missing = [path for path in reports if not path.is_file()]
    if missing:
        raise SystemExit("Coverage report missing: " + ", ".join(map(str, missing)))

    covered, total = merged_line_counts(reports)
    if total == 0:
        raise SystemExit("Coverage reports contain no source lines.")

    percent = covered * 100 / total
    status = "MET" if percent >= 90 else "NOT MET"
    message = (
        f"Combined unit + Android line coverage: {covered}/{total} "
        f"({percent:.2f}%). 90% target: {status}."
    )
    print(message)
    summary_path = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary_path:
        with Path(summary_path).open("a", encoding="utf-8") as summary:
            summary.write(f"### Test coverage\n\n{message}\n")


if __name__ == "__main__":
    main()
