cd %~dp0
cd ..
set BASE=%CD%
set CLASSPATH="%BASE%/lib/*;"

java -cp %CLASSPATH% i5.las2peer.tools.L2pNodeLauncher -w -p 9011 - uploadStartupDirectory('etc/startup') startService('i5.las2peer.services.mobsos.SurveyService','mobsosrules') startWebConnector interactive
pause
