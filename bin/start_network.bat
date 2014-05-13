cd %~dp0
cd ..
set BASE=%CD%
set CLASSPATH="%BASE%/lib/*;"

java -cp %CLASSPATH% i5.las2peer.tools.L2pNodeLauncher -s 9001 - uploadStartupDirectory startService('i5.las2peer.services.mobsos.SurveyService','mobsosrules') startConnector('i5.las2peer.webConnector.WebConnector') interactive
pause
