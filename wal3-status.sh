#!/bin/bash

TX_HASH=${1:-}
BLOCKCHAIN=${2:-anvil}
ADDR=${3:-0x00000000000000000000000000000000000}
OID=${OID:-00000000-0000-0000-5555-000000000001}

SERVICE_URI=${SERVICE_URI:-http://127.0.0.1:8080/api/v1/wal}
ACCESS_TOKEN=${ACCESS_TOKEN-`cat ACCESS_TOKEN`}

>&2 echo "ADDR=$ADDR"
>&2 echo "OID=$OID"

curl -S -s -D /dev/stderr -X GET -H 'Content-Type: application/json' -H "Authorization: Bearer $ACCESS_TOKEN" $SERVICE_URI/owner/${OID}/${ADDR}/tx/${TX_HASH}/${BLOCKCHAIN}