#!/bin/bash

ADDR=${1:-}
BLOCKCHAIN=${2}

SERVICE_URI=${SERVICE_URI:-http://127.0.0.1:8080/api/v1/wal}
ACCESS_TOKEN=${ACCESS_TOKEN-`cat ACCESS_TOKEN`}

>&2 echo "ADDR=$ADDR"
>&2 echo "OID=$OID"

curl -S -s -D /dev/stderr -X GET -H 'Content-Type: application/json' -H "Authorization: Bearer $ACCESS_TOKEN" $SERVICE_URI/tenant/${ADDR}/balance/${BLOCKCHAIN}
