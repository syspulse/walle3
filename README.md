# wal3-signer

Wallet Signer

0. Private Keys never leave Signer

1. Wallet is associated with `oid` (owner ID UUID) and assigned during generation

2. Only JWT with correct `oid` can perform operations on a wallet

3. Admin JWT can perform any operations

## Data Store

### mem://

Used only for testing. Stores wallets in the memmory unencrypted

### dir://

Stores wallets in a file in the directory

### jdbc://

## Wallet Encryption

Encryption for wallet data (private keys, oid)

### file://

File with the key

### key://

Explicit key

### kms://

KMS keyId

ATTENTION: AWS Credentials must contain `AWS_REGION` together with (AWS_ACCESS_KEY_ID,AWS_SECRET_ACCESS_KEY,AWS_SESSION_TOKEN)


Stores wallet in the DB

## Signer

### eth1://

Internal sec256k1 signer







