#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
kotlinc app/src/main/java/ru/dikoveriya/lab/LabModels.kt app/src/main/java/ru/dikoveriya/lab/RecipeCsv.kt qa/RecipeMathTest.kt -include-runtime -d /tmp/dikoveriya-math-test.jar
java -jar /tmp/dikoveriya-math-test.jar
kotlinc app/src/main/java/ru/dikoveriya/lab/LabModels.kt app/src/main/java/ru/dikoveriya/lab/RecipeCsv.kt qa/RecipeCsvTest.kt -include-runtime -d /tmp/dikoveriya-csv-test.jar
java -jar /tmp/dikoveriya-csv-test.jar
python3 qa/check_sql.py
kotlinc app/src/main/java/ru/dikoveriya/lab/LabOperationRules.kt qa/LabOperationRulesTest.kt -include-runtime -d /tmp/diko-lab-rules.jar
java -jar /tmp/diko-lab-rules.jar
