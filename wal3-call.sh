#!/bin/bash

TO=${1:-0x70997970C51812dc3A010C7d01b50e0d17dc79C8}

VALUE=${VALUE:-1 eth}
ADDR=${ADDR:-0xf39fd6e51aad88f6f4ce6ab8827279cfffb92266}

LIMIT=${LIMIT:-21000}
DATA=${DATA:-}
OUTPUT_TYPE=${OUTPUT_TYPE}
CHAIN_ID=${CHAIN_ID:-31337}

OID=${OID:-00000000-0000-0000-5555-000000000001}

ACCESS_TOKEN=${ACCESS_TOKEN-`cat ACCESS_TOKEN`}
SERVICE_URI=${SERVICE_URI:-http://localhost:8080/api/v1/wal}

>&2 echo "ADDR=$ADDR"
>&2 echo "OID=$OID"
>&2 echo "TO=$TO"
>&2 echo "VALUE=$VALUE"
>&2 echo "GAS=$FEE/$TIP/$LIMIT"
>&2 echo "DATA=$DATA"
>&2 echo "NONCE=$NONCE"

DATA_JSON="{\"to\":\"$TO\",\"data\":\"$DATA\",\"value\":\"$VALUE\",\"output\":\"$OUTPUT_TYPE\",\"chain\":{\"name\":\"evm\",\"id\":\"$CHAIN_ID\"}}"

>&2 echo $DATA_JSON
curl -S -s -D /dev/stderr -X POST --data "$DATA_JSON" -H 'Content-Type: application/json' -H "Authorization: Bearer $ACCESS_TOKEN" $SERVICE_URI/owner/${OID}/${ADDR}/call
