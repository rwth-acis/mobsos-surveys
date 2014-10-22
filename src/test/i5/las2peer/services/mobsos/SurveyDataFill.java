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

import static org.apache.commons.lang3.StringEscapeUtils.escapeXml10;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import i5.las2peer.p2p.LocalNode;
import i5.las2peer.restMapper.data.Pair;
import i5.las2peer.security.Agent;
import i5.las2peer.security.GroupAgent;
import i5.las2peer.security.ServiceAgent;
import i5.las2peer.security.UserAgent;
import i5.las2peer.testing.MockAgentFactory;
import i5.las2peer.webConnector.WebConnector;
import i5.las2peer.webConnector.client.ClientResponse;
import i5.las2peer.webConnector.client.MiniClient;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.json.simple.parser.ParseException;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.w3c.dom.Document;

import com.mysql.jdbc.EscapeTokenizer;


/**
 * JUnit Test Class for MobSOS Survey Service
 * 
 * @author Dominik Renzel
 *
 */
public class SurveyDataFill {

	private static final String HTTP_ADDRESS = "http://127.0.0.1";
	private static final int HTTP_PORT = WebConnector.DEFAULT_HTTP_PORT;

	private static LocalNode node;
	private static WebConnector connector;
	private static MiniClient c1;
	private static ByteArrayOutputStream logStream;
	private static UserAgent user;

	private static final String testServiceClass = "i5.las2peer.services.mobsos.SurveyService";


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

		user = MockAgentFactory.getAdam();

		Agent[] as;

		node.storeAgent(user);

		node.launch();

		ServiceAgent testService = ServiceAgent.generateNewAgent(testServiceClass, "a pass");
		testService.unlockPrivateKey("a pass");

		node.registerReceiver(testService);

		//start connector
		logStream = new ByteArrayOutputStream ();

		//connector = new WebConnector(true,HTTP_PORT,false,1000,"./etc/xmlc");
		connector = new WebConnector(true,HTTP_PORT,false,1000);

		connector.setSocketTimeout(10000);
		connector.setLogStream(new PrintStream (logStream));


		connector.start ( node );
		Thread.sleep(1000); //wait a second for the connector to become ready

		connector.updateServiceList();

		c1 = new MiniClient();
		c1.setAddressPort(HTTP_ADDRESS, HTTP_PORT);
		c1.setLogin(Long.toString(user.getId()), "adamspass");

		//String xml=RESTMapper.mergeXMLs(new String[]{RESTMapper.getMethodsAsXML(SurveyService.class)});
		//System.out.println(xml);

		//avoid timing errors: wait for the repository manager to get all services before continuing

		try
		{
			System.out.println("waiting..");
			Thread.sleep(5000);
		}
		catch(InterruptedException e)
		{
			e.printStackTrace();
		}

		// first delete all surveys & questionnaires
		c1.sendRequest("DELETE", "mobsos/surveys","");
		c1.sendRequest("DELETE", "mobsos/questionnaires","");
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

	@Test
	public void createQuestionnaires(){

		// SUS Questionnaire (engl.)
		try{

			JSONObject q = new JSONObject(); 
			q.put("name","System Usability Scale (SUS)");
			q.put("description","A simple questionnaire for measuring usability and learnability. The SUS questionnaire is based on: Brooke, J. (1996) \"SUS: A quick and dirty usability scale\". In: Usability Evaluation in Industry. The two different factors of usability and learnability stem from a study by Lewis, J. R. & Sauro, J. (2009) \"The Factor Structure of the System Usability Scale\".");
			q.put("lang", "en-US");
			q.put("organization", "Advanced Community Information Systems (ACIS) Group, RWTH Aachen University, Germany");
			q.put("logo","http://dbis.rwth-aachen.de/cms/research/ACIS/ACIS%20Logo%20Transparent.png");

			String qfuri = "./doc/xml/questionnaires/sus-questionnaire-en.xml";

			URL qu = createQuestionnaire(q, qfuri);

			System.out.println("Created questionnaire " + qfuri + ": " + qu);
		} catch (Exception e){
			e.printStackTrace();
		}

		// SUMI Questionnaire (engl.)
		try{

			JSONObject q = new JSONObject(); 
			q.put("name","System Usability Measurement Inventory (SUMI)");
			q.put("description","Software Usability Measurement Inventory. An extensive questionnaire on software usability. The SUMI questionnaire is based on: Kirakowski, J. (1996) \"The software usability measurement inventory: background and usage\". In: Usability Evaluation in Industry. Its use is proposed in ISO 9241, a multi-part standard from the International Organization for Standardization (ISO) covering ergonomics of human-computer interaction.");
			q.put("lang", "en-US");
			q.put("organization", "Advanced Community Information Systems (ACIS) Group, RWTH Aachen University, Germany");
			q.put("logo","http://dbis.rwth-aachen.de/cms/research/ACIS/ACIS%20Logo%20Transparent.png");

			String qfuri = "./doc/xml/questionnaires/sumi-questionnaire-en.xml";

			URL qu = createQuestionnaire(q, qfuri);

			System.out.println("Created questionnaire " + qfuri + ": " + qu);
		} catch (Exception e){
			e.printStackTrace();
		}

		// USE Questionnaire (engl.)
		try{

			JSONObject q = new JSONObject(); 
			q.put("name","Usefulness, Satisfaction, and Ease-of-Use (USE)");
			q.put("description","A questionnaire for measuring usability in terms of Usefulness, Satisfaction, and Ease of use/learning. The USE questionnaire is based on: Lund, A.M. (2001) \"Measuring Usability with the USE Questionnaire\". STC Usability SIG Newsletter, 8:2.");
			q.put("lang", "en-US");
			q.put("organization", "Advanced Community Information Systems (ACIS) Group, RWTH Aachen University, Germany");
			q.put("logo","http://dbis.rwth-aachen.de/cms/research/ACIS/ACIS%20Logo%20Transparent.png");

			String qfuri = "./doc/xml/questionnaires/use-questionnaire.xml";

			URL qu = createQuestionnaire(q, qfuri);

			System.out.println("Created questionnaire " + qfuri + ": " + qu);
		} catch (Exception e){
			e.printStackTrace();
		}

		// UEQ Questionnaire (engl.)
		try{

			JSONObject q = new JSONObject(); 
			q.put("name","User Experience Questionnaire (UEQ)");
			q.put("description","A simple questionnaire for measuring user experience. The UEQ Questionnaire is based on: Laugwitz, Held, and Schrepp (2008) \"Construction and Evaluation of a User Experience Questionnaire\". In: USAB 2008, LNCS 5298, pp. 63-76. The scale items load on the six factors Attractiveness, Perspicuity, Efficiency, Dependability, Stimulation, and Novelty.");
			q.put("lang", "en-US");
			q.put("organization", "Advanced Community Information Systems (ACIS) Group, RWTH Aachen University, Germany");
			q.put("logo","http://dbis.rwth-aachen.de/cms/research/ACIS/ACIS%20Logo%20Transparent.png");

			String qfuri = "./doc/xml/questionnaires/ueq-questionnaire-en.xml";

			URL qu = createQuestionnaire(q, qfuri);

			System.out.println("Created questionnaire " + qfuri + ": " + qu);
		} catch (Exception e){
			e.printStackTrace();
		}

		// TAM Questionnaire (engl.)
		try{

			JSONObject q = new JSONObject(); 
			q.put("name","Technology Acceptance Model (TAM)");
			q.put("description","A questionnaire for measuring usability in terms of perceived usefulness and ease-of-Use. The TAM questionnaire is based on: Davis, Fred D. (1989) \"Perceived Usefulness, Perceived Ease of Use, and User Acceptance of Information Technology\". MIS Quarterly 13, pp. 319-340.");
			q.put("lang", "en-US");
			q.put("organization", "Advanced Community Information Systems (ACIS) Group, RWTH Aachen University, Germany");
			q.put("logo","http://dbis.rwth-aachen.de/cms/research/ACIS/ACIS%20Logo%20Transparent.png");

			String qfuri = "./doc/xml/questionnaires/tam-questionnaire.xml";

			URL qu = createQuestionnaire(q, qfuri);

			System.out.println("Created questionnaire " + qfuri + ": " + qu);
		} catch (Exception e){
			e.printStackTrace();
		}

		// IS-Impact Questionnaire (engl.)
		try{

			JSONObject q = new JSONObject(); 
			q.put("name","IS-Impact Questionnaire");
			q.put("description","A questionnaire for measuring impact and quality of an Information System in the dimensions Individual Impact, Organizational Impact, Information Quality, and System Quality. The IS-Impact Questionnaire is based on: Gable, Guy G. and Sedera, Darshana and Chan, Taizan (2008) \"Re-conceptualizing information system success: the IS-Impact Measurement Model\". Journal of the Association for Information Systems, 9(7). pp. 377-408.");
			q.put("lang", "en-US");
			q.put("organization", "Advanced Community Information Systems (ACIS) Group, RWTH Aachen University, Germany");
			q.put("logo","http://dbis.rwth-aachen.de/cms/research/ACIS/ACIS%20Logo%20Transparent.png");

			String qfuri = "./doc/xml/questionnaires/is-impact-questionnaire.xml";

			URL qu = createQuestionnaire(q, qfuri);

			System.out.println("Created questionnaire " + qfuri + ": " + qu);
		} catch (Exception e){
			e.printStackTrace();
		}

	}

	public URL createQuestionnaire(JSONObject q, String qfuri) throws IOException{

		// first add a new questionnaire
		ClientResponse r = c1.sendRequest("POST", "mobsos/questionnaires",q.toJSONString(),"application/json","*/*", new Pair[]{});
		JSONObject o;

		o = (JSONObject) JSONValue.parse(r.getResponse().trim());

		System.out.println(o.toJSONString());
		URL u;

		u = new URL((String) o.get("url"));

		// read content from questionnaire XML file
		String qform = IOUtils.getStringFromFile(qfuri).trim();

		try {
			Document doc = IOUtils.loadXMLFromString(qfuri);
			TransformerFactory tf = TransformerFactory.newInstance();
			Transformer transformer = tf.newTransformer();
			StringWriter writer = new StringWriter();
			transformer.transform(new DOMSource(doc), new StreamResult(writer));
			qform = writer.getBuffer().toString().replaceAll("\n|\r", "");

		} catch (Exception e) {
			System.out.println("Erreur!!!!!!!!!!!!!!!!!!!");
			e.printStackTrace();
		}

		// upload questionnaire form XML
		ClientResponse result=c1.sendRequest("PUT", u.getPath() + "/form",qform,"text/xml","*/*", new Pair[]{});


		return u;

	}

}
