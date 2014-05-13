echo IMPORTANT: the following curl tests require valid credentials. Run 'ant all' to generate a LAS2peer test user agent 'UserA'. The agent's login can be extracted from the file ./etc/startup/agent-user-UserA.xml. It is the numeric value contained in the id-element. The agent's password is set to a default value "userAPass". Whenever you rerun 'ant all', new public/private keys and a new id are generated for the test user agent. Do not forget to adapt credentials in curl calls!

set endpoint=http://localhost:8080/mobsos
set credentials=4442538330439435920:userAPass

echo "list all surveys"
curl -X GET %endpoint%/surveys --user %credentials%
pause

echo "delete all surveys"
curl -v -X "DELETE" %endpoint%/surveys --user %credentials%
pause

echo "create new survey"
curl -v -X POST %endpoint%/surveys --user %credentials% -H "Content-Type: application/json" -d "{\"name\":\"Antonio Banderas Success Survey\",\"description\":\"A survey to find out which facets of Antonio Banderas make him so successful.\",\"resource\":\"http://www.imdb.com/name/nm0000104/\",\"organization\":\"ACIS Group\",\"logo\":\"http://dbis.rwth-aachen.de/cms/images/logo.jpg\",\"start\":\"2014-05-20T00:00:00Z\",\"end\":\"2014-06-20T23:59:59Z\"}"
pause

echo "list all surveys"
curl -v -X GET %endpoint%/surveys --user %credentials%
pause

echo "retrieve survey information"

REM pause

REM curl -v -X "DELETE" http://localhost:8080/tests/s100r1JSO --user -7862164820900576677:dspgtud
REM pause

REM ACDSense - create test for demonstrating CSV operations (test id: s100r1JSO)
REM curl -v -X POST http://localhost:8080/tests?format=csv --user -7862164820900576677:dspgtud --data-binary @meta.csv -H 'Content-type:text/csv'
REM pause

REM ACDSense - upload one-element send data package as JSON (only works once with specified id (primary key!); change id for subsequent requests)
REM curl -v -X POST http://localhost:8080/tests/s10r10WFP/data/send?format=json --user -7862164820900576677:dspgtud -H "Content-Type: application/json" -d "[{\"id\":\"1005\",\"time\":\"1239082345554\",\"fromjid\":\"temperature@role.dbis.rwth-aachen.de\",\"pldsize\":103,\"msgsize\":268,\"msg\":\"msg\",\"tojid\":\"office6237@ambience.role.dbis.rwth-aachen.de\/temperature\"}]"
REM pause