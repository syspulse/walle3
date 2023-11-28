#!/bin/bash

ADDR=${1:-}
TO=${2:-0xfffffffffffffffffffffffffffffffffffff}
VALUE=${3:-0}
FEE=${4:-0}

ACCESS_TOKEN=${ACCESS_TOKEN-`cat ACCESS_TOKEN`}
SERVICE_URI=${SERVICE_URI:-http://localhost:8080/api/v1/wal3}

>&2 echo "ADDR=$ADDR"
>&2 echo "OID=$OID"
>&2 echo "TO=$TO"

DATA_JSON="{\"to\":\"$TO\",\"data\":\"$DATA\",\"value\":$VALUE,\"fee\":$FEE}"

>&2 echo $DATA_JSON
curl -S -s -D /dev/stderr -X POST --data "$DATA_JSON" -H 'Content-Type: application/json' -H "Authorization: Bearer $ACCESS_TOKEN" $SERVICE_URI/${ADDR}/sign
