#!/bin/bash

OID=${1:-00000000-0000-0000-1000-000000000001}

ACCESS_TOKEN=${ACCESS_TOKEN-`cat ACCESS_TOKEN`}
SERVICE_URI=${SERVICE_URI:-http://localhost:8080/api/v1/wal3}

>&2 echo "OID=$OID"

#DATA_JSON="{\"email\":\"$EMAIL\",\"name\":\"$NAME\",\"name\":\"$NAME\",\"xid\":\"$XID\"}"
if [ "$OID" == "random" ]; then
   OID=`uuidgen`
   DATA_JSON="{\"oid\":\"$OID\"}"
else
   DATA_JSON="{\"oid\":\"$OID\"}"
fi

>&2 echo $DATA_JSON
curl -S -s -D /dev/stderr -X POST --data "$DATA_JSON" -H 'Content-Type: application/json' -H "Authorization: Bearer $ACCESS_TOKEN" $SERVICE_URI/random
