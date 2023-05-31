#!/bin/bash
#
# This script automates the building and testing of the applications.
#
# Copyright (c) 2023 Open Technologies for Integration
# Licensed under the MIT license (see LICENSE for details)
#

# Exit on any failure
set -e

# Create the work directory
rm -rf /tmp/ace-scatter-gather-test-examples-work-dir junit-reports
mqsicreateworkdir /tmp/ace-scatter-gather-test-examples-work-dir

echo "Compiling the applications and unit tests"
# Build the applications and unit tests, but not the component tests or dummy back-end flows.
ibmint deploy --input-path . --output-work-directory /tmp/ace-scatter-gather-test-examples-work-dir \
  --project AggregationApplication \
  --project AggregationApplication_UnitTest \
  --project CollectorApplication \
  --project CollectorApplication_UnitTest

# ibmint optimize server new for v12.0.4 - speed up test runs
ibmint optimize server --work-directory /tmp/ace-scatter-gather-test-examples-work-dir --enable JVM --disable NodeJS

echo "Running the Aggregation unit tests"
IntegrationServer -w /tmp/ace-scatter-gather-test-examples-work-dir --test-project AggregationApplication_UnitTest \
  --start-msgflows false --mq-queue-manager-name dummyQM --test-junit-options "--reports-dir=junit-reports"

echo "Running the Collector unit tests"
IntegrationServer -w /tmp/ace-scatter-gather-test-examples-work-dir --test-project CollectorApplication_UnitTest \
  --start-msgflows false --mq-queue-manager-name dummyQM --test-junit-options "--reports-dir=junit-reports"
