/*
Copyright (c1) 2014 Dominik Renzel, Advanced Community Information Systems (ACIS) Group, 
Chair of Computer Science 5 (Databases & Information Systems), RWTH Aachen University, Germany
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

 * Redistributions of source code must retain the above copyright notice, this
  list of conditions and the following disclaimer.

 * Redistributions in binary form must reproduce the above copyright notice,
  this list of conditions and the following disclaimer in the documentation
  and/or other materials provided with the distribution.

 * Neither the name of the {organization} nor the names of its
  contributors may be used to endorse or promote products derived from
  this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package i5.las2peer.services.mobsos;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import i5.las2peer.p2p.LocalNode;
import i5.las2peer.p2p.ServiceNameVersion;
import i5.las2peer.restMapper.data.Pair;
import i5.las2peer.security.Agent;
import i5.las2peer.security.GroupAgent;
import i5.las2peer.security.ServiceAgent;
import i5.las2peer.security.UserAgent;
import i5.las2peer.services.mobsos.SurveyService;
import i5.las2peer.testing.MockAgentFactory;
import i5.las2peer.webConnector.WebConnector;
import i5.las2peer.webConnector.client.ClientResponse;
import i5.las2peer.webConnector.client.MiniClient;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.json.simple.parser.ParseException;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * JUnit Test Class for MobSOS Survey Service
 * 
 * @author Dominik Renzel
 *
 */
public class SurveyServiceTest {

	private static final String HTTP_ADDRESS = "http://127.0.0.1";
	private static final int HTTP_PORT = WebConnector.DEFAULT_HTTP_PORT;

	private static LocalNode node;
	private static WebConnector connector;
	private static MiniClient c1, c2, c3, ac;
	private static ByteArrayOutputStream logStream;

	private static UserAgent user1, user2, user3, anon;
	private static GroupAgent group1, group2;

	private static final ServiceNameVersion testServiceClass = new ServiceNameVersion(SurveyService.class.getCanonicalName(),"0.1");


	/**
	 * Called before the tests start.
	 * 
	 * Sets up the node and initializes connector and users that can be used throughout the tests.
	 * 
	 * @throws Exception
	 */
	@BeforeClass
	public static void startServer() throws Exception {

		//start node
		node = LocalNode.newNode();

		user1 = MockAgentFactory.getAdam();
		user2 = MockAgentFactory.getAbel();
		user3 = MockAgentFactory.getEve();
		anon = MockAgentFactory.getAnonymous();
		user1.unlockPrivateKey("adamspass");
		user2.unlockPrivateKey("abelspass");
		user3.unlockPrivateKey("evespass");


		Agent[] as;

		as = new Agent[]{user1,user2};
		group1 = GroupAgent.createGroupAgent(as);

		as = new Agent[]{user2,user3};
		group2 = GroupAgent.createGroupAgent(as);

		node.storeAgent(user1);
		node.storeAgent(user2);
		node.storeAgent(user3);

		node.storeAgent(group1);
		node.storeAgent(group2);

		node.launch();

		ServiceAgent testService = ServiceAgent.createServiceAgent(testServiceClass, "a pass");
		testService.unlockPrivateKey("a pass");

		node.registerReceiver(testService);

		//start connector
		logStream = new ByteArrayOutputStream ();

		//connector = new WebConnector(true,HTTP_PORT,false,1000,"./etc/xmlc");
		connector = new WebConnector(true,HTTP_PORT,false,1000);

		connector.setLogStream(new PrintStream (logStream));


		connector.start ( node );
		Thread.sleep(1000); //wait a second for the connector to become ready
		
		connector.updateServiceList();

		//String xml=RESTMapper.mergeXMLs(new String[]{RESTMapper.getMethodsAsXML(SurveyService.class)});
		//System.out.println(xml);

		//avoid timing errors: wait for the repository manager to get all services before continuing
		
		try
		{
			System.out.println("waiting..");
			Thread.sleep(15000);
		}
		catch(InterruptedException e)
		{
			e.printStackTrace();
		}
		c1 = new MiniClient();
		c1.setAddressPort(HTTP_ADDRESS, HTTP_PORT);
		c1.setLogin(Long.toString(user1.getId()), "adamspass");

		c2 = new MiniClient();
		c2.setAddressPort(HTTP_ADDRESS, HTTP_PORT);
		c2.setLogin(Long.toString(user2.getId()), "abelspass");

		c3 = new MiniClient();
		c3.setAddressPort(HTTP_ADDRESS, HTTP_PORT);
		c3.setLogin(Long.toString(user3.getId()), "evespass");

		ac = new MiniClient();
		ac.setAddressPort(HTTP_ADDRESS, HTTP_PORT);
	}

	/**
	 * Called after the tests have finished.
	 * Shuts down the server and prints out the connector log file for reference.
	 * 
	 * @throws Exception
	 */
	@AfterClass
	public static void shutDownServer () throws Exception {

		connector.stop();
		node.shutDown();

		connector = null;
		node = null;

		LocalNode.reset();

		System.out.println("Connector-Log:");
		System.out.println("--------------");

		System.out.println(logStream.toString());

	}


	/**
	 * Test search and retrieval of questionnaires 
	 */
	@SuppressWarnings("unchecked")
	@Test
	public void testRetrieveSearchQuestionnaires(){

		try
		{	
			//first delete all questionnaires
			c1.sendRequest("DELETE", "mobsos-surveys/questionnaires","");

			// then generate two questionnaires, one to be searched
			c2.sendRequest("POST", "mobsos-surveys/questionnaires",generateQuestionnaireJSON().toJSONString(),"application/json","*/*",new Pair[]{});
			c3.sendRequest("POST", "mobsos-surveys/questionnaires",generateNeedleQuestionnaireJSON().toJSONString(),"application/json","*/*",new Pair[]{});

			// list only questionnaire URLs
			try {
				ClientResponse result=c1.sendRequest("GET", "mobsos-surveys/questionnaires?full=0&q", "", "*/*", "application/json", new Pair[]{});
				
				//System.out.println("List of Questionnaire URLs: " + result.getResponse());
				assertEquals(200,result.getHttpCode());
				assertTrue(result.getHeader("Content-Type").contains("application/json"));
				Object o = JSONValue.parseWithException(result.getResponse().trim());
				assertTrue(o instanceof JSONObject);
				JSONObject jo = (JSONObject) o;
				assertTrue(jo.containsKey("questionnaires"));
				o = jo.get("questionnaires");
				assertTrue(o instanceof JSONArray);
				JSONArray a = (JSONArray) o;
				assertEquals(2,a.size());

			} catch (ParseException e) {
				fail("Could not parse service response to JSON Object!");
				e.printStackTrace();
			}

			// search for specific questionnaires with query string
			try {
				ClientResponse result=c2.sendRequest("GET", "mobsos-surveys/questionnaires?full=1&q=Needle in the Haystack","");
				//System.out.println("List of Questionnaires: " + result.getResponse());

				assertEquals(200,result.getHttpCode());
				assertTrue(result.getHeader("Content-Type").contains("application/json"));
				Object o = JSONValue.parseWithException(result.getResponse().trim());
				assertTrue(o instanceof JSONObject);
				JSONObject jo = (JSONObject) o;
				assertTrue(jo.containsKey("questionnaires"));
				o = jo.get("questionnaires");
				assertTrue(o instanceof JSONArray);
				JSONArray a = (JSONArray) o;
				assertEquals(1,a.size());

			} catch (ParseException e) {
				fail("Could not parse service response to JSON Object!");
				e.printStackTrace();
			}
		}
		catch(Exception e)
		{
			e.printStackTrace();
			fail ( "Exception: " + e );
		}	
	}

	/**
	 * Test the creation of new questionnaires.
	 */

	@SuppressWarnings("unchecked")
	@Test
	public void testQuestionnaireCreation()
	{
		try {
			// first delete all questionnaires
			c2.sendRequest("DELETE", "mobsos-surveys/questionnaires","");

			// then create new questionnaire
			ClientResponse result=c1.sendRequest("POST", "mobsos-surveys/questionnaires",generateQuestionnaireJSON().toJSONString(),"application/json","*/*",new Pair[]{});

			assertEquals(201, result.getHttpCode());
			assertEquals("application/json",result.getHeader("Content-Type"));

			//System.out.println("Newly created questionnaire response: " + result.getResponse());

			Object o = JSONValue.parseWithException(result.getResponse().trim());
			assertTrue(o instanceof JSONObject);
			JSONObject jo = (JSONObject) o;
			assertTrue(jo.keySet().contains("url"));
			String urlStr = (String) jo.get("url");
			// instantiate new URL to see if result field URL contains a valid URL (throws MalformedURLException if not)
			URL url = new URL(urlStr);

		} catch (ParseException e){
			e.printStackTrace();
			fail("Could not parse service response to JSON Object!");
		} catch (MalformedURLException e){
			e.printStackTrace();
			fail("Service should return a valid URL to the new questionnaire's resource");
		} catch (Exception e){
			e.printStackTrace();
		}
	}

	/**
	 * Test the failing creation of new questionnaires with invalid data, including correctness of error messages.
	 */
	@SuppressWarnings("unchecked")
	@Test
	public void testBadRequestQuestionnaireCreation()
	{		
		JSONObject invalidQuestionnaire = generateQuestionnaireJSON(); // until now, questionnaire JSON is ok. Introduce problems now...
		invalidQuestionnaire.put("name", new Integer(2)); // name must be string

		ClientResponse result;
		
		result = c1.sendRequest("POST", "mobsos-surveys/questionnaires",invalidQuestionnaire.toJSONString(),"application/json","*/*",new Pair[]{});

		assertEquals("text/plain",result.getHeader("Content-Type"));
		assertEquals(400, result.getHttpCode());

		invalidQuestionnaire.put("name", "Alcoholics Standard Questionnaire"); //make valid again and introduce other problem
		invalidQuestionnaire.put("logo","dbis"); // malformed logo URL

		result = c1.sendRequest("POST", "mobsos-surveys/questionnaires",invalidQuestionnaire.toJSONString(),"application/json","*/*",new Pair[]{});

		assertEquals("text/plain",result.getHeader("Content-Type"));
		assertEquals(400, result.getHttpCode());

		invalidQuestionnaire.put("logo","http://dbis.rwth-aachen.de/nonexistingimage"); // non-existing logo resource

		result = c1.sendRequest("POST", "mobsos-surveys/questionnaires",invalidQuestionnaire.toJSONString(),"application/json","*/*",new Pair[]{});

		assertEquals("text/plain",result.getHeader("Content-Type"));
		assertEquals(400, result.getHttpCode());

		invalidQuestionnaire.put("logo","http://dbis.rwth-aachen.de/gadgets"); // existing non-image resource

		result = c1.sendRequest("POST", "mobsos-surveys/questionnaires",invalidQuestionnaire.toJSONString(),"application/json","*/*",new Pair[]{});

		assertEquals("text/plain",result.getHeader("Content-Type"));
		assertEquals(400, result.getHttpCode());

	}

	/**
	 * Test the deletion of all questionnaires at once.
	 */
	@SuppressWarnings("unchecked")
	@Test
	public void testDeleteAllQuestionnaires()
	{
		try {

			// first add a couple of questionnaires
			c1.sendRequest("POST", "mobsos-surveys/questionnaires",generateQuestionnaireJSON().toJSONString(),"application/json","*/*",new Pair[]{});
			c1.sendRequest("POST", "mobsos-surveys/questionnaires",generateQuestionnaireJSON().toJSONString(),"application/json","*/*",new Pair[]{});

			ClientResponse create = c1.sendRequest("POST", "mobsos-surveys/questionnaires",generateQuestionnaireJSON().toJSONString(),"application/json","*/*",new Pair[]{});
			assertEquals(201,create.getHttpCode());

			// check if deletion of all questionnaires works
			ClientResponse delete=c1.sendRequest("DELETE", "mobsos-surveys/questionnaires","");
			assertEquals(200,delete.getHttpCode());

			// then check if questionnaire list retrieval retrieves an empty list.
			ClientResponse result=c1.sendRequest("GET", "mobsos-surveys/questionnaires?full=1&q","", "*/*", "application/json", new Pair[]{});
			assertEquals(200,result.getHttpCode());
			Object o = JSONValue.parseWithException(result.getResponse().trim());
			assertTrue(o instanceof JSONObject);
			JSONObject jo = (JSONObject) o;
			assertTrue(jo.get("questionnaires") != null);
			o = jo.get("questionnaires");
			assertTrue(o instanceof JSONArray);
			JSONArray ja = (JSONArray) o;
			assertTrue(ja.isEmpty());

			// check deletion again without any existing questionnaire. Should be idempotent trivially.
			// check if deletion of all questionnaires works
			delete=c1.sendRequest("DELETE", "mobsos-surveys/questionnaires","");
			assertEquals(200,delete.getHttpCode());

			// then check if questionnaire list retrieval retrieves an empty list.
			result=c1.sendRequest("GET", "mobsos-surveys/questionnaires?full=1&q","", "*/*", "application/json", new Pair[]{});
			assertEquals(200,result.getHttpCode());
			o = JSONValue.parseWithException(result.getResponse().trim());
			assertTrue(o instanceof JSONObject);
			jo = (JSONObject) o;
			assertTrue(jo.get("questionnaires") != null);
			o = jo.get("questionnaires");
			assertTrue(o instanceof JSONArray);
			ja = (JSONArray) o;
			assertTrue(ja.isEmpty());

		}  catch (ParseException e) {
			fail("Could not parse service response to JSON Object!");
			e.printStackTrace();
		}
	}

	/**
	 * Test the retrieval of a single questionnaire.
	 */
	@SuppressWarnings("unchecked")
	@Test
	public void testQuestionnaireRetrieval(){
		try {

			// first delete all questionnaires
			c1.sendRequest("DELETE", "mobsos-surveys/questionnaires","");

			// then add a couple of questionnaires
			c1.sendRequest("POST", "mobsos-surveys/questionnaires",generateQuestionnaireJSON().toJSONString(),"application/json","*/*",new Pair[]{});
			c1.sendRequest("POST", "mobsos-surveys/questionnaires",generateQuestionnaireJSON().toJSONString(),"application/json","*/*",new Pair[]{});

			// then get complete list and pick the first questionnaire URL for subsequent testing
			ClientResponse list = c1.sendRequest("GET", "mobsos-surveys/questionnaires?full=0&q","");
			JSONObject jo = (JSONObject) JSONValue.parseWithException(list.getResponse().trim());
			String fullurl = (String) ((JSONArray) jo.get("questionnaires")).get(0);

			// check if first questionnaire URL is a valid URL, then extract path
			URL u = new URL(fullurl);
			String path = u.getPath().startsWith("/") ? u.getPath().substring(1) : u.getPath();

			// now check if questionnaire retrieval works properly
			ClientResponse result=c1.sendRequest("GET", path,"","","application/json",new Pair[]{});
			assertEquals(200,result.getHttpCode());
			Object o = JSONValue.parseWithException(result.getResponse().trim());
			assertTrue(o instanceof JSONObject);
			JSONObject rjo = (JSONObject) o;

			// check if all fields are contained in result
			assertTrue(rjo.keySet().contains("id"));
			assertTrue(rjo.keySet().contains("name"));
			assertTrue(rjo.keySet().contains("organization"));
			assertTrue(rjo.keySet().contains("logo"));
			assertTrue(rjo.keySet().contains("description"));
			assertTrue(rjo.keySet().contains("owner"));
			assertTrue(rjo.keySet().contains("lang"));

		} catch (ParseException e) {
			e.printStackTrace();
			fail("Could not parse service response to JSON Object!");
		} catch (MalformedURLException e) {
			e.printStackTrace();
			fail("Detected invalid questionnaire URL! " + e.getMessage());
		}
	}

	/**
	 * Test the updating of an existing questionnaire.
	 */
	@SuppressWarnings("unchecked")
	@Test
	public void testUpdateExistingQuestionnaire(){


		// first delete all questionnaires
		c1.sendRequest("DELETE", "mobsos-surveys/questionnaires","");

		// then add a questionnaire
		ClientResponse r = c1.sendRequest("POST", "mobsos-surveys/questionnaires",generateQuestionnaireJSON().toJSONString(),"application/json","*/*",new Pair[]{});
		assertTrue(r.getResponse()!=null);
		assertTrue(r.getResponse().length()>0);
		try{

			JSONObject o = (JSONObject) JSONValue.parseWithException(r.getResponse().trim());
			assertTrue(o.keySet().contains("url"));

			String fullurl = (String) o.get("url");

			// check if first questionnaire URL is a valid URL, then extract path
			URL u = new URL(fullurl);
			String path = u.getPath().startsWith("/") ? u.getPath().substring(1) : u.getPath();
			
			System.out.println("testUpdateExistingQuestionnaire: " + u);

			// use path to get the questionnaire
			ClientResponse result=c1.sendRequest("GET", path,"","","application/json",new Pair[]{});
			assertEquals(200,result.getHttpCode()); // questionnaire should exist

			JSONObject questionnaire = null;

			try{
				questionnaire = (JSONObject) JSONValue.parseWithException(result.getResponse());
			} catch (ParseException e){
				fail(e.getMessage());
			}

			//System.out.println(questionnaire.toJSONString());
			// change some fields in questionnaire
			questionnaire.put("name","Changed Beerdrinker questionnaire");
			questionnaire.put("description", "This questionnaire is for all those who like to drink changed beer.");

			// then call service to update existing questionnaire
			ClientResponse updateresult=c1.sendRequest("PUT", path,questionnaire.toJSONString(), "application/json","*/*",new Pair[]{});
			assertEquals(200, updateresult.getHttpCode());

			ClientResponse updated=c1.sendRequest("GET", path,"","","application/json",new Pair[]{});
			assertEquals(200,updated.getHttpCode()); // questionnaire should exist
			JSONObject updatedQuestionnaire = (JSONObject) JSONValue.parse(updated.getResponse());

			assertEquals(questionnaire,updatedQuestionnaire);

			// try to update the existing questionnaire as another user, who is not the owner. This should fail with a 401.
			questionnaire.put("name", "Ahole Questionnaire!");
			questionnaire.put("description", "I destroy your work now!");

			ClientResponse notowner = c3.sendRequest("PUT", path,questionnaire.toJSONString(), "application/json","*/*",new Pair[]{});
			//System.out.println("Response: " + notowner.getResponse());
			assertEquals(401, notowner.getHttpCode());

			// try to update the existing questionnaire as owner, but with an undefined field. This should fail with a 400.
			questionnaire.put("shibby", "shabby");

			ClientResponse invalidupdate = c1.sendRequest("PUT", path,questionnaire.toJSONString(), "application/json","*/*",new Pair[]{});
			//System.out.println("Response: " + invalidupdate.getResponse());
			assertEquals(400, invalidupdate.getHttpCode());


		} catch (ParseException e) {
			e.printStackTrace();
			fail("Service returned invalid JSON! " + e.getMessage());
		} catch (MalformedURLException e){
			e.printStackTrace();
			fail("Service returned malformed URL!");
		}
	}

	/**
	 * Test the deletion of an individual existing questionnaire.
	 */
	@SuppressWarnings("unchecked")
	@Test
	public void testDeleteExistingQuestionnaire(){
		try {

			// first delete all questionnaires
			c1.sendRequest("DELETE", "mobsos-surveys/questionnaires","");

			// then add a questionnaire
			c1.sendRequest("POST", "mobsos-surveys/questionnaires",generateQuestionnaireJSON().toJSONString(),"application/json","*/*",new Pair[]{});

			// then get complete list of questionnaire URLs and pick the first for subsequent testing
			ClientResponse list = c1.sendRequest("GET", "mobsos-surveys/questionnaires?full=0&q","");
			JSONObject jo = (JSONObject) JSONValue.parseWithException(list.getResponse().trim());
			String fullurl = (String) ((JSONArray) jo.get("questionnaires")).get(0);

			// check if first questionnaire URL is a valid URL, then extract path
			URL u = new URL(fullurl);
			String path = u.getPath().startsWith("/") ? u.getPath().substring(1) : u.getPath();

			//System.out.println("URL: " + u.toString());

			// try to delete particular questionnaire with different user than owner. Should be forbidden.
			ClientResponse delnown=c2.sendRequest("DELETE", path,"", "","",new Pair[]{});

			//System.out.println("URL: " + u.getPath());
			assertEquals(401,delnown.getHttpCode());

			// then check if questionnaire still exists.
			ClientResponse stillthere=c1.sendRequest("GET", path,"", "","application/json",new Pair[]{});
			assertEquals(200,stillthere.getHttpCode());

			// now check if deletion of particular questionnaire works
			ClientResponse delete=c1.sendRequest("DELETE", path,"");
			assertEquals(200,delete.getHttpCode());

			// then check if previously deleted questionnaire still exists.
			ClientResponse result=c1.sendRequest("GET", path,"", "","application/json",new Pair[]{});
			assertEquals(404,result.getHttpCode());

		}  catch (ParseException e) {
			e.printStackTrace();
			fail("Could not parse service response to JSON Object!");
		} catch (MalformedURLException e) {
			e.printStackTrace();
			fail("Detected invalid questionnaire URL! " + e.getMessage());
		}
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testUploadQuestionnaireForm(){
		try{
			// first delete all questionnaires
			c1.sendRequest("DELETE", "mobsos-surveys/questionnaires","");

			// then add a questionnaire
			ClientResponse r = c1.sendRequest("POST", "mobsos-surveys/questionnaires", generateQuestionnaireJSON().toJSONString(),"application/json","*/*",new Pair[]{});

			// extract questionnaire URL for subsequent requests to up/download questionnaire form
			JSONObject o = (JSONObject) JSONValue.parseWithException(r.getResponse().trim());
			URL u = new URL((String) o.get("url"));
			String path = u.getPath().startsWith("/") ? u.getPath().substring(1) : u.getPath();

			// try to download questionnaire form. Should result in not found, since no form was uploaded, yet.
			ClientResponse downl = c1.sendRequest("GET", u.getPath() + "/form", "");
			//System.out.println("Error: " + downl.getResponse());
			assertEquals(404, downl.getHttpCode());

			// read content from example questionnaire XML file
			String qform = IOUtils.getStringFromFile(new File("./doc/xml/qu1.xml"));

			// now upload form XML
			String url = path + "/form";
			ClientResponse result=c1.sendRequest("PUT", url,qform,"text/xml","*/*",new Pair[]{});
			//System.out.println("Response: " + result.getResponse());
			assertEquals(200, result.getHttpCode());

			// download form again. This should result in success.
			downl = c1.sendRequest("GET", url, "");
			assertTrue(downl.getHeader("Content-Type").contains("text/xml"));
			assertEquals(200, downl.getHttpCode());

			// try to upload form as different user than questionnaire owner. Should fail with unauthorized.
			ClientResponse notown = c3.sendRequest("PUT", url,qform,"text/xml","*/*",new Pair[]{});
			//System.out.println("Response: " + notown.getResponse());
			assertEquals(401, notown.getHttpCode());

			// try to upload invalid form as questionnaire owner. Should fail with invalid request.
			String invalidForm = "<invalid>This is an invalid questionnaire form.</invalid>";
			ClientResponse invalid = c1.sendRequest("PUT", url,invalidForm,"text/xml","*/*",new Pair[]{});
			//System.out.println("Response: " + invalid.getResponse());
			assertEquals(400, invalid.getHttpCode());

		} catch (MalformedURLException e){
			e.printStackTrace();
			fail("Service returned malformed URL!");
		} catch (IOException e) {
			e.printStackTrace();
			fail("An unexpected exception occurred on loading test form data: "+e.getMessage());
		} catch (ParseException e) {
			e.printStackTrace();
			fail("Service returned invalid JSON! " + e.getMessage());
		} 
	}

	/**
	 * Test search and retrieval of surveys
	 */
	@SuppressWarnings("unchecked")
	@Test
	public void testRetrieveSearchSurveys(){
		try
		{
			//first delete all surveys
			c1.sendRequest("DELETE", "mobsos-surveys/surveys","");

			// then generate two surveys, one to be searched
			c2.sendRequest("POST", "mobsos-surveys/surveys",generateSurveyJSON().toJSONString(),"application/json","*/*",new Pair[]{});
			c3.sendRequest("POST", "mobsos-surveys/surveys",generateNeedleSurveyJSON().toJSONString(),"application/json","*/*",new Pair[]{});

			// list only survey URLs
			try {
				ClientResponse result=c1.sendRequest("GET", "mobsos-surveys/surveys?full=0&q","","*/*","application/json",new Pair[]{});
				//System.out.println("List of Survey URLs: " + result.getResponse());

				assertEquals(200,result.getHttpCode());
				assertTrue(result.getHeader("Content-Type").contains("application/json"));
				Object o = JSONValue.parseWithException(result.getResponse().trim());
				assertTrue(o instanceof JSONObject);
				JSONObject jo = (JSONObject) o;
				assertTrue(jo.containsKey("surveys"));
				o = jo.get("surveys");
				assertTrue(o instanceof JSONArray);
				JSONArray a = (JSONArray) o;
				assertEquals(2,a.size());

			} catch (ParseException e) {
				fail("Could not parse service response to JSON Object!");
				e.printStackTrace();
			}

			// search for specific surveys with query string
			try {
				ClientResponse result=c2.sendRequest("GET", "mobsos-surveys/surveys?full=1&q=Needle in the Haystack","","*/*","application/json",new Pair[]{});
				//System.out.println("List of surveys: " + result.getResponse());

				assertEquals(200,result.getHttpCode());
				assertTrue(result.getHeader("Content-Type").contains("application/json"));
				Object o = JSONValue.parseWithException(result.getResponse().trim());
				assertTrue(o instanceof JSONObject);
				JSONObject jo = (JSONObject) o;
				assertTrue(jo.containsKey("surveys"));
				o = jo.get("surveys");
				assertTrue(o instanceof JSONArray);
				JSONArray a = (JSONArray) o;
				assertEquals(1,a.size());

			} catch (ParseException e) {
				fail("Could not parse service response to JSON Object!");
				e.printStackTrace();
			}
		}
		catch(Exception e)
		{
			e.printStackTrace();
			fail ( "Exception: " + e );
		}	
	}

	/**
	 * Test the creation of new surveys.
	 */
	@SuppressWarnings("unchecked")
	@Test
	public void testSurveyCreation()
	{
		try {
			// first delete all surveys
			c2.sendRequest("DELETE", "mobsos-surveys/surveys","");

			// then create new questionnaire
			ClientResponse result=c1.sendRequest("POST", "mobsos-surveys/surveys",generateSurveyJSON().toJSONString(),"application/json","*/*",new Pair[]{});

			assertEquals(201, result.getHttpCode());
			assertEquals("application/json",result.getHeader("Content-Type"));

			//System.out.println("Newly created survey response: " + result.getResponse());

			Object o = JSONValue.parseWithException(result.getResponse().trim());
			assertTrue(o instanceof JSONObject);
			JSONObject jo = (JSONObject) o;
			assertTrue(jo.keySet().contains("url"));
			String urlStr = (String) jo.get("url");
			// instantiate new URL to see if result field URL contains a valid URL (throws MalformedURLException if not)
			URL url = new URL(urlStr);

		} catch (ParseException e){
			e.printStackTrace();
			fail("Could not parse service response to JSON Object!");
		} catch (MalformedURLException e){
			e.printStackTrace();
			fail("Service should return a valid URL to the new questionnaire's resource");
		}
	}

	/**
	 * Test the creation of new surveys with invalid data.
	 */
	@SuppressWarnings("unchecked")
	@Test
	public void testBadRequestSurveyCreation()
	{
		// first delete all surveys
		c2.sendRequest("DELETE", "mobsos-surveys/surveys","");

		// now try creating surveys with invalid data

		JSONObject invalidSurvey = generateSurveyJSON(); // until now, survey JSON is ok. Introduce problems now...
		invalidSurvey.put("name", new Integer(2)); // name must be string

		ClientResponse result=c1.sendRequest("POST", "mobsos-surveys/surveys",invalidSurvey.toJSONString(),"application/json","*/*",new Pair[]{});
		assertEquals(400, result.getHttpCode());

		invalidSurvey.put("name", "Alcoholics Standard Survey"); //make valid again and introduce other problem
		invalidSurvey.put("start", "20144-33-44T1000"); //introduce wrong time

		result=c1.sendRequest("POST", "mobsos-surveys/surveys",invalidSurvey.toJSONString(),"application/json","*/*",new Pair[]{});
		assertEquals(400, result.getHttpCode());

		invalidSurvey.put("start", "2014-05-08T12:00:00Z"); //make valid again and introduce other problem
		invalidSurvey.put("end", "2014-04-01T00:00:00Z"); // end time before start time

		result=c1.sendRequest("POST", "mobsos-surveys/surveys",invalidSurvey.toJSONString(),"application/json","*/*",new Pair[]{});
		assertEquals(400, result.getHttpCode());

		invalidSurvey.put("end", "2014-06-08T00:00:00Z"); // make valid again and introduce other problem
		invalidSurvey.put("logo","dbis"); // malformed logo URL

		result=c1.sendRequest("POST", "mobsos-surveys/surveys",invalidSurvey.toJSONString(),"application/json","*/*",new Pair[]{});
		assertEquals(400, result.getHttpCode());

		invalidSurvey.put("logo","http://dbis.rwth-aachen.de/nonexistingimage"); // non-existing logo resource

		result=c1.sendRequest("POST", "mobsos-surveys/surveys",invalidSurvey.toJSONString(),"application/json","*/*",new Pair[]{});
		assertEquals(400, result.getHttpCode());

		invalidSurvey.put("logo","http://dbis.rwth-aachen.de/gadgets"); // existing non-image resource

		result=c1.sendRequest("POST", "mobsos-surveys/surveys",invalidSurvey.toJSONString(),"application/json","*/*",new Pair[]{});
		assertEquals(400, result.getHttpCode());
		
		// using OIDC now.
		//invalidSurvey.put("logo","http://dbis.rwth-aachen.de/cms/images/logo.jpg"); // make valid again and introduce other problem
		//invalidSurvey.put("resource","shitonashingle"); // malformed resource URL
		
		//result=c1.sendRequest("POST", "mobsos-surveys/surveys",invalidSurvey.toJSONString(),"application/json","*/*",new Pair[]{});
		//assertEquals(400, result.getHttpCode());

		//invalidSurvey.put("resource","http://dbis.rwth-aachen.de/nonexistingresource"); // non-existing resource URL

		//result=c1.sendRequest("POST", "mobsos-surveys/surveys",invalidSurvey.toJSONString(),"application/json","*/*",new Pair[]{});
		//assertEquals(400, result.getHttpCode());
		

	}

	/**
	 * Test the deletion of all surveys at once.
	 */
	@SuppressWarnings("unchecked")
	@Test
	public void testDeleteAllSurveys()
	{
		try {

			// first add a couple of surveys
			c1.sendRequest("POST", "mobsos-surveys/surveys",generateSurveyJSON().toJSONString(),"application/json","*/*",new Pair[]{});
			c1.sendRequest("POST", "mobsos-surveys/surveys",generateSurveyJSON().toJSONString(),"application/json","*/*",new Pair[]{});

			ClientResponse create = c1.sendRequest("POST", "mobsos-surveys/surveys",generateSurveyJSON().toJSONString(),"application/json","*/*",new Pair[]{});
			assertEquals(201,create.getHttpCode());

			// check if deletion of all surveys works
			ClientResponse delete=c1.sendRequest("DELETE", "mobsos-surveys/surveys","");
			assertEquals(200,delete.getHttpCode());

			// then check if survey list retrieval retrieves an empty list.
			ClientResponse result=c1.sendRequest("GET", "mobsos-surveys/surveys?full=1&q","", "*/*","application/json",new Pair[]{});
			assertEquals(200,result.getHttpCode());
			Object o = JSONValue.parseWithException(result.getResponse().trim());
			assertTrue(o instanceof JSONObject);
			JSONObject jo = (JSONObject) o;
			assertTrue(jo.get("surveys") != null);
			o = jo.get("surveys");
			assertTrue(o instanceof JSONArray);
			JSONArray ja = (JSONArray) o;
			assertTrue(ja.isEmpty());

			// check deletion again without any existing surveys. Should be idempotent trivially.
			// check if deletion of all surveys works
			delete=c1.sendRequest("DELETE", "mobsos-surveys/surveys","");
			assertEquals(200,delete.getHttpCode());

			// then check if survey list retrieval retrieves an empty list.
			result=c1.sendRequest("GET", "mobsos-surveys/surveys?full=1&q","", "*/*","application/json",new Pair[]{});
			assertEquals(200,result.getHttpCode());
			o = JSONValue.parseWithException(result.getResponse().trim());
			assertTrue(o instanceof JSONObject);
			jo = (JSONObject) o;
			assertTrue(jo.get("surveys") != null);
			o = jo.get("surveys");
			assertTrue(o instanceof JSONArray);
			ja = (JSONArray) o;
			assertTrue(ja.isEmpty());

		}  catch (ParseException e) {
			fail("Could not parse service response to JSON Object!");
			e.printStackTrace();
		}
	}

	/**
	 * Test the retrieval of a single survey.
	 */
	@SuppressWarnings("unchecked")
	@Test
	public void testSurveyRetrieval(){
		try {
			// first delete all surveys
			c1.sendRequest("DELETE", "mobsos-surveys/surveys","");

			// then add a couple of surveys
			
			c1.sendRequest("POST", "mobsos-surveys/surveys",generateSurveyJSON().toJSONString(),"application/json","*/*",new Pair[]{});
			c1.sendRequest("POST", "mobsos-surveys/surveys",generateSurveyJSON().toJSONString(),"application/json","*/*",new Pair[]{});

			// then get complete list and pick the first survey URL for subsequent testing
			ClientResponse list = c1.sendRequest("GET", "mobsos-surveys/surveys?full=0&q","");
			JSONObject jo = (JSONObject) JSONValue.parseWithException(list.getResponse().trim());
			String fullurl = (String) ((JSONArray) jo.get("surveys")).get(0);

			// check if first survey URL is a valid URL, then extract path
			URL u = new URL(fullurl);
			String path = u.getPath().startsWith("/") ? u.getPath().substring(1) : u.getPath();
			
			// now check if survey retrieval works properly
			ClientResponse result=c1.sendRequest("GET", path,"","*/*","application/json",new Pair[]{});
			assertEquals(200,result.getHttpCode());
			Object o = JSONValue.parseWithException(result.getResponse().trim());
			assertTrue(o instanceof JSONObject);
			JSONObject rjo = (JSONObject) o;

			// check if all fields are contained in result
			assertTrue(rjo.keySet().contains("id"));
			assertTrue(rjo.keySet().contains("name"));
			assertTrue(rjo.keySet().contains("organization"));
			assertTrue(rjo.keySet().contains("logo"));
			assertTrue(rjo.keySet().contains("description"));
			assertTrue(rjo.keySet().contains("owner"));
			assertTrue(rjo.keySet().contains("resource"));
			assertTrue(rjo.keySet().contains("start"));
			assertTrue(rjo.keySet().contains("end"));
			assertTrue(rjo.keySet().contains("lang"));

		} catch (ParseException e) {
			e.printStackTrace();
			fail("Could not parse service response to JSON Object!");
		} catch (MalformedURLException e) {
			e.printStackTrace();
			fail("Detected invalid survey URL! " + e.getMessage());
		} catch (Exception e){
			e.printStackTrace();
		}
	}

	/**
	 * Test the updating of an existing survey.
	 */
	@SuppressWarnings("unchecked")
	@Test
	public void testUpdateExistingSurvey(){


		// first delete all surveys
		c1.sendRequest("DELETE", "mobsos-surveys/surveys","");

		// then add a survey
		ClientResponse r = c1.sendRequest("POST", "mobsos-surveys/surveys",generateSurveyJSON().toJSONString(),"application/json","*/*",new Pair[]{});
		assertTrue(r.getResponse()!=null);
		assertTrue(r.getResponse().length()>0);
		try{

			JSONObject o = (JSONObject) JSONValue.parseWithException(r.getResponse().trim());
			assertTrue(o.keySet().contains("url"));

			String fullurl = (String) o.get("url");

			// check if first survey URL is a valid URL, then extract path
			URL u = new URL(fullurl);
			String path = u.getPath().startsWith("/") ? u.getPath().substring(1) : u.getPath();

			// use path to get the survey
			ClientResponse result=c1.sendRequest("GET", path,"","","application/json",new Pair[]{});
			assertEquals(200,result.getHttpCode()); // survey should exist

			JSONObject survey = null;

			try{
				survey= (JSONObject) JSONValue.parseWithException(result.getResponse());
			} catch (ParseException e){
				fail(e.getMessage());
			}

			// change some fields in survey
			survey.put("name","Changed Beerdrinker survey");
			survey.put("description", "This survey is for all those who like to drink changed beer.");

			// then call service to update existing survey
			ClientResponse updateresult=c1.sendRequest("PUT", path,survey.toJSONString(), "application/json","*/*",new Pair[]{});
			assertEquals(200, updateresult.getHttpCode());

			ClientResponse updated=c1.sendRequest("GET", path,"","","application/json",new Pair[]{});
			assertEquals(200,updated.getHttpCode()); // survey should exist
			JSONObject updatedSurvey = (JSONObject) JSONValue.parse(updated.getResponse());

			assertEquals(survey,updatedSurvey);

			// try to update the existing survey as another user, who is not the owner. This should fail with a 401.
			survey.put("name", "Ahole Questionnaire!");
			survey.put("description", "I destroy your work now!");

			ClientResponse notowner = c3.sendRequest("PUT", path,survey.toJSONString(), "application/json","*/*",new Pair[]{});
			//System.out.println("Response: " + notowner.getResponse());
			assertEquals(401, notowner.getHttpCode());

			// try to update the existing survey as owner, but with an undefined field. This should fail with a 400.
			survey.put("shibby", "shabby");

			ClientResponse invalidupdate = c1.sendRequest("PUT", path,survey.toJSONString(), "application/json","*/*",new Pair[]{});
			//System.out.println("Response: " + invalidupdate.getResponse());
			assertEquals(400, invalidupdate.getHttpCode());


		} catch (ParseException e) {
			e.printStackTrace();
			fail("Service returned invalid JSON! " + e.getMessage());
		} catch (MalformedURLException e){
			e.printStackTrace();
			fail("Service returned malformed URL!");
		}
	}


	/**
	 * Test the deletion of an individual existing survey.
	 */
	@SuppressWarnings("unchecked")
	@Test
	public void testDeleteExistingSurvey(){
		try {

			// first delete all surveys
			c1.sendRequest("DELETE", "mobsos-surveys/surveys","");

			// then add a survey
			c1.sendRequest("POST", "mobsos-surveys/surveys",generateSurveyJSON().toJSONString(),"application/json","*/*",new Pair[]{});

			// then get complete list of survey URLs and pick the first for subsequent testing
			ClientResponse list = c1.sendRequest("GET", "mobsos-surveys/surveys?full=0&q","");
			JSONObject jo = (JSONObject) JSONValue.parseWithException(list.getResponse().trim());
			String fullurl = (String) ((JSONArray) jo.get("surveys")).get(0);

			// check if first survey URL is a valid URL, then extract path
			URL u = new URL(fullurl);
			String path = u.getPath().startsWith("/") ? u.getPath().substring(1) : u.getPath();
			//System.out.println("URL: " + u.toString());

			// try to delete particular survey with different user than owner. Should be forbidden.
			ClientResponse delnown=c2.sendRequest("DELETE", path,"");
			assertEquals(401,delnown.getHttpCode());

			// then check if survey still exists.
			ClientResponse stillthere=c1.sendRequest("GET", path,"", "","application/json",new Pair[]{});
			assertEquals(200,stillthere.getHttpCode());

			// now check if deletion of particular survey works
			ClientResponse delete=c1.sendRequest("DELETE", path,"");
			assertEquals(200,delete.getHttpCode());

			// then check if previously deleted survey still exists.
			ClientResponse result=c1.sendRequest("GET", path,"", "","application/json",new Pair[]{});
			assertEquals(404,result.getHttpCode());

		}  catch (ParseException e) {
			e.printStackTrace();
			fail("Could not parse service response to JSON Object!");
		} catch (MalformedURLException e) {
			e.printStackTrace();
			fail("Detected invalid survey URL! " + e.getMessage());
		}
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testSubmitQuestionnaireAnswer(){
		try{
			// first add a new questionnaire
			ClientResponse r = c1.sendRequest("POST", "mobsos-surveys/questionnaires",generateQuestionnaireJSON().toJSONString(),"application/json","*/*", new Pair[]{});

			JSONObject o = (JSONObject) JSONValue.parseWithException(r.getResponse().trim());
			URL u = new URL((String) o.get("url"));
			String path = u.getPath().startsWith("/") ? u.getPath().substring(1) : u.getPath();
			// read content from example questionnaire XML file
			String qform = IOUtils.getStringFromFile(new File("./doc/xml/qu2.xml"));
			
			// upload questionnaire XML content
			ClientResponse result=c1.sendRequest("PUT", path + "/form",qform,"text/xml","*/*", new Pair[]{});
			System.err.println(result.getResponse());
			assertEquals(200, result.getHttpCode());

			// then create survey using the previously created questionnaire and form
			ClientResponse csvres=c1.sendRequest("POST", "mobsos-surveys/surveys",generateSurveyJSON().toJSONString(),"application/json","*/*", new Pair[]{});
			JSONObject svu = (JSONObject) JSONValue.parseWithException(csvres.getResponse().trim());
			URL su = new URL((String) svu.get("url"));
			String path2 = su.getPath().startsWith("/") ? su.getPath().substring(1) : su.getPath();
			//System.out.println("Survey URL:" + su);
			//System.out.println("Questionnaire URL: " + u);

			int qid = Integer.parseInt(u.getPath().substring(u.getPath().lastIndexOf("/")+1));
			//System.out.println("Questionnaire id: " + qid);

			// now set questionnaire to survey
			JSONObject qidset = new JSONObject();
			qidset.put("qid", qid);

			ClientResponse ares=c1.sendRequest("POST", path2 + "/questionnaire",qidset.toJSONString(),"application/json","*/*", new Pair[]{});
			assertEquals(200, ares.getHttpCode());

			// read content from example questionnaire response XML file
			String qanswer = IOUtils.getStringFromFile(new File("./doc/xml/qa2.xml"));
			// submit questionnaire answer XML content
			ares=c1.sendRequest("POST", path2 + "/responses",qanswer,"text/xml","*/*",new Pair[]{});
			assertEquals(200, ares.getHttpCode());

			// do the same with a second user and another result
			String qa3=IOUtils.getStringFromFile(new File("./doc/xml/qa3.xml"));
			ares=c2.sendRequest("POST", path2 + "/responses",qa3,"text/xml","*/*",new Pair[]{});
			assertEquals(200, ares.getHttpCode());

			// do the same with a third user and another result, submitted as JSON
			JSONObject qa3Json = new JSONObject();
			qa3Json.put("A.2.3","Der gestiefelte Kater");
			qa3Json.put("A.2.2","4");
			qa3Json.put("A.2.1","0");

			ares=c3.sendRequest("POST", path2 + "/responses",qa3Json.toJSONString(),"application/json","*/*",new Pair[]{});
			assertEquals(200, ares.getHttpCode());

			// now try to get results
			ClientResponse ga=c1.sendRequest("GET", path2 + "/responses?sepline=0&sep","","","text/csv",new Pair[]{});
			//System.out.println(ga.getResponse());
			assertEquals(200, ga.getHttpCode());
			
			// finally, try to submit another response for a user that already submitted. Should result in conflict.
			qa3Json.put("A.2.3","Der gestiefelte Kater mit Mantel");

			ares=c3.sendRequest("POST", path2 + "/responses",qa3Json.toJSONString(),"application/json","*/*",new Pair[]{});
			assertEquals(409, ares.getHttpCode());

		} catch (MalformedURLException e){
			e.printStackTrace();
			fail("Service returned malformed URL!");
		} catch (IOException e) {
			e.printStackTrace();
			fail("An unexpected exception occurred on loading test form data: "+e.getMessage());
		} catch (ParseException e) {
			e.printStackTrace();
			fail("Service returned invalid JSON! " + e.getMessage());
		} 
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testSubmitInvalidQuestionnaireAnswer(){
		try{
			// first delete all surveys & questionnaires
			c1.sendRequest("DELETE", "mobsos-surveys/surveys","");
			c1.sendRequest("DELETE", "mobsos-surveys/questionnaires","");

			// first add a new questionnaire
			ClientResponse r = c1.sendRequest("POST", "mobsos-surveys/questionnaires",generateQuestionnaireJSON().toJSONString(),"application/json","*/*", new Pair[]{});

			JSONObject o = (JSONObject) JSONValue.parseWithException(r.getResponse().trim());
			URL u = new URL((String) o.get("url"));
			String path = u.getPath().startsWith("/") ? u.getPath().substring(1) : u.getPath();
			
			// read content from example questionnaire XML file
			String qform = IOUtils.getStringFromFile(new File("./doc/xml/qu2.xml"));

			// upload questionnaire XML content
			ClientResponse result=c1.sendRequest("PUT", path + "/form",qform,"text/xml","*/*", new Pair[]{});
			assertEquals(200, result.getHttpCode());

			// then create survey using the previously created questionnaire and form
			ClientResponse csvres=c1.sendRequest("POST", "mobsos-surveys/surveys?full&q",generateSurveyJSON().toJSONString(),"application/json","*/*", new Pair[]{});
			JSONObject svu = (JSONObject) JSONValue.parseWithException(csvres.getResponse().trim());
			URL su = new URL((String) svu.get("url"));
			String path2 = su.getPath().startsWith("/") ? su.getPath().substring(1) : su.getPath();
			
			int qid = Integer.parseInt(u.getPath().substring(u.getPath().lastIndexOf("/")+1));

			// now set questionnaire to survey
			JSONObject qidset = new JSONObject();
			qidset.put("qid", qid);
			
			ClientResponse ares=c1.sendRequest("POST", path2 + "/questionnaire",qidset.toJSONString(),"application/json","*/*", new Pair[]{});
			assertEquals(200, ares.getHttpCode());

			// read content from sample invalid questionnaire answer XML file
			String qanswer1 = IOUtils.getStringFromFile(new File("./doc/xml/qa2-invalid-mandatory-question.xml"));
			// submit questionnaire answer XML content
			ClientResponse ares1=c1.sendRequest("POST", path2 + "/responses",qanswer1,"application/json","*/*", new Pair[]{});

			assertEquals(400, ares1.getHttpCode());
			// currently, the following assertion will fail due to issue LAS-49 (http://layers.dbis.rwth-aachen.de/jira/browse/LAS-49).
			// TODO: uncomment, when issue is resolved.
			//assertTrue(ares1.getResponse().contains("The mandatory question A.2.3 was not answered!"));

			// read content from sample invalid questionnaire answer XML file
			String qanswer2 = IOUtils.getStringFromFile(new File("./doc/xml/qa2-invalid-wrong-answertype.xml"));
			// submit questionnaire answer XML content
			ClientResponse ares2=c1.sendRequest("POST", path2 + "/responses",qanswer2,"application/json","*/*", new Pair[]{});

			assertEquals(400, ares2.getHttpCode());
			// currently, the following assertion will fail due to issue LAS-49 (http://layers.dbis.rwth-aachen.de/jira/browse/LAS-49).
			// TODO: uncomment, when issue is resolved.
			//assertTrue(ares2.getResponse().contains("is expected to be parseable as an integer!"));

			// read content from sample invalid questionnaire answer XML file
			String qanswer3 = IOUtils.getStringFromFile(new File("./doc/xml/qa2-invalid-undefined-question.xml"));
			// submit questionnaire answer XML content
			ClientResponse ares3=c1.sendRequest("POST", path2 + "/responses", qanswer3,"application/json","*/*", new Pair[]{});

			assertEquals(400, ares3.getHttpCode());
			// currently, the following assertion will fail due to issue LAS-49 (http://layers.dbis.rwth-aachen.de/jira/browse/LAS-49).
			// TODO: uncomment, when issue is resolved.
			//assertTrue(ares3.getResponse().contains("some string in error message"));

			// read content from sample invalid questionnaire answer XML file
			String qanswer4 = IOUtils.getStringFromFile(new File("./doc/xml/qa2-invalid-question-answertime.xml"));
			// submit questionnaire answer XML content
			ClientResponse ares4=c1.sendRequest("POST", path2 + "/responses",qanswer4,"application/json","*/*", new Pair[]{});

			assertEquals(400, ares4.getHttpCode());
			// currently, the following assertion will fail due to issue LAS-49 (http://layers.dbis.rwth-aachen.de/jira/browse/LAS-49).
			// TODO: uncomment, when issue is resolved.
			//assertTrue(ares4.getResponse().contains("some string in error message"));

		} catch (MalformedURLException e){
			e.printStackTrace();
			fail("Service returned malformed URL!");
		} catch (IOException e) {
			e.printStackTrace();
			fail("An unexpected exception occurred on loading test form data: "+e.getMessage());
		} catch (ParseException e) {
			e.printStackTrace();
			fail("Service returned invalid JSON! " + e.getMessage());
		} 
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testSubmitSurveyResponseEarlyLate(){
		try{
			// generate some relative timepoints to now
			Calendar cal = Calendar.getInstance();
			Date today = cal.getTime();
			cal.add(Calendar.DAY_OF_YEAR,-1);
			Date yesterday = cal.getTime();
			cal.add(Calendar.DAY_OF_YEAR,2);
			Date tomorrow = cal.getTime();
			cal.add(Calendar.YEAR, 1);
			Date nextyear = cal.getTime();
			cal.add(Calendar.YEAR, -2);
			Date lastyear = cal.getTime();

			SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
			df.setTimeZone(TimeZone.getTimeZone("GMT"));

			String lastyearUTC = df.format(lastyear);
			String yesterdayUTC = df.format(yesterday);
			String nowUTC = df.format(today);
			String tomorrowUTC = df.format(tomorrow);
			String nextyearUTC = df.format(nextyear);

			
			System.out.println("Last Year: " + lastyearUTC);
			System.out.println("Yesterday: " + yesterdayUTC);
			System.out.println("Now: " + nowUTC);
			System.out.println("Tomorrow: " + tomorrowUTC);
			System.out.println("Next Year: " + nextyearUTC);
			
			// first delete all surveys & questionnaires
			c1.sendRequest("DELETE", "mobsos-surveys/surveys","");
			c1.sendRequest("DELETE", "mobsos-surveys/questionnaires","");

			// first add a new questionnaire
			ClientResponse r = c1.sendRequest("POST", "mobsos-surveys/questionnaires",generateQuestionnaireJSON().toJSONString(),"application/json","*/*", new Pair[]{});

			JSONObject o = (JSONObject) JSONValue.parseWithException(r.getResponse().trim());
			URL u = new URL((String) o.get("url"));
			String path = u.getPath().startsWith("/") ? u.getPath().substring(1) : u.getPath();
			// read content from example questionnaire XML file
			String qform = IOUtils.getStringFromFile(new File("./doc/xml/qu2.xml"));

			// upload questionnaire XML content
			ClientResponse result=c1.sendRequest("PUT", path + "/form",qform,"text/xml","*/*", new Pair[]{});
			assertEquals(200, result.getHttpCode());

			// then create survey that is already expired
			ClientResponse csvres_exp=c1.sendRequest("POST", "mobsos-surveys/surveys",generateSurveyJSON(lastyearUTC,yesterdayUTC).toJSONString(),"application/json","*/*", new Pair[]{});
			JSONObject svu_exp = (JSONObject) JSONValue.parseWithException(csvres_exp.getResponse().trim());
			URL su_exp = new URL((String) svu_exp.get("url"));
			String path2 = su_exp.getPath().startsWith("/") ? su_exp.getPath().substring(1) : su_exp.getPath();
			int qid = Integer.parseInt(u.getPath().substring(u.getPath().lastIndexOf("/")+1));

			// now set questionnaire to survey
			JSONObject qidset = new JSONObject();
			qidset.put("qid", qid);

			ClientResponse ares=c1.sendRequest("POST", path2 + "/questionnaire",qidset.toJSONString(),"application/json","*/*", new Pair[]{});
			assertEquals(200, ares.getHttpCode());

			// then create survey starting in the future
			ClientResponse csvres_fut=c1.sendRequest("POST", "mobsos-surveys/surveys",generateSurveyJSON(tomorrowUTC, nextyearUTC).toJSONString(),"application/json","*/*", new Pair[]{});
			JSONObject svu_fut = (JSONObject) JSONValue.parseWithException(csvres_fut.getResponse().trim());
			URL su_fut = new URL((String) svu_fut.get("url"));
			String path3 = su_fut.getPath().startsWith("/") ? su_fut.getPath().substring(1) : su_fut.getPath();
			
			ares=c1.sendRequest("POST", path3 + "/questionnaire",qidset.toJSONString(),"application/json","*/*", new Pair[]{});
			assertEquals(200, ares.getHttpCode());

			// now we are ready to submit a response for given survey.

			// read content from example questionnaire response XML file
			String qanswer = IOUtils.getStringFromFile(new File("./doc/xml/qa2.xml"));

			// submit survey response to expired survey. Should result in forbidden.
			ares=c1.sendRequest("POST", path2 + "/responses",qanswer,"text/xml","*/*",new Pair[]{});
			assertEquals(403, ares.getHttpCode());
			//assertEquals("Cannot submit response. Survey expired.", ares.getResponse());

			// submit survey response to expired survey. Should result in forbidden.
			ares=c1.sendRequest("POST", path3 + "/responses",qanswer,"text/xml","*/*",new Pair[]{});
			assertEquals(403, ares.getHttpCode());
			//assertEquals("Cannot submit response. Survey has not begun, yet.", ares.getResponse());
		
			
		} catch (MalformedURLException e){
			e.printStackTrace();
			fail("Service returned malformed URL!");
		} catch (IOException e) {
			e.printStackTrace();
			fail("An unexpected exception occurred on loading test form data: "+e.getMessage());
		} catch (ParseException e) {
			e.printStackTrace();
			fail("Service returned invalid JSON! " + e.getMessage());
		} 
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Test
	public void testGetSurveyQuestionnaireForm(){
		try{
			// first delete all surveys & questionnaires
			c1.sendRequest("DELETE", "mobsos-surveys/surveys","");
			c1.sendRequest("DELETE", "mobsos-surveys/questionnaires","");

			// first add a new questionnaire
			ClientResponse r = c1.sendRequest("POST", "mobsos-surveys/questionnaires",generateQuestionnaireJSON().toJSONString(),"application/json","*/*", new Pair[]{});

			JSONObject o = (JSONObject) JSONValue.parseWithException(r.getResponse().trim());
			URL u = new URL((String) o.get("url"));
			String path = u.getPath().startsWith("/") ? u.getPath().substring(1) : u.getPath();

			// read content from example questionnaire XML file
			String qform = IOUtils.getStringFromFile(new File("./doc/xml/qu2.xml"));

			// upload questionnaire XML content

			ClientResponse result=c1.sendRequest("PUT", path + "/form",qform,"text/xml","*/*", new Pair[]{});
			//System.out.println(result.getResponse());
			assertEquals(200, result.getHttpCode());
			// then create survey using the previously created questionnaire and form
			ClientResponse csvres=c1.sendRequest("POST", "mobsos-surveys/surveys",generateSurveyJSON().toJSONString(),"application/json","*/*", new Pair[]{});
			JSONObject svu = (JSONObject) JSONValue.parseWithException(csvres.getResponse().trim());
			URL su = new URL((String) svu.get("url"));
			String path2 = su.getPath().startsWith("/") ? su.getPath().substring(1) : su.getPath();
			int qid = Integer.parseInt(u.getPath().substring(u.getPath().lastIndexOf("/")+1));

			// now set questionnaire to survey
			JSONObject qidset = new JSONObject();
			qidset.put("qid", qid);

			ClientResponse ares=c1.sendRequest("POST", path2 + "/questionnaire",qidset.toJSONString(),"application/json","*/*", new Pair[]{});
			assertEquals(200, ares.getHttpCode());

			// now we are ready to download questionnaire form for given survey.
			Pair lang = new Pair("accept-language","de-DE"); 
			ClientResponse qsfres=c1.sendRequest("GET", path2 + "/questionnaire","","","text/html",new Pair[]{lang});
			assertEquals(200, qsfres.getHttpCode());


		} catch (MalformedURLException e){
			e.printStackTrace();
			fail("Service returned malformed URL!");
		} catch (IOException e) {
			e.printStackTrace();
			fail("An unexpected exception occurred on loading test form data: "+e.getMessage());
		} catch (ParseException e) {
			e.printStackTrace();
			fail("Service returned invalid JSON! " + e.getMessage());
		} 
	}
	
	
	//@SuppressWarnings("unchecked")
	//@Test
	//public void testResourceMeta(){
	//	ClientResponse res=c1.sendRequest("POST", "mobsos-surveys/resource-meta","http://wikipedia.org","text/plain","*/*",new Pair[]{});
	//	System.out.println("Resource Meta: " + res.getResponse());
	//	assertEquals(200, res.getHttpCode());
	//}




	//	@Test
	//	public void testSubmitQuestionnaireAnswerWithCommunity(){
	//		try{
	//			// first add a new questionnaire
	//			ClientResponse r = c1.sendRequest("POST", "mobsos-surveys/questionnaires",generateQuestionnaireJSON().toJSONString());
	//			JSONObject o = (JSONObject) JSONValue.parseWithException(r.getResponse().trim());
	//			URL u = new URL((String) o.get("url"));
	//
	//			// read content from example questionnaire XML file
	//			String qform = IOUtils.getStringFromFile(new File("./doc/xml/qu2.xml"));
	//
	//			// upload questionnaire XML content
	//			ClientResponse result=c1.sendRequest("POST", u.getPath() + "/form",qform);
	//			assertEquals(200, result.getHttpCode());
	//
	//			// then create survey using the previously created questionnaire and form
	//			ClientResponse csvres=c1.sendRequest("POST", "mobsos-surveys/surveys",generateSurveyJSON().toJSONString());
	//			JSONObject svu = (JSONObject) JSONValue.parseWithException(csvres.getResponse().trim());
	//			URL su = new URL((String) svu.get("url"));
	//
	//			System.out.println("Survey URL:" + su);
	//			System.out.println("Questionnaire URL: " + u);
	//
	//			int qid = Integer.parseInt(u.getPath().substring(u.getPath().lastIndexOf("/")+1));
	//			System.out.println("Questionnaire id: " + qid);
	//
	//			// now set questionnaire to survey
	//			JSONObject qidset = new JSONObject();
	//			qidset.put("qid", qid);
	//
	//			ClientResponse ares=c1.sendRequest("POST", su.getPath() + "/questionnaire",qidset.toJSONString());
	//			assertEquals(200, ares.getHttpCode());
	//
	//			// read content from example questionnaire answer XML file
	//			String qanswer = IOUtils.getStringFromFile(new File("./doc/xml/qa2.xml"));
	//			// submit questionnaire answer XML content
	//			ares=c1.sendRequest("POST", su.getPath() + "/answers/"+group1.getId(),qanswer);
	//			assertEquals(200, ares.getHttpCode());
	//
	//			// do the same with a second user and another result
	//			String qa3=IOUtils.getStringFromFile(new File("./doc/xml/qa3.xml"));
	//			ares=c2.sendRequest("POST", su.getPath() + "/answers/"+group1.getId(),qa3);
	//			assertEquals(200, ares.getHttpCode());
	//			
	//			// now try to get results
	//			ClientResponse ga=c1.sendRequest("GET", su.getPath() + "/answers/"+group1.getId(),"");
	//			assertEquals(200, ga.getHttpCode());
	//			System.out.println(ga.getResponse());
	//
	//		} catch (MalformedURLException e){
	//			e.printStackTrace();
	//			fail("Service returned malformed URL!");
	//		} catch (IOException e) {
	//			e.printStackTrace();
	//			fail("An unexpected exception occurred on loading test form data: "+e.getMessage());
	//		} catch (ParseException e) {
	//			e.printStackTrace();
	//			fail("Service returned invalid JSON! " + e.getMessage());
	//		} 
	//	}






	/*
	@Test
	public void testGetSurveyQuestionnaireFormWithCommunityContext(){
		try{
			// first add a new questionnaire
			ClientResponse r = c1.sendRequest("POST", "mobsos-surveys/questionnaires",generateQuestionnaireJSON().toJSONString());
			JSONObject o = (JSONObject) JSONValue.parseWithException(r.getResponse().trim());
			URL u = new URL((String) o.get("url"));

			// read content from example questionnaire XML file
			String qform = IOUtils.getStringFromFile(new File("./doc/xml/qu2.xml"));

			// upload questionnaire XML content
			ClientResponse result=c1.sendRequest("POST", u.getPath() + "/form",qform);
			assertEquals(200, result.getHttpCode());

			// then create survey using the previously created questionnaire and form
			ClientResponse csvres=c1.sendRequest("POST", "mobsos-surveys/surveys",generateSurveyJSON().toJSONString());
			JSONObject svu = (JSONObject) JSONValue.parseWithException(csvres.getResponse().trim());
			URL su = new URL((String) svu.get("url"));

			System.out.println("Survey URL:" + su);
			System.out.println("Questionnaire URL: " + u);

			int qid = Integer.parseInt(u.getPath().substring(u.getPath().lastIndexOf("/")+1));
			System.out.println("Questionnaire id: " + qid);

			// now set questionnaire to survey
			JSONObject qidset = new JSONObject();
			qidset.put("qid", qid);

			ClientResponse ares=c1.sendRequest("POST", su.getPath() + "/questionnaire",qidset.toJSONString());
			assertEquals(200, ares.getHttpCode());

			String npath = su.getPath() + "/questionnaire/" + group1.getId();
			System.out.println("GET " + npath);
			// now we are ready to download questionnaire form for given survey in a given community context.
			ClientResponse qsfres=c1.sendRequest("GET", su.getPath() + "/questionnaire/" + group1.getId(),"","",MediaType.TEXT_HTML,new Pair[]{});

			System.out.println("Status: " + qsfres.getHttpCode());
			System.out.println("Response: " + qsfres.getResponse());


		} catch (MalformedURLException e){
			e.printStackTrace();
			fail("Service returned malformed URL!");
		} catch (IOException e) {
			e.printStackTrace();
			fail("An unexpected exception occurred on loading test form data: "+e.getMessage());
		} catch (ParseException e) {
			e.printStackTrace();
			fail("Service returned invalid JSON! " + e.getMessage());
		} 
	}
	 */



	/**
	 * Generates a valid survey JSON representation.
	 */
	@SuppressWarnings({ "unchecked", "unused" })
	private static JSONObject generateSurveyJSON(){

		// generate some relative timepoints to now
		Calendar cal = Calendar.getInstance();
		Date today = cal.getTime();
		cal.add(Calendar.DAY_OF_YEAR,-1);
		Date yesterday = cal.getTime();
		cal.add(Calendar.DAY_OF_YEAR,2);
		Date tomorrow = cal.getTime();
		cal.add(Calendar.YEAR, 1);
		Date nextyear = cal.getTime();
		cal.add(Calendar.YEAR, -2);
		Date lastyear = cal.getTime();

		SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
		df.setTimeZone(TimeZone.getTimeZone("GMT"));

		String lastyearUTC = df.format(lastyear);
		String yesterdayUTC = df.format(yesterday);
		String nowUTC = df.format(today);
		String tomorrowUTC = df.format(tomorrow);
		String nextyearUTC = df.format(nextyear);

		JSONObject obj = new JSONObject(); 
		obj.put("name","Wikipedia Survey " + (new Date()).getTime());
		obj.put("organization", "Advanced Community Information Systems Group, RWTH Aachen University");
		obj.put("logo","http://dbis.rwth-aachen.de/cms/images/logo.jpg");
		obj.put("description","A sample survey on Wikipedia.");
		obj.put("resource", "http://wikipedia.org"); 
		obj.put("start",lastyearUTC);
		obj.put("end", nextyearUTC);
		obj.put("lang", "en-US");

		return obj;
	}

	/**
	 * Generates a valid survey JSON representation.
	 */
	@SuppressWarnings("unchecked")
	private static JSONObject generateNeedleSurveyJSON(){

		JSONObject obj = new JSONObject(); 
		obj.put("name","Nadel im Heuhaufen " + (new Date()).getTime());
		obj.put("organization", "Advanced Community Information Systems Group, RWTH Aachen University");
		obj.put("logo","http://dbis.rwth-aachen.de/cms/images/logo.jpg");
		obj.put("description","Eine schwer auffindbare Umfrage (Needle in the Haystack).");
		obj.put("resource", "http://wikipedia.org"); 
		obj.put("start","2014-06-06T00:00:00Z");
		obj.put("end", "2014-08-06T23:59:59Z");
		obj.put("lang", "de-DE");

		return obj;
	}

	@SuppressWarnings("unchecked")
	private static JSONObject generateSurveyJSON(String start, String end){

		JSONObject obj = new JSONObject(); 
		obj.put("name","Nadel im Heuhaufen " + start);
		obj.put("organization", "Advanced Community Information Systems Group, RWTH Aachen University");
		obj.put("logo","http://dbis.rwth-aachen.de/cms/images/logo.jpg");
		obj.put("description","Eine schwer auffindbare Umfrage (Needle in the Haystack).");
		obj.put("resource", "http://wikipedia.org"); 
		obj.put("start",start);
		obj.put("end", end);
		obj.put("lang", "de-DE");

		return obj;
	}

	/**
	 * Generates a valid questionnaire JSON representation.
	 */
	@SuppressWarnings("unchecked")
	private static JSONObject generateQuestionnaireJSON(){

		JSONObject obj = new JSONObject(); 
		obj.put("name","Quality Questionnaire " + (new Date()).getTime());
		obj.put("organization", "Advanced Community Information Systems Group, RWTH Aachen University");
		obj.put("logo","http://dbis.rwth-aachen.de/cms/images/logo.jpg");
		obj.put("description","A questionnaire designed to ask for quality");
		obj.put("lang", "en-US");

		return obj;
	}

	/**
	 * Generates a valid questionnaire JSON representation.
	 */
	@SuppressWarnings("unchecked")
	private static JSONObject generateNeedleQuestionnaireJSON(){

		JSONObject obj = new JSONObject(); 
		obj.put("name","Nadel im Heuhaufen " + (new Date()).getTime());
		obj.put("organization", "Sucher der heiligen Nadel");
		obj.put("logo","http://dbis.rwth-aachen.de/cms/images/logo.jpg");
		obj.put("description","Ein Fragebogen wie die Nadel im Heuhaufen (Needle in the Haystack)");
		obj.put("lang", "de-DE");

		return obj;
	}
}
