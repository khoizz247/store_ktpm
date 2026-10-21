"""Small offline check for the CSV-to-report converter."""

import csv
import json
import tempfile
import unittest
from pathlib import Path

from performance.report import build_report


class ReportTest(unittest.TestCase):
    def test_reads_aggregate_and_writes_summary(self):
        with tempfile.TemporaryDirectory() as directory:
            run_dir = Path(directory)
            metadata = {
                "started_at_utc": "2026-09-24T00:00:00+00:00",
                "git_commit": "example-sha", "environment": "test fixture",
                "cpu_count": 4, "memory_gb": 30, "java_version": "21",
                "mysql_version": "8.4", "locust_version": "2.32.1",
                "phases": {"smoke": {"users": 5, "duration_seconds": 60, "exit_code": 0}},
            }
            (run_dir / "metadata.json").write_text(json.dumps(metadata), encoding="utf-8")
            with (run_dir / "smoke_stats.csv").open("w", encoding="utf-8", newline="") as stream:
                writer = csv.DictWriter(stream, fieldnames=[
                    "Name", "Request Count", "Failure Count", "Requests/s",
                    "50%", "95%", "99%",
                ])
                writer.writeheader()
                writer.writerow({"Name": "Aggregated", "Request Count": 100,
                                 "Failure Count": 1, "Requests/s": 10,
                                 "50%": 20, "95%": 50, "99%": 90})
            result = build_report(run_dir)
            self.assertEqual(result[0]["failure_percent"], 1)
            self.assertEqual(result[0]["p95_ms"], 50)
            self.assertTrue((run_dir / "summary.csv").is_file())
            self.assertIn("example-sha", (run_dir / "REPORT.md").read_text(encoding="utf-8"))
            report = (run_dir / "REPORT.md").read_text(encoding="utf-8")
            self.assertIn("Chưa có api.log", report)
            self.assertNotIn("Nhận xét cần hoàn thiện", report)
            (run_dir / "api.log").write_text("INFO started\nERROR database failed\nTestException: fixture\n", encoding="utf-8")
            build_report(run_dir)
            self.assertIn("2 dòng có mẫu ERROR/Exception", (run_dir / "REPORT.md").read_text(encoding="utf-8"))

    def test_declared_phase_without_csv_is_not_silently_omitted(self):
        with tempfile.TemporaryDirectory() as directory:
            run_dir = Path(directory)
            (run_dir / "metadata.json").write_text(json.dumps({
                "phases": {"smoke": {"users": 5, "duration_seconds": 60}}}), encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "without its Locust CSV"):
                build_report(run_dir)


if __name__ == "__main__":
    unittest.main()
