# wal3-signer

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
|     | 

Example:

```
./run-wal3.sh --datastore=jdbc://postgres
```

`postgres` config is defined in `conf/application.conf`

```
./run-wal3.sh --datastore=dir://./store
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
./run-wal3.sh --cypher=kms://arn:aws:kms:eu-west-1:$ACCOUNT:key/e7d5e92b-5553-454c-a73e-2ab104c5e087
```

## Signer

| parameter | description |
|-------------|--------------|
| eth1://    |  secp256k1 Signer             |
| eth2://    | BLS Signer (not supported for transaction) |
| kms://[arn] | AWS KMS SECP256K1 keyId |
|     | 



