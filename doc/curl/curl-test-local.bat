echo IMPORTANT: the following curl tests require valid credentials. Run 'ant all' to generate a LAS2peer test user agent 'UserA'. The agent's login can be extracted from the file ./etc/startup/agent-user-UserA.xml. It is the numeric value contained in the id-element. The agent's password is set to a default value "userAPass". Whenever you rerun 'ant all', new public/private keys and a new id are generated for the test user agent. Do not forget to adapt credentials in curl calls!

set endpoint=http://localhost:8080/mobsos
set cred1=5830973071816504729:userAPass
set cred2=5593111138118188637:userBPass

echo "create new questionnaire 1"
curl -v -X POST %endpoint%/questionnaires --user %cred1% -H "Content-Type: application/json" -d "{\"name\":\"Learning Layers Standard Application Success Questionnaire\",\"description\":\"A questionnaire to assess the success of an application developed by the Learning Layers project in different dimensions of success.\",\"organization\":\"Learning Layers\",\"logo\":\"http://learning-layers.eu/wp-content/themes/learninglayers/images/logo.png\"}"
pause

echo "upload questionnaire form"
curl -v -X POST %endpoint%/questionnaires/1/form --user %cred1% --data-binary @doc/xml/qu2.xml -H 'Content-type:text/xml'
pause

echo "create new survey 1"
curl -v -X POST %endpoint%/surveys --user %cred1% -H "Content-Type: application/json" -d "{\"name\":\"SeViAnno Success Survey\",\"description\":\"A survey to assess the different factors of success in applications developed by the Learning Layers project (http://learning-layers.eu).\",\"resource\":\"https://github.com/learning-layers/sevianno\",\"organization\":\"Learning Layers\",\"logo\":\"http://learning-layers.eu/wp-content/themes/learninglayers/images/logo.png\",\"start\":\"2014-05-20T00:00:00Z\",\"end\":\"2014-06-20T23:59:59Z\"}"
pause

echo "set questionnaire for survey 1"
curl -v -X POST %endpoint%/surveys/1/questionnaire --user %cred1% -H "Content-Type: application/json" -d "{\"qid\":1}"
pause

echo "get questionnaire form with second user without community context"
curl -v -X GET %endpoint%/surveys/1/questionnaire --user %cred1% -H "Content-Type: text/html" > form.html

echo "list all surveys"
curl -v -X GET %endpoint%/surveys --user %cred1%
pause



REM pause

REM curl -v -X "DELETE" http://localhost:8080/tests/s100r1JSO --user -7862164820900576677:dspgtud
REM pause

REM ACDSense - create test for demonstrating CSV operations (test id: s100r1JSO)
REM curl -v -X POST http://localhost:8080/tests?format=csv --user -7862164820900576677:dspgtud --data-binary @meta.csv -H 'Content-type:text/csv'
REM pause

REM ACDSense - upload one-element send data package as JSON (only works once with specified id (primary key!); change id for subsequent requests)
REM curl -v -X POST http://localhost:8080/tests/s10r10WFP/data/send?format=json --user -7862164820900576677:dspgtud -H "Content-Type: application/json" -d "[{\"id\":\"1005\",\"time\":\"1239082345554\",\"fromjid\":\"temperature@role.dbis.rwth-aachen.de\",\"pldsize\":103,\"msgsize\":268,\"msg\":\"msg\",\"tojid\":\"office6237@ambience.role.dbis.rwth-aachen.de\/temperature\"}]"
REM pause