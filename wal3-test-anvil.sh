#!/bin/bash
# start anvil

VALUE=${1:-7}

export ADDR0=0x70997970C51812dc3A010C7d01b50e0d17dc79C8
export SK=0x59c6995e998f97a5a0044966f0945389dc9e86dae88c7a8412f4603b6b78690d

ADDR1=`./wal3-random.sh | jq -r .addr`
VALUE_ETH=`cast to-wei $VALUE`

echo "========================================================================================================="
echo "Transfer $ADDR0 -> $ADDR1"

cast send $ADDR1 --private-key=$SK --value=$VALUE_ETH --gas-price=30gwei --priority-gas-price=2.0gwei --gas-limit=21000
echo "========================================================================================================="

# check ADDR0
echo $ADDR0
cast balance $ADDR0
# check ADDR1
echo $ADDR1
cast balance $ADDR1
echo "========================================================================================================="

echo -e "Transfer $ADDR1 -> $ADDR0\n"
# Transfer back
./wal3-tx.sh $ADDR1 $ADDR0 "1 eth"

echo "========================================================================================================="
# check ADDR0
echo $ADDR0
cast balance $ADDR0
# check ADDR1
echo $ADDR1
cast balance $ADDR1
