import tempfile
import unittest
from pathlib import Path

from coverage_summary import merged_line_counts


class MergedLineCoverageTest(unittest.TestCase):
    def report(self, directory: str, filename: str, contents: str) -> Path:
        path = Path(directory) / filename
        path.write_text(contents, encoding="utf-8")
        return path

    def test_merges_duplicate_lines_using_the_union_of_hits(self) -> None:
        source = '<report><package name="com/app"><sourcefile name="Home.kt"><line nr="1" mi="1" ci="0"/><line nr="2" mi="0" ci="2"/></sourcefile></package></report>'
        with tempfile.TemporaryDirectory() as directory:
            first = self.report(directory, "unit.xml", source)
            second = self.report(
                directory,
                "android.xml",
                source.replace('nr="1" mi="1" ci="0"', 'nr="1" mi="0" ci="1"'),
            )
            covered, total = merged_line_counts([first, second])
        self.assertEqual((2, 2), (covered, total))

    def test_counts_uncovered_source_lines(self) -> None:
        source = '<report><package name="com/app"><sourcefile name="Home.kt"><line nr="1" mi="1" ci="0"/><line nr="2" mi="0" ci="2"/></sourcefile></package></report>'
        with tempfile.TemporaryDirectory() as directory:
            report = self.report(directory, "report.xml", source)
            covered, total = merged_line_counts([report])
        self.assertEqual((1, 2), (covered, total))

    def test_same_filename_in_different_packages_is_counted_separately(self) -> None:
        source = '<report><package name="com/first"><sourcefile name="Page.kt"><line nr="1" mi="0" ci="1"/></sourcefile></package><package name="com/second"><sourcefile name="Page.kt"><line nr="1" mi="0" ci="1"/></sourcefile></package></report>'
        with tempfile.TemporaryDirectory() as directory:
            report = self.report(directory, "report.xml", source)
            covered, total = merged_line_counts([report])
        self.assertEqual((2, 2), (covered, total))

    def test_reports_can_have_missing_instruction_hit_counts(self) -> None:
        source = '<report><package name="com/app"><sourcefile name="Page.kt"><line nr="1" mi="1"/></sourcefile></package></report>'
        with tempfile.TemporaryDirectory() as directory:
            report = self.report(directory, "report.xml", source)
            covered, total = merged_line_counts([report])
        self.assertEqual((0, 1), (covered, total))


if __name__ == "__main__":
    unittest.main()
