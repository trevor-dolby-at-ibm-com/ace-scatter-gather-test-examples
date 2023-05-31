#!/bin/bash
#
# This script automates the building and testing of the applications.
#
# Copyright (c) 2023 Open Technologies for Integration
# Licensed under the MIT license (see LICENSE for details)
#

# Exit on any failure
set -e

export QMNAME="ACEv12_QM"
echo "Using $QMNAME as the component test queue manager"

# This will fail if the QM does not exist or isn't started
echo "Ensuring required queues exist"
cat sg-queues.mqsc | /opt/mqm/bin/runmqsc $QMNAME

# Create the work directory
rm -rf /tmp/ace-scatter-gather-test-examples-work-dir junit-reports
mqsicreateworkdir /tmp/ace-scatter-gather-test-examples-work-dir

echo "Compiling the applications and unit tests"
# Build the applications and component tests, including the dummy back-end flows.
ibmint deploy --input-path . --output-work-directory /tmp/ace-scatter-gather-test-examples-work-dir \
  --project BackendApplication \
  --project AggregationApplication \
  --project AggregationApplication_ComponentTest \
  --project CollectorApplication \
  --project CollectorApplication_ComponentTest

# ibmint optimize server new for v12.0.4 - speed up test runs
# The Collector flow relies on using port 7600 as a pseudo-backend for REST requests
ibmint optimize server --work-directory /tmp/ace-scatter-gather-test-examples-work-dir --enable JVM --enable NodeJS

echo "Running the Aggregation component tests"
IntegrationServer -w /tmp/ace-scatter-gather-test-examples-work-dir --test-project AggregationApplication_ComponentTest \
  --start-msgflows true --mq-queue-manager-name $QMNAME --test-junit-options "--reports-dir=junit-reports"

echo "Running the Collector component tests"
IntegrationServer -w /tmp/ace-scatter-gather-test-examples-work-dir --test-project CollectorApplication_ComponentTest \
  --start-msgflows true --mq-queue-manager-name $QMNAME --test-junit-options "--reports-dir=junit-reports"
