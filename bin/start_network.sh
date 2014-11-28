#! /bin/bash

SCRIPTDIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd ${SCRIPTDIR}/../
BASE=${PWD}
export CLASSPATH="${PWD}/lib/*"

java -cp "${CLASSPATH}" i5.las2peer.tools.L2pNodeLauncher -w -p 9001 - "uploadStartupDirectory('etc/startup')" "startService('i5.las2peer.services.mobsos.SurveyService','mobsosrules')" "startWebConnector" 