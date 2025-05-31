#!/bin/bash -eu

cd $(dirname $0)

FOLDER="log"
NAME=$(basename $1)
DATE=$(date '+%y%m%d%H%M')
FILENAME="${NAME%.*}-${DATE}.log"

echo "Log File:  ${FOLDER}/${FILENAME}" 
kscript $1 2>&1 > "${FOLDER}/${FILENAME}" &
echo "Process: $!"
disown %1


