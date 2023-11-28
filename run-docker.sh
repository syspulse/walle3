#!/bin/bash                                                                                                                                                                                            
#CWD=`echo $(dirname $(readlink -f $0))`
#cd $CWD

t=`pwd`;
APP=`basename "$t"`
CONF=`echo $APP | awk -F"-" '{print $2}'`

export SITE=${SITE:-$CONF}

DOCKER="syspulse/${APP}:latest"

DATA_DIR=${DATA_DIR:-/mnt/data}

if [ "$S3_BUCKET" != "" ]; then
   PRIVILEGED="--privileged"
fi

echo "APP: $APP"
echo "SITE: $SITE"
echo "DOCKER: $DOCKER"
echo "DATA_DIR: $DATA_DIR"
echo "ARGS: $@"
echo "OPT: $OPT"
echo "DATASTORE: $DATASTORE"

# ATTENTION: /app/bin/skel-app overrides arguments !
#
# process_args () {
#   local no_more_snp_opts=0
#   while [ $# -gt 0 ]; do
#     case "$1" in
#              --) shift && no_more_snp_opts=1 && break ;;
#        -h|-help) usage; exit 1 ;;
#     -v|-verbose) verbose=1 && shift ;;
#       -d|-debug) debug=1 && shift ;;
#     -no-version-check) no_version_check=1 && shift ;;

docker run --rm --name $APP -p 8080:8080 -v `pwd`/conf:/app/conf -v $DATA_DIR:/data \
   -e JAVA_OPTS=$OPT \
   -e DB_HOST=$DB_HOST \
   -e SMTP_HOST=$SMTP_HOST \
   -e SMTP_USER=$SMTP_USER \
   -e SMTP_PASS=$SMTP_PASS \
   -e SMTP_FROM=$SMTP_FROM \
   -e AWS_ACCESS_KEY_ID=$AWS_ACCESS_KEY_ID \
   -e AWS_SECRET_ACCESS_KEY=$AWS_SECRET_ACCESS_KEY \
   -e AWS_REGION=$AWS_REGION \
   -e S3_BUCKET=$S3_BUCKET \
   $PRIVILEGED \
   $DOCKER $@
