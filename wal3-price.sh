#!/bin/bash

CHAIN_ID=${1:-ethereum}

OID=${OID:-00000000-0000-0000-5555-000000000001}

ACCESS_TOKEN=${ACCESS_TOKEN-`cat ACCESS_TOKEN`}
SERVICE_URI=${SERVICE_URI:-http://localhost:8080/api/v1/wal}

#CHAIN_ID=${CHAIN_ID:}

>&2 echo "CHAIN_ID=$CHAIN_ID"

#DATA_JSON="{\"to\":\"$TO\",\"data\":\"$DATA\",\"chain\":{\"name\":\"evm\",\"id\":\"$CHAIN_ID\"}}"

>&2 echo $DATA_JSON
curl -S -s -D /dev/stderr -X GET --data "$DATA_JSON" -H 'Content-Type: application/json' -H "Authorization: Bearer $ACCESS_TOKEN" $SERVICE_URI/blockchain/${CHAIN_ID}
