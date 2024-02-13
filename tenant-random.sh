#!/bin/bash

ACCESS_TOKEN=${ACCESS_TOKEN-`cat ACCESS_TOKEN`}
SERVICE_URI=${SERVICE_URI:-http://localhost:8080/api/v1/wal}

>&2 echo "ADDR=$ADDR"

DATA_JSON="{\"oid\":\"\"}"

>&2 echo $DATA_JSON
curl -S -s -D /dev/stderr -X POST --data "$DATA_JSON" -H 'Content-Type: application/json' -H "Authorization: Bearer $ACCESS_TOKEN" $SERVICE_URI/tenant/random
