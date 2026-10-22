#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p build/issue-report-test
"${JAVA_HOME:+$JAVA_HOME/bin/}javac" -d build/issue-report-test \
  src/client/java/com/slabbed/client/IssueReport.java \
  src/client/java/com/slabbed/client/IssueReportSession.java \
  src/client/java/com/slabbed/client/IssueReportPlacement.java \
  tools/testsrc/com/slabbed/client/IssueReportTest.java
"${JAVA_HOME:+$JAVA_HOME/bin/}java" -cp build/issue-report-test com.slabbed.client.IssueReportTest
