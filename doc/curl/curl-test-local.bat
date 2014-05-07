REM ACDSense - get disco set for given test as CSV (written to file testdataset.csv)
curl -v -X GET http://localhost:8080/mobsos/surveys/3 --user -5021131362343084485:adamspass
pause

REM ACDSense - delete full dataset by deleting all tests
REM curl -v -X "DELETE" http://localhost:8080/mobsos/surveys --user -7862164820900576677:dspgtud
REM pause

REM curl -v -X "DELETE" http://localhost:8080/tests/s100r1JSO --user -7862164820900576677:dspgtud
REM pause

REM ACDSense - create test for demonstrating CSV operations (test id: s100r1JSO)
REM curl -v -X POST http://localhost:8080/tests?format=csv --user -7862164820900576677:dspgtud --data-binary @meta.csv -H 'Content-type:text/csv'
REM pause

REM ACDSense - upload one-element send data package as JSON (only works once with specified id (primary key!); change id for subsequent requests)
REM curl -v -X POST http://localhost:8080/tests/s10r10WFP/data/send?format=json --user -7862164820900576677:dspgtud -H "Content-Type: application/json" -d "[{\"id\":\"1005\",\"time\":\"1239082345554\",\"fromjid\":\"temperature@role.dbis.rwth-aachen.de\",\"pldsize\":103,\"msgsize\":268,\"msg\":\"msg\",\"tojid\":\"office6237@ambience.role.dbis.rwth-aachen.de\/temperature\"}]"
REM pause