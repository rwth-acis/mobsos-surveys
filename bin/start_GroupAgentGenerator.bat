cd %~dp0
cd ..
set BASE=%CD%
set CLASSPATH="%BASE%/lib/*"

java -cp %CLASSPATH% i5.las2peer.tools.GroupAgentGenerator "./etc/startup/agent-user-UserA.xml" "./etc/startup/agent-user-UserB.xml"
pause
