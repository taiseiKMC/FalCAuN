#!/bin/bash -eu

cd $(dirname $0)

FOLDER="log"
NAME=$(basename $1)
DATE=$(date '+%y%m%d%H%M')
FILENAME="${NAME%.*}-${DATE}.log"

echo "Log File:  ${FOLDER}/${FILENAME}" 
nohup kscript $1 > "${FOLDER}/${FILENAME}" 2>&1 &
echo "Process: $!"


