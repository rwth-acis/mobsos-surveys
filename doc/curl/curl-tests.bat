:: MobSOS Surveys - curl Commandlines
:: ==================================
::
:: This file contains sample curl commands for all resources and methods supported by the MobSOS Surveys RESTful API.
:: As such it serves as a kind of documentation for developers working with the API.
:: By default, all commandlines are commented out with a "rem".
:: 
:: IMPORTANT: some of the following curl commandlines require valid credentials. Run 'ant all' in the root folder of this project to generate a las2peer test user agent 'UserA'. The agent's login can be extracted from the file ./etc/startup/agent-user-UserA.xml. It is the numeric value contained in the id-element (including the minus sign!). The agent's password is set to a default value "userAPass". Whenever you rerun 'ant all', new public/private keys and a new id are generated for the test user agent. Do not forget to adapt credentials in curl commandlines!

:: API endpoint name and URL to be used in subsequent curl commandlines

:: Setting for localhost (default)

set ep_name=Localhost
set ep=http://localhost:8080/mobsos-surveys

:: Setting for deployment on steen

::set ep_name=Steen
::set ep=http://steen.informatik.rwth-aachen.de:9084/mobsos-surveys

:: Setting for Layers Adapter

:: set ep_name=Layers Adapter
:: set ep=http://api.learning-layers.eu:8280/mobsos-surveys

:: las2peer user credentials to be used in subsequent curl commandlines
set cred=-9147513385456075294:userAPass

:: ***************** QUESTIONNAIRE RESOURCES *********************

cls
rem curl -v -X GET %ep%/questionnaires -H "Accept: text/html" -H "Accept-Language: en-US"
echo "**** %ep_name% - GET QUESTIONNAIRES (HTML/NO AUTH)"
pause

cls
rem curl -v -X GET %ep%/questionnaires -H "Accept: text/html" -H "Accept-Language: en-US" --user %cred%
echo "**** %ep_name% - GET QUESTIONNAIRES (HTML/AUTH)"
pause

cls
rem curl -v -X GET %ep%/questionnaires -H "Accept: application/json"
echo "**** %ep_name% - GET QUESTIONNAIRES (JSON/NO AUTH)"
pause

cls
rem curl -v -X GET %ep%/questionnaires -H "Accept: application/json" --user %cred%
echo "**** %ep_name% - GET QUESTIONNAIRES (JSON/AUTH)"
pause

cls
rem curl -v -X POST %ep%/questionnaires --user %cred% -H "Content-Type: application/json" -H "Accept: application/json" -d "{\"name\":\"Sample Questionnaire %RANDOM%\",\"description\":\"A questionnaire.\",\"organization\":\"Nillenposse\",\"logo\":\"http://nillenposse.de/pics/slogo.jpg\",\"lang\":\"de-DE\"}"
echo "**** %ep_name% - POST NEW QUESTIONNAIRE (JSON/AUTH)"
pause

cls
rem curl -v -X DELETE %ep%/questionnaires --user %cred%
echo "**** %ep_name% - DELETE QUESTIONNAIRES (AUTH)"
pause

cls
rem curl -v -X GET %ep%/questionnaires/1 -H "Accept: text/html" -H "Accept-Language: en-US"
echo "**** %ep_name% - GET QUESTIONNAIRE (HTML/NO AUTH)"
pause

cls
rem curl -v -X GET %ep%/questionnaires/1 -H "Accept: text/html" -H "Accept-Language: en-US" --user %cred%
echo "**** %ep_name% - GET QUESTIONNAIRE (HTML/AUTH)"
pause

cls
rem curl -v -X GET %ep%/questionnaires/1 -H "Accept: application/json"
echo "**** %ep_name% - GET QUESTIONNAIRE (JSON/NO AUTH)"
pause

cls
rem curl -v -X GET %ep%/questionnaires/1 -H "Accept: application/json" --user %cred%
echo "**** %ep_name% - GET QUESTIONNAIRE (JSON/AUTH)"
pause

cls
rem curl -v -X PUT %ep%/questionnaires/18 --user %cred% -H "Content-Type: application/json" -d "{\"name\":\"Sample Questionnaire %RANDOM%\",\"description\":\"A questionnaire.\",\"organization\":\"Nillenposse\",\"logo\":\"http://nillenposse.de/pics/slogo.jpg\",\"lang\":\"en-US\"}"
echo "**** %ep_name% - UPDATE QUESTIONNAIRE (JSON/AUTH)"
pause

cls
rem curl -v -X DELETE %ep%/questionnaires/19 --user %cred%
echo "**** %ep_name% - DELETE QUESTIONNAIRE (AUTH)"
pause

cls
rem curl -v -X GET %ep%/questionnaires/20/form -H "Accept: text/xml"
echo "**** %ep_name% - GET QUESTIONNAIRE FORM (JSON/NO AUTH)"
pause

cls
rem curl -v -X GET %ep%/questionnaires/20/form -H "Accept: text/xml" --user %cred%
echo "**** %ep_name% - GET QUESTIONNAIRE FORM (JSON/AUTH)"
pause

cls
rem curl -v -X PUT %ep%/questionnaires/17/form --user %cred% --data-binary @questionnaire.xml -H "Content-type: text/xml"
echo "**** %ep_name% - PUT QUESTIONNAIRE FORM (XML/AUTH)"
pause

:: ***************** SURVEY RESOURCES *********************

cls
rem -v -X GET %ep%/surveys -H "Accept: text/html" -H "Accept-Language: en-US"
echo "**** %ep_name% - GET SURVEYS (HTML/NO AUTH)"
pause

cls
rem curl -v -X GET %ep%/surveys -H "Accept: text/html" -H "Accept-Language: en-US" --user %cred%
echo "**** %ep_name% - GET SURVEYS (HTML/AUTH)"
pause

cls
rem curl -v -X GET %ep%/surveys -H "Accept: application/json"
echo "**** %ep_name% - GET SURVEYS (JSON/NO AUTH)"
pause

cls
rem curl -v -X GET %ep%/surveys -H "Accept: application/json" --user %cred%
echo "**** %ep_name% - GET SURVEYS (JSON/AUTH)"
pause

cls
rem curl -v -X POST %ep%/surveys --user %cred% -H "Content-Type: application/json" -d "{\"name\":\"Survey %RANDOM%\",\"description\":\"A survey.\",\"resource\":\"http://nillenposse.de\",\"organization\":\"Nillenposse\",\"logo\":\"http://nillenposse.de/pics/slogo.jpg\",\"start\":\"2014-05-20T00:00:00Z\",\"end\":\"2014-06-20T23:59:59Z\",\"lang\":\"de-DE\"}"
echo "**** %ep_name% - POST NEW SURVEY (JSON/AUTH)"
pause

cls
rem curl -v -X DELETE %ep%/surveys --user %cred%
echo "**** %ep_name% - DELETE SURVEYS (AUTH)"
pause

cls
rem curl -v -X GET %ep%/surveys/7 -H "Accept: text/html" -H "Accept-Language: en-US"
echo "**** %ep_name% - GET SURVEY (HTML/NO AUTH)"
pause

cls
rem curl -v -X GET %ep%/surveys/7 -H "Accept: text/html" -H "Accept-Language: en-US" --user %cred%
echo "**** %ep_name% - GET SURVEY (HTML/AUTH)"
pause

cls
rem curl -v -X GET %ep%/surveys/7 -H "Accept: application/json"
echo "**** %ep_name% - GET SURVEY (JSON/NO AUTH)"
pause

cls
rem curl -v -X GET %ep%/surveys/7 -H "Accept: application/json" --user %cred%
echo "**** %ep_name% - GET SURVEYS (JSON/AUTH)"
pause

cls
rem curl -v -X PUT %ep%/surveys/5 --user %cred% -H "Content-Type: application/json" -d "{\"name\":\"Another Survey %RANDOM%\",\"description\":\"A changed survey.\",\"resource\":\"http://nillenposse.de\",\"organization\":\"Nillenposse\",\"logo\":\"http://nillenposse.de/pics/slogo.jpg\",\"start\":\"2014-05-20T00:00:00Z\",\"end\":\"2014-06-20T23:59:59Z\",\"lang\":\"de-DE\"}"
echo "**** %ep_name% - POST NEW SURVEY (JSON/AUTH)"
pause

cls
rem curl -v -X DELETE %ep%/surveys/5 --user %cred%
echo "**** %ep_name% - DELETE SURVEY (AUTH)"
pause

cls
rem curl -v -X GET %ep%/surveys/7/questionnaire -H "Accept: text/html" -H "Accept-Language: en-US"
echo "**** %ep_name% - GET SURVEY QUESTIONNAIRE FORM (HTML/NO AUTH)"
pause

cls
rem curl -v -X GET %ep%/surveys/7/questionnaire -H "Accept: text/html" -H "Accept-Language: en-US" --user %cred%
echo "**** %ep_name% - GET SURVEY QUESTIONNAIRE FORM (HTML/AUTH)"
pause

cls
rem curl -v -X POST %ep%/surveys/7/questionnaire --user %cred% -H "Content-Type: application/json" -d "{\"qid\":20}"
echo "**** %ep_name% - SET SURVEY QUESTIONNAIRE (JSON/AUTH)"
pause

cls
rem curl -v -X GET %ep%/surveys/7/responses -H "Accept: text/html" -H "Accept-Language: en-US"
echo "**** %ep_name% - GET SURVEY RESPONSES (HTML/NO AUTH)"
pause

cls
rem curl -v -X GET %ep%/surveys/7/responses -H "Accept: text/html" -H "Accept-Language: en-US" --user %cred%
echo "**** %ep_name% - GET SURVEY RESPONSES (HTML/AUTH)"
pause

cls
rem curl -v -X GET %ep%/surveys/7/responses -H "Accept: text/csv"
echo "**** %ep_name% - GET SURVEY RESPONSES (CSV/NO AUTH)"
pause

cls
rem curl -v -X GET %ep%/surveys/7/responses -H "Accept: text/csv" --user %cred%
echo "**** %ep_name% - GET SURVEY RESPONSES (CSV/AUTH)"
pause

cls
rem curl -v -X POST %ep%/surveys/7/responses -H "Content-Type: application/json" -d "{\"COOL.1\":\"1\",\"COOL.C\":\"So eine Scheiss-Seite!\"}"
echo "**** %ep_name% - POST SURVEY RESPONSE (JSON/NO AUTH)"
pause

cls
rem curl -v -X POST %ep%/surveys/7/responses --user %cred% -H "Content-Type: application/json" -d "{\"COOL.1\":\"4\",\"COOL.C\":\"Hab schöne Sachen drin gesehen.\"}"
echo "**** %ep_name% - POST SURVEY RESPONSE (JSON/AUTH)"
pause

cls
rem curl -v -X POST %ep%/surveys/7/responses --data-binary @response.xml -H "Content-type: text/xml"
echo "**** %ep_name% - POST SURVEY RESPONSE (XML/NO AUTH)"
pause

cls
rem curl -v -X POST %ep%/surveys/7/responses --data-binary @response.xml -H "Content-type: text/xml" --user %cred%
echo "**** %ep_name% - POST SURVEY RESPONSE (XML/AUTH)"
pause

cls
rem curl -v -X DELETE %ep%/surveys/7/responses
echo "**** %ep_name% - DELETE ALL SURVEY RESPONSES (NO AUTH)"
pause
