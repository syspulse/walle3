# walle3

Wallet Signer

1. Private Keys never leave Signer

2. Wallet is associated with `oid` (owner ID UUID) and assigned during generation

3. Only JWT with correct `oid` can perform operations on a wallet

4. Admin/Service JWT can perform any operations

## Data Store

| parameter | description |
|-------------|--------------|
| mem://    |  Used only for testing. Stores wallets in the memory              |
| dir://[dir]    | Stores wallets in encrypted files (in the directory) |
| jdbc:// | JDBC (Posgtres DB by default) |
| kms://[uri] | AWS KMS ECDSA_SHA_256 (custom Endpoiint  is supported)|
|     | 

__ATTENTION__: If `kms://` datastore is selected, only `kms://` Signer is supported and Cypher is ignored

Example:

```
./run-wal3.sh --datastore=jdbc://postgres
```

`postgres` config is defined in `conf/application.conf`

```
./run-wal3.sh --datastore=dir://./store
```

AWS KMS:
```
./run-wal3.sh --datastore=kms://
```

Custom KMS:
```
./run-wal3.sh --datastore=kms://http://localhost:4599
```


## Cypher (Wallet encryption)

__NOTE__: Only PrivateKey is encrypted

| parameter | description |
|-------------|--------------|
| file://[file]    |  file with an passphrase (encryption key) (e.g. `file://key.txt`) |
| key://<key>    | Encryption key (e.g. `key://passphrase123`) |
| kms://<arn> | AWS KMS AES-256 keyId |
|     | 

__ATTENTION__: AWS Credentials must contain `AWS_REGION` together with (AWS_ACCESS_KEY_ID,AWS_SECRET_ACCESS_KEY,AWS_SESSION_TOKEN)

Example:

```
./run-wal3.sh --cypher=kms://arn:aws:kms:eu-west-1:$AWS_ACCOUNT:key/e7d5e92b-5553-454c-a73e-2ab104c5e087
```

## Signer

| parameter | description |
|-------------|--------------|
| eth1://    |  secp256k1 Signer             |
| eth2://    | BLS Signer (not supported for transaction) |
| kms://[uri]| AWS KMS SECP256K1 |
|     | 

__NOTE__: If signer is `kms://`, Datastore must be kms:// as well


## Blockchains

Wallet operations (`balance`, `signature`, `transaction`) require connection to the RPC node.

The format: `chain_id`=`name`=`uri`

Example:

```
./run-wal3.sh --datastore=postgres:// --blockchains=1=mainnet=http://infura,534352=scroll=https://rpc.scroll.io
```

---

## Tests

For KMS tests, run local env

KMS: https://github.com/nsmithuk/local-kms?tab=readme-ov-file

Tutorial: https://nsmith.net/aws-kms-cli


```
docker run -p 4599:8080 --mount type=bind,source="$(pwd)"/data_kms,target=/data nsmithuk/local-kms

export AWS_ACCESS_KEY_ID="111"
export AWS_SECRET_ACCESS_KEY="222"
export AWS_REGION=eu-west-1
export AWS_ACCOUNT=0000

export AWS_ENDPOINT=http://localhost:4599
```
