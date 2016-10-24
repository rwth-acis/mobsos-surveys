#! /bin/bash

SCRIPTDIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd ${SCRIPTDIR}/../
BASE=${PWD}
export CLASSPATH="${PWD}/lib/*"

java -cp "${CLASSPATH}" i5.las2peer.tools.L2pNodeLauncher -p 9001 "startService('i5.las2peer.services.mobsos.surveys.SurveyService@0.2','mobsosrules')" "startWebConnector" "interactive" 