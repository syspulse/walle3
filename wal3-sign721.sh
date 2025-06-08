#!/bin/bash

ADDR=${1:-0xf39fd6e51aad88f6f4ce6ab8827279cfffb92266}
NAME=${NAME:-Firewall Contract}
VER=${VER:-1.0.0}
VERIFYING_CONTRACT=${VERIFYING_CONTRACT:-0x0000000000000000000000000000000000000999}
CHAIN_ID=${CHAIN_ID:-31337}

MONITORED_CONTRACT=${MONITORED_CONTRACT:-0x1111111111111111111111111111111111111111}
PROVIDER=${PROVIDER:-0x2222222222222222222222222222222222222222}
NONCE=${NONCE:-7013}


OID=${OID:-00000000-0000-0000-5555-000000000001}

ACCESS_TOKEN=${ACCESS_TOKEN-`cat ACCESS_TOKEN`}
SERVICE_URI=${SERVICE_URI:-http://localhost:8080/api/v1/wal}

>&2 echo "ADDR=$ADDR"
>&2 echo "OID=$OID"
>&2 echo "NAME=$NAME"
>&2 echo "VER=$VER"
>&2 echo "VERIFYING_CONTRACT=$VERIFYING_CONTRACT"
>&2 echo "CHAIN_ID=$CHAIN_ID"

>&2 echo "MONITORED_CONTRACT=$MONITORED_CONTRACT"
>&2 echo "PROVIDER=$PROVIDER"
>&2 echo "NONCE=$NONCE"

#DATA_JSON="{\"to\":\"$TO\",\"data\":\"$DATA\",\"value\":\"$VALUE\",\"gasPrice\":\"$FEE\",\"gasTip\":\"$TIP\",\"gasLimit\":$LIMIT, \"nonce\":$NONCE,\"chain\":{\"name\":\"evm\",\"id\":\"$CHAIN_ID\"}}"

DATA_JSON=$(cat << EOF
{
  "name": "$NAME",
  "version": "$VER",
  "verifyingContract": "$VERIFYING_CONTRACT",
  "chain": {
    "name": "evm",
    "id": "$CHAIN_ID"
  },
  "primaryType": "grantProviderRoleToContract",

  "types": {
    "grantProviderRoleToContract": [
      {"name": "monitoredContract", "type": "address"},
      {"name": "provider", "type": "address"},
      {"name": "nonce", "type": "uint256"}
    ]
  },
  "value": {
    "monitoredContract": "$MONITORED_CONTRACT",
    "provider": "$PROVIDER",
    "nonce": $NONCE
  }
}
EOF
)

>&2 echo $DATA_JSON
curl -S -s -D /dev/stderr -X POST --data "$DATA_JSON" -H 'Content-Type: application/json' -H "Authorization: Bearer $ACCESS_TOKEN" $SERVICE_URI/owner/${OID}/${ADDR}/sign712
