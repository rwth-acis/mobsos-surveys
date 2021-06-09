#! /bin/bash

SCRIPTDIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd ${SCRIPTDIR}/../
BASE=${PWD}
export CLASSPATH="${PWD}/lib/*"

java -cp "${CLASSPATH}" i5.las2peer.tools.L2pNodeLauncher -p 9016 --bootstrap 192.168.1.12:9011 --service-directory service uploadStartupDirectory "startService('i5.las2peer.services.mobsos.surveys.SurveyService@0.3.1','mobsosrules')" "interactive" 
