/*
Copyright (c) 2014 Dominik Renzel, Advanced Community Information Systems (ACIS) Group, 
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

import i5.las2peer.api.Service;
import i5.las2peer.p2p.AgentNotKnownException;
import i5.las2peer.restMapper.HttpResponse;
import i5.las2peer.restMapper.MediaType;
import i5.las2peer.restMapper.RESTMapper;
import i5.las2peer.restMapper.annotations.Consumes;
import i5.las2peer.restMapper.annotations.ContentParam;
import i5.las2peer.restMapper.annotations.DELETE;
import i5.las2peer.restMapper.annotations.GET;
import i5.las2peer.restMapper.annotations.POST;
import i5.las2peer.restMapper.annotations.PUT;
import i5.las2peer.restMapper.annotations.Path;
import i5.las2peer.restMapper.annotations.PathParam;
import i5.las2peer.restMapper.annotations.Produces;
import i5.las2peer.restMapper.annotations.QueryParam;
import i5.las2peer.restMapper.annotations.Version;
import i5.las2peer.security.Agent;
import i5.las2peer.security.GroupAgent;
import i5.las2peer.security.UserAgent;
import i5.las2peer.tools.GroupAgentGenerator;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Serializable;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.TimeZone;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.XMLConstants;
import javax.xml.bind.DatatypeConverter;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.dom.DOMSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.json.simple.parser.ParseException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSSerializer;
import org.xml.sax.SAXException;

/**
 * 
 * MobSOS Survey Service
 * 
 * TODO: add class documentation
 * 
 * @author Dominik Renzel
 *
 */
@Path("mobsos")
@Version("0.1")
public class SurveyService extends Service {

	public final static String MOBSOS_QUESTIONNAIRE_NS = "http://dbis.rwth-aachen.de/mobsos/questionnaire.xsd";

	private Connection connection;
	private PreparedStatement surveyInsertStatement, surveysQueryStatement, surveysDeleteStatement;
	private PreparedStatement surveyQueryStatement, surveyCheckOwnerStatement, surveyDeleteStatement, surveyUpdateStatement, surveySetQuestionnaireStatement;

	private PreparedStatement questionnaireInsertStatement, questionnairesQueryStatement, questionnairesDeleteStatement;
	private PreparedStatement questionnaireQueryStatement, questionnaireCheckOwnerStatement, questionnaireDeleteStatement, questionnaireUpdateStatement;
	private PreparedStatement questionnaireUploadFormStatement, questionnaireDownloadFormStatement;

	private PreparedStatement submitQuestionAnswerStatement, submitQuestionAnswerStatementNoC;

	private DocumentBuilder parser;
	private Validator validator;

	private String epUrl, questionnaireSchemaPath;
	private String jdbcDriverClassName;
	private String jdbcUrl, jdbcSchema;
	private String jdbcLogin, jdbcPass;

	private PreparedStatement surveyGetQuestionnaireIdStatement;

	private PreparedStatement surveysFullQueryStatement;
	private PreparedStatement questionnairesFullQueryStatement;

	public SurveyService(){
		// set values from configuration file
		this.setFieldValues();

		this.monitor = true;

		try {
			initDatabaseConnection();
			initXMLInfrastructure();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		} catch (SQLException e) {
			e.printStackTrace();
		} catch (SAXException e) {
			e.printStackTrace();
		} catch (ParserConfigurationException e) {
			e.printStackTrace();
		}

		// print out REST mapping for this service
		//System.out.println(getRESTMapping());
	}

	// authentication done by Web Connector
	// only emulates an auth endpoint.
	@GET
	@Path("auth")
	public HttpResponse authenticate(){
		HttpResponse result = new HttpResponse("");
		result.setStatus(200);
		return result;
	}

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	@Path("agentinfo")
	public HttpResponse getMyAgentInformation(){
		return getAgentInformation(this.getActiveAgent().getId());
	}

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	@Path("agentinfo/{id}")
	public HttpResponse getAgentInformation(@PathParam("id") long id){
		try{
			try {
				Agent a = this.getActiveNode().getAgent(id);


				JSONObject uo = new JSONObject();
				uo.put("id",id);

				if(a instanceof UserAgent){
					uo.put("type", "user");

					UserAgent ua = (UserAgent) a;
					uo.put("login", ua.getLoginName());
					uo.put("mail", ua.getEmail());

				} else if (a instanceof GroupAgent) {
					uo.put("type", "group");
					GroupAgent ga = (GroupAgent) a;
					uo.put("size", ga.getSize());
					uo.put("member", ga.isMember(this.getActiveAgent()));
				}

				HttpResponse result = new HttpResponse(uo.toJSONString());
				result.setStatus(200);
				return result;
			} catch (AgentNotKnownException e) {
				HttpResponse result = new HttpResponse("Agent "+ id + " not found.");
				result.setStatus(404);
				return result;
			}

		} catch (Exception e){
			e.printStackTrace();
			HttpResponse result = new HttpResponse("");
			result.setStatus(500);
			return result;
		}
	}

	/**
	 * Retrieves a list of all surveys.
	 * @return
	 */
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	@Path("surveys")
	public HttpResponse getSurveys(@QueryParam(defaultValue = "0", name = "f") int full)
	{

		try {
			JSONObject r = new JSONObject();
			JSONArray a = new JSONArray();

			if(full <= 0){
				ResultSet rs = surveysQueryStatement.executeQuery();

				while(rs.next()){
					String id = rs.getString("id");
					a.add(epUrl + "mobsos/surveys/" + id);
				}

				r.put("surveys", a);

				HttpResponse result = new HttpResponse(r.toJSONString());
				result.setStatus(200);
				return result;
			} else {
				ResultSet rs = surveysFullQueryStatement.executeQuery();

				if (!rs.isBeforeFirst()){
					r.put("surveys", a);
					HttpResponse result = new HttpResponse(r.toJSONString());
					result.setStatus(200);
					return result;
				}

				while(rs.next()){
					JSONObject survey = readSurveyFromResultSet(rs);
					survey.put("url", epUrl + "mobsos/surveys/" + survey.get("id"));
					a.add(survey);
				}

				r.put("surveys", a);

				HttpResponse result = new HttpResponse(r.toJSONString());
				result.setStatus(200);
				return result;
			}

		} catch (Exception e) {
			e.printStackTrace();
			HttpResponse result = new HttpResponse("");
			result.setStatus(500);
			return result;
		}
	}

	/**
	 * Creates a new survey
	 * 
	 * @param content
	 * 
	 */
	@POST
	@Path("surveys")
	public HttpResponse createSurvey(@ContentParam String content)
	{
		try {
			JSONObject o;

			try{
				o = parseSurvey(content);
			} catch (IllegalArgumentException | ParseException e){
				e.printStackTrace();
				HttpResponse result = new HttpResponse(e.getMessage());
				result.setStatus(400);
				return result;
			}

			int sid = storeNewSurvey(o);

			JSONObject r = new JSONObject();
			r.put("url",epUrl + "mobsos/surveys/" + sid);
			HttpResponse result = new HttpResponse(r.toJSONString());
			result.setStatus(201);

			return result;

		} catch (Exception e) {
			e.printStackTrace();
			HttpResponse result = new HttpResponse("");
			result.setStatus(500);
			return result;
		}
	}

	/**
	 * Deletes all surveys at once without any check for ownership. 
	 * This method should be only be used for development and with absolute caution!
	 */
	@DELETE
	@Path("surveys")
	public HttpResponse deleteSurveys(){
		try {
			surveysDeleteStatement.executeUpdate();
			connection.commit();

			HttpResponse result = new HttpResponse("");
			result.setStatus(200);
			return result;
		} catch (Exception e) {
			e.printStackTrace();
			HttpResponse result = new HttpResponse("");
			result.setStatus(500);
			return result;
		}
	}

	/**
	 * Retrieves information for a given survey
	 * 
	 * @param id
	 * @return
	 */
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	@Path("surveys/{id}")
	public HttpResponse getSurvey(@PathParam("id") int id){

		try {
			surveyQueryStatement.clearParameters();
			surveyQueryStatement.setInt(1, id);

			ResultSet rs = surveyQueryStatement.executeQuery();

			if (!rs.isBeforeFirst()){
				HttpResponse result = new HttpResponse("Survey " + id + " does not exist!");
				result.setStatus(404);
				return result;
			}

			rs.next();
			JSONObject r = readSurveyFromResultSet(rs);
			HttpResponse result = new HttpResponse(r.toJSONString());
			result.setStatus(200);
			return result;
		} catch (Exception e) {
			e.printStackTrace();
			HttpResponse result = new HttpResponse("Internal Error: " + e.getMessage());
			result.setStatus(500);
			return result;
		}
	}

	/**
	 * Updates a survey with a given id. The respective survey may only be deleted, if the active agent is the survey's owner.
	 * 
	 * @param id
	 * @return
	 */
	@POST //TODO: replace by PUT as soon as new Web Connector version is available via ivy.
	@Path("surveys/{id}")
	public HttpResponse updateSurvey(@PathParam("id") int id, @ContentParam String content){

		try {
			int exown;
			exown = checkExistenceOwnership(id,0);

			// check if survey exists; if not, return 404.
			if(exown == -1){
				HttpResponse result = new HttpResponse("Survey " + id + " does not exist.");
				result.setStatus(404);
				return result;
			} 
			// if survey exists, check if active agent is owner. if not, return 401.
			else if (exown == 0){
				HttpResponse result = new HttpResponse("Survey " + id + " may only be deleted by its owner.");
				result.setStatus(401);
				return result;
			}

			// if survey exists and active agent is owner, proceed.

			JSONObject o;
			// parse and validate content. If invalid, return 400 (bad request)
			try{
				o = parseSurvey(content);
			} catch (IllegalArgumentException | ParseException e){
				HttpResponse result = new HttpResponse(e.getMessage());
				result.setStatus(400);
				return result;
			}

			surveyUpdateStatement.clearParameters();
			surveyUpdateStatement.setString(1, (String) o.get("organization"));
			surveyUpdateStatement.setString(2, (String) o.get("logo"));
			surveyUpdateStatement.setString(3, (String) o.get("name"));
			surveyUpdateStatement.setString(4, (String) o.get("description"));
			surveyUpdateStatement.setString(5, (String) o.get("resource"));
			surveyUpdateStatement.setTimestamp(6, new Timestamp(DatatypeConverter.parseDateTime((String)o.get("start")).getTimeInMillis()));
			surveyUpdateStatement.setTimestamp(7, new Timestamp(DatatypeConverter.parseDateTime((String)o.get("end")).getTimeInMillis()));
			surveyUpdateStatement.setInt(8, id);

			surveyUpdateStatement.executeUpdate();
			connection.commit();

			HttpResponse result = new HttpResponse("Survey " + id + " updated.");
			result.setStatus(200);
			return result;

		} catch (Exception e) {
			e.printStackTrace();
			HttpResponse result = new HttpResponse("Internal Error: " + e.getMessage());
			result.setStatus(500);
			return result;
		}
	}

	@GET
	@Produces(MediaType.TEXT_HTML)
	@Path("surveys/{id}/questionnaire")
	public HttpResponse getSurveyQuestionnaireFormHTML(@PathParam("id") int id){
		try{

			// retrieve survey data; if survey does not exist, return 404.
			HttpResponse r = getSurvey(id);
			if(200 != r.getStatus()){
				System.err.println(r.getResult());
				return r;
			}

			JSONObject survey = (JSONObject) JSONValue.parse(r.getResult());

			// check if survey has the questionnaire id field qid set. If not, return 404 with a respective message.
			if(null == survey.get("qid")){
				HttpResponse result = new HttpResponse("Questionnaire not set for survey " + id + ".");
				result.setStatus(404);
				return result;
			}

			long qid = (Long) survey.get("qid");

			// now download questionnaire form from database
			questionnaireDownloadFormStatement.clearParameters();
			questionnaireDownloadFormStatement.setLong(1, qid);

			ResultSet rs = questionnaireDownloadFormStatement.executeQuery();

			if (!rs.isBeforeFirst()){
				HttpResponse result = new HttpResponse("Form for questionnaire " + qid + " does not exist!");
				result.setStatus(404);
				return result;
			}

			rs.next();

			String formXml = rs.getString(1);

			String adaptedFormXml = adaptForm(formXml,survey, (UserAgent) this.getActiveAgent(),null);

			Document form;
			// before returning form, make sure it's still valid (may be obsolete step...)
			try{
				form = validateQuestionnaireData(adaptedFormXml);
			} catch(IOException e){
				e.printStackTrace();
				HttpResponse result = new HttpResponse("Internal error: " + e.getMessage());
				result.setStatus(500);
				return result;
			} catch (SAXException e){
				e.printStackTrace();
				HttpResponse result = new HttpResponse("Questionnaire form is invalid! Cause: " + e.getMessage());
				result.setStatus(400);
				return result;
			}

			String text = new Scanner(new File("./doc/xml/questionnaire-template.html")).useDelimiter("\\A").next();

			String adaptText = adaptForm(text, survey, (UserAgent) this.getActiveAgent(), null);

			Vector<String> qpages = new Vector<String>();
			Vector<String> navpills = new Vector<String>();

			NodeList nodeList = form.getElementsByTagNameNS(MOBSOS_QUESTIONNAIRE_NS, "Page");

			// then iterate over all question pages
			for (int i = 0; i < nodeList.getLength(); i++) {

				Node node = nodeList.item(i);
				if (node.getNodeType() == Node.ELEMENT_NODE) {
					Element e = (Element) node;
					if(e.getAttribute("xsi:type").endsWith("QuestionPageType")){

						// first add nav pill item
						String navpill = "\t\t\t\t\t<li><a href=\"#step-" + i +"\"><h4 class=\"list-group-item-heading\">" + i + "</h4></a></li>\n";
						navpills.add(navpill);

						// then add question page
						String qpage = "\t\t<div class=\"row setup-content\" id=\"step-" + i + "\"><div class=\"col-xs-12\"><div class=\"col-md-12 well text-center\">\n";

						String name = e.getAttribute("name");
						qpage += "\t\t\t<h2>" + name + "</h2>\n";

						String instr = e.getElementsByTagNameNS(MOBSOS_QUESTIONNAIRE_NS,"Instructions").item(0).getTextContent().trim();

						String cssClass = "question";

						if(e.getAttribute("required") != null && e.getAttribute("required").equals("true")){
							cssClass += " required";
							instr += " <i>(required)</i>";
						}

						qpage +="\t\t\t<div class=\"" + cssClass + "\" >" + instr + "</div>\n";

						String qtype = e.getAttribute("xsi:type");

						String quid = e.getAttribute("qid");

						if("qu:OrdinalScaleQuestionPageType".equals(qtype)){

							// TODO: do something with default value, if set.
							int defval = Integer.parseInt(e.getAttribute("defval"));
							String minlabel = e.getAttribute("minlabel");
							String maxlabel = e.getAttribute("maxlabel");
							int minval =  Integer.parseInt(e.getAttribute("minval"));
							int maxval = Integer.parseInt(e.getAttribute("maxval"));

							qpage += "\t\t\t<div class=\"btn-group\" data-toggle=\"buttons\">\n";
							qpage += "\t\t\t\t<span class=\"btn\">" + minlabel + "</span>\n";
							for(int k=minval;k<=maxval;k++){
								qpage += "\t\t\t\t<label class=\"btn btn-primary\">\n";
								qpage += "\t\t\t\t\t<input name=\""+ quid + "\" type=\"radio\" value=\""+k + "\">" + k + "\n";
								qpage += "\t\t\t\t</label>\n";
							}
							qpage += "\t\t\t\t<span class=\"btn\">" + maxlabel + "</span>\n";
							qpage += "\t\t\t</div>\n";

						} else if ("qu:DichotomousQuestionPageType".equals(qtype)){

							// TODO: do something with default value, if set.
							int defval = Integer.parseInt(e.getAttribute("defval"));
							String minlabel = e.getAttribute("minlabel");
							String maxlabel = e.getAttribute("maxlabel");

							qpage += "\t\t\t<div class=\"btn-group\" data-toggle=\"buttons\">\n";
							qpage += "\t\t\t\t<label class=\"btn btn-primary\">\n";
							qpage += "\t\t\t\t\t<input name=\"" + quid + "\" type=\"radio\" value=\"0\">" + minlabel + "\n";
							qpage += "\t\t\t\t</label>\n";
							qpage += "\t\t\t\t<label class=\"btn btn-primary\">\n";
							qpage += "\t\t\t\t\t<input name=\"" + quid + "\" type=\"radio\" value=\"1\">" + maxlabel + "\n";
							qpage += "\t\t\t\t</label>\n";
							qpage += "\t\t\t</div>\n";

						} else if ("qu:FreeTextQuestionPageType".equals(qtype)){
							qpage += "\t\t\t<textarea name=\"" +quid + "\" class=\"freetext\" rows=\"3\"></textarea>\n";
						}

						qpage += "\t\t</div></div></div>\n";
						qpages.add(qpage);
					}
				}
			}

			// now that all questions are extracted and transformed to HTML, append nav pill items and question pages to final HTML.

			// first serialize nav pill items
			String navpillItems = "";
			for(int j=0;j<navpills.size();j++){
				navpillItems += navpills.get(j);
			}

			// then serialize question page divs
			String questionDivs = "";
			for(int j=0;j<qpages.size();j++){
				questionDivs += qpages.get(j);
			}

			// then generate answer link
			URL answerUrl = new URL(epUrl+"surveys/"+id+"/answers"); 

			String answerLink = "<a href=\""+ answerUrl + "\" id=\"return-url\" class=\"hidden\" ></a>";

			// finally insert all generated parts into the resulting adapted HTML
			adaptText = adaptText.replaceAll("<!-- NAVPILLS -->", navpillItems);
			adaptText = adaptText.replaceAll("<!-- QUESTIONPAGES -->",questionDivs);
			adaptText = adaptText.replaceAll("<!-- ANSWERLINK -->", answerLink);

			// return adapted HTML
			HttpResponse result = new HttpResponse(adaptText);
			result.setStatus(200);
			return result;

		} catch (Exception e){
			e.printStackTrace();
			HttpResponse result = new HttpResponse("Internal error: " + e.getMessage());
			result.setStatus(500);
			return result;
		}

	}


	@GET
	@Produces(MediaType.TEXT_HTML)
	@Path("surveys/{id}/questionnaire/{cid}")
	public HttpResponse getSurveyQuestionnaireFormForCommunityHTML(@PathParam("id") int id, @PathParam("cid") long cid){
		try{

			GroupAgent community;
			// if community not found, return 404
			try{

				Agent a = this.getActiveNode().getAgent(cid);
				if(!(a instanceof GroupAgent)){
					HttpResponse result = new HttpResponse("Agent " + cid + " does not represent a community!");
					result.setStatus(400);
					return result;
				}
				community = (GroupAgent) a;
			} catch (AgentNotKnownException e){
				e.printStackTrace();
				HttpResponse result = new HttpResponse("Community " + cid + " does not exist!");
				result.setStatus(404);
				return result;
			}

			// retrieve survey data; if survey does not exist, return 404.
			HttpResponse r = getSurvey(id);
			if(200 != r.getStatus()){
				System.err.println(r.getResult());
				return r;
			}

			JSONObject survey = (JSONObject) JSONValue.parse(r.getResult());

			// check if survey has the questionnaire id field qid set. If not, return 404 with a respective message.
			if(null == survey.get("qid")){
				HttpResponse result = new HttpResponse("Questionnaire not set for survey " + id + ".");
				result.setStatus(404);
				return result;
			}

			long qid = (Long) survey.get("qid");

			// now download questionnaire form from database
			questionnaireDownloadFormStatement.clearParameters();
			questionnaireDownloadFormStatement.setLong(1, qid);

			ResultSet rs = questionnaireDownloadFormStatement.executeQuery();

			if (!rs.isBeforeFirst()){
				HttpResponse result = new HttpResponse("Form for questionnaire " + qid + " does not exist!");
				result.setStatus(404);
				return result;
			}

			rs.next();

			String formXml = rs.getString(1);

			String adaptedFormXml = adaptForm(formXml,survey, (UserAgent) this.getActiveAgent(),community);

			Document form;
			// before returning form, make sure it's still valid (may be obsolete step...)
			try{
				form = validateQuestionnaireData(adaptedFormXml);
			} catch(IOException e){
				e.printStackTrace();
				HttpResponse result = new HttpResponse("Internal error: " + e.getMessage());
				result.setStatus(500);
				return result;
			} catch (SAXException e){
				e.printStackTrace();
				HttpResponse result = new HttpResponse("Questionnaire form is invalid! Cause: " + e.getMessage());
				result.setStatus(400);
				return result;
			}

			String text = new Scanner(new File("./doc/xml/questionnaire-template.html")).useDelimiter("\\A").next();

			String adaptText = adaptForm(text, survey, (UserAgent) this.getActiveAgent(), community);

			Vector<String> qpages = new Vector<String>();
			Vector<String> navpills = new Vector<String>();

			NodeList nodeList = form.getElementsByTagNameNS(MOBSOS_QUESTIONNAIRE_NS, "Page");

			// then iterate over all question pages
			for (int i = 0; i < nodeList.getLength(); i++) {

				Node node = nodeList.item(i);
				if (node.getNodeType() == Node.ELEMENT_NODE) {
					Element e = (Element) node;
					if(e.getAttribute("xsi:type").endsWith("QuestionPageType")){

						// first add nav pill item
						String navpill = "\t\t\t\t\t<li><a href=\"#step-" + i +"\"><h4 class=\"list-group-item-heading\">" + i + "</h4></a></li>\n";
						navpills.add(navpill);

						// then add question page
						String qpage = "\t\t<div class=\"row setup-content\" id=\"step-" + i + "\"><div class=\"col-xs-12\"><div class=\"col-md-12 well text-center\">\n";

						String name = e.getAttribute("name");
						qpage += "\t\t\t<h2>" + name + "</h2>\n";

						String instr = e.getElementsByTagNameNS(MOBSOS_QUESTIONNAIRE_NS,"Instructions").item(0).getTextContent().trim();

						String cssClass = "question";

						if(e.getAttribute("required") != null && e.getAttribute("required").equals("true")){
							cssClass += " required";
							instr += " <i>(required)</i>";
						}

						qpage +="\t\t\t<div class=\"" + cssClass + "\" >" + instr + "</div>\n";

						String qtype = e.getAttribute("xsi:type");

						String quid = e.getAttribute("qid");

						if("qu:OrdinalScaleQuestionPageType".equals(qtype)){

							// TODO: do something with default value, if set.
							int defval = Integer.parseInt(e.getAttribute("defval"));
							String minlabel = e.getAttribute("minlabel");
							String maxlabel = e.getAttribute("maxlabel");
							int minval =  Integer.parseInt(e.getAttribute("minval"));
							int maxval = Integer.parseInt(e.getAttribute("maxval"));

							qpage += "\t\t\t<div class=\"btn-group\" data-toggle=\"buttons\">\n";
							qpage += "\t\t\t\t<span class=\"btn\">" + minlabel + "</span>\n";
							for(int k=minval;k<=maxval;k++){
								qpage += "\t\t\t\t<label class=\"btn btn-primary\">\n";
								qpage += "\t\t\t\t\t<input name=\""+ quid + "\" type=\"radio\" value=\""+k + "\">" + k + "\n";
								qpage += "\t\t\t\t</label>\n";
							}
							qpage += "\t\t\t\t<span class=\"btn\">" + maxlabel + "</span>\n";
							qpage += "\t\t\t</div>\n";

						} else if ("qu:DichotomousQuestionPageType".equals(qtype)){

							// TODO: do something with default value, if set.
							int defval = Integer.parseInt(e.getAttribute("defval"));
							String minlabel = e.getAttribute("minlabel");
							String maxlabel = e.getAttribute("maxlabel");

							qpage += "\t\t\t<div class=\"btn-group\" data-toggle=\"buttons\">\n";
							qpage += "\t\t\t\t<label class=\"btn btn-primary\">\n";
							qpage += "\t\t\t\t\t<input name=\"" + quid + "\" type=\"radio\" value=\"0\">" + minlabel + "\n";
							qpage += "\t\t\t\t</label>\n";
							qpage += "\t\t\t\t<label class=\"btn btn-primary\">\n";
							qpage += "\t\t\t\t\t<input name=\"" + quid + "\" type=\"radio\" value=\"1\">" + maxlabel + "\n";
							qpage += "\t\t\t\t</label>\n";
							qpage += "\t\t\t</div>\n";

						} else if ("qu:FreeTextQuestionPageType".equals(qtype)){
							qpage += "\t\t\t<textarea name=\"" +quid + "\" class=\"freetext\" rows=\"3\"></textarea>\n";
						}

						qpage += "\t\t</div></div></div>\n";
						qpages.add(qpage);
					}
				}
			}

			// now that all questions are extracted and transformed to HTML, append nav pill items and question pages to final HTML.

			// first serialize nav pill items
			String navpillItems = "";
			for(int j=0;j<navpills.size();j++){
				navpillItems += navpills.get(j);
			}

			// then serialize question page divs
			String questionDivs = "";
			for(int j=0;j<qpages.size();j++){
				questionDivs += qpages.get(j);
			}

			// then generate answer link
			URL answerUrl = new URL(epUrl+"surveys/"+id+"/answers/"+cid); 

			String answerLink = "<a href=\""+ answerUrl + "\" id=\"return-url\" class=\"hidden\" ></a>";

			// finally insert all generated parts into the resulting adapted HTML
			adaptText = adaptText.replaceAll("<!-- NAVPILLS -->", navpillItems);
			adaptText = adaptText.replaceAll("<!-- QUESTIONPAGES -->",questionDivs);
			adaptText = adaptText.replaceAll("<!-- ANSWERLINK -->", answerLink);

			// return adapted HTML
			HttpResponse result = new HttpResponse(adaptText);
			result.setStatus(200);
			return result;

		} catch (Exception e){
			e.printStackTrace();
			HttpResponse result = new HttpResponse("Internal error: " + e.getMessage());
			result.setStatus(500);
			return result;
		}

	}

	@GET
	@Produces(MediaType.TEXT_XML)
	@Path("surveys/{id}/questionnaire/{cid}")
	public HttpResponse getSurveyQuestionnaireFormForCommunityXML(@PathParam("id") int id, @PathParam("cid") long cid){
		try{

			GroupAgent community;
			// if community not found, return 404
			try{

				Agent a = this.getActiveNode().getAgent(cid);
				if(!(a instanceof GroupAgent)){
					HttpResponse result = new HttpResponse("Agent " + cid + " does not represent a community!");
					result.setStatus(400);
					return result;
				}
				community = (GroupAgent) a;
			} catch (AgentNotKnownException e){
				e.printStackTrace();
				HttpResponse result = new HttpResponse("Community " + cid + " does not exist!");
				result.setStatus(404);
				return result;
			}

			// retrieve survey data; if survey does not exist, return 404.
			HttpResponse r = getSurvey(id);
			if(200 != r.getStatus()){
				System.err.println(r.getResult());
				return r;
			}

			JSONObject survey = (JSONObject) JSONValue.parse(r.getResult());

			// check if survey has the questionnaire id field qid set. If not, return 404 with a respective message.
			if(null == survey.get("qid")){
				HttpResponse result = new HttpResponse("Questionnaire not set for survey " + id + ".");
				result.setStatus(404);
				return result;
			}

			long qid = (Long) survey.get("qid");

			// now download questionnaire form from database
			questionnaireDownloadFormStatement.clearParameters();
			questionnaireDownloadFormStatement.setLong(1, qid);

			ResultSet rs = questionnaireDownloadFormStatement.executeQuery();

			if (!rs.isBeforeFirst()){
				HttpResponse result = new HttpResponse("Form for questionnaire " + qid + " does not exist!");
				result.setStatus(404);
				return result;
			}

			rs.next();

			String formXml = rs.getString(1);

			String adaptedFormXml = adaptForm(formXml,survey, (UserAgent) this.getActiveAgent(),community);

			// before returning form, make sure it's still valid (may be obsolete step...)
			try{
				validateQuestionnaireData(adaptedFormXml);
				HttpResponse result = new HttpResponse(adaptedFormXml);
				result.setStatus(200);
				return result;
			} catch(IOException e){
				e.printStackTrace();
				HttpResponse result = new HttpResponse("Internal error: " + e.getMessage());
				result.setStatus(500);
				return result;
			} catch (SAXException e){
				e.printStackTrace();
				HttpResponse result = new HttpResponse("Questionnaire form is invalid! Cause: " + e.getMessage());
				result.setStatus(400);
				return result;
			}
		} catch (Exception e){
			e.printStackTrace();
			HttpResponse result = new HttpResponse("Internal error: " + e.getMessage());
			result.setStatus(500);
			return result;
		}
	}

	/**
	 * Adapts a given questionnaire form before being administered to a requesting user in a given community context.
	 * For adaptation purposes, questionnaire authors can make use of a set of author tags, which are replaced by this method.
	 * The syntax for author tags is ${AT}. The following values for AT are supported:
	 * <ul>
	 * 	<li>USER.ID - identifier of requesting user</li>
	 *  <li>USER.NAME - login name of requesting user</li>
	 * 	<li>USER.MAIL - email address of requesting user</li>
	 * 	<li>COMMUNITY.ID - identifier of community, in whose context questionnaire form is requested</li>
	 * 	<li>SURVEY.ID - identifier of survey, in whose context questionnaire form is requested </li>
	 *  <li>SURVEY.NAME - name of survey, in whose context questionnaire form is requested </li>
	 *  <li>SURVEY.DESCRIPTION - description of survey, in whose context questionnaire form is requested </li>
	 *  <li>SURVEY.RESOURCE - resource under consideration in survey, in whose context questionnaire form is requested </li>
	 *  <li>SURVEY.START - ISO-8601 compliant UTC start time of survey, in whose context questionnaire form is requested </li>
	 *  <li>SURVEY.END - ISO-8601 compliant UTC end time of survey, in whose context questionnaire form is requested </li>
	 *  <li>SURVEY.OWNER - owner of survey </li>
	 *  <li>SURVEY.ORGANIZATION - organization owner of survey belongs to </li>
	 *  <li>SURVEY.LOGO - logo to be shown in survey header, e.g. organization logo </li>
	 * </ul>
	 * 
	 * @param originalFormXml
	 * @param survey
	 * @param user
	 * @param community
	 * @return
	 */
	private String adaptForm(String originalFormXml, JSONObject survey, UserAgent user, GroupAgent community){
		// detect all  tags used by questionnaire author throughout the form 
		// and replace them by the respective values.
		Pattern p = Pattern.compile("\\$\\{([^\\}])+\\}");
		Matcher m = p.matcher(originalFormXml);

		String adaptedform = new String(originalFormXml);

		// replace any occurring author tags within questionnaire form
		Vector<String> foundTags = new Vector<String>();
		while(m.find()){
			String tag = m.group().substring(2,m.group().length()-1);
			String value = null;
			if(!foundTags.contains(tag)){
				if(tag.startsWith("USER")){
					if(tag.endsWith("ID")){
						value = "" + user.getId();
					} else if(tag.endsWith("NAME")){
						value = user.getLoginName();
					} else if(tag.endsWith("MAIL")){
						value = user.getEmail();
					}
				} else if(tag.startsWith("COMMUNITY")){
					if(tag.endsWith("ID")){
						if(community != null){
							value = "" + community.getId();
						} else {
							value = "(no community context)";
						}
					} 
				} else if(tag.startsWith("SURVEY.")){
					if (tag.endsWith("ID")){
						value = (String) survey.get("id");
					} else if (tag.endsWith("NAME")){
						value = (String) survey.get("name");
					} else if (tag.endsWith("DESCRIPTION")){
						value = (String) survey.get("description");
					} else if (tag.endsWith("RESOURCE")){
						value = (String) survey.get("resource");
					} else if (tag.endsWith("START")){
						value = (String) survey.get("start");
					} else if (tag.endsWith("END")){
						value = (String) survey.get("end");
					} else if (tag.endsWith("OWNER")){
						value = (String) survey.get("owner");
					} else if (tag.endsWith("ORGANIZATION")){
						value = (String) survey.get("organization");
					} else if (tag.endsWith("LOGO")){
						value = (String) survey.get("logo");
					}
				}

				//TODO: add more author tags, if necessary.

				if(value!=null){
					adaptedform = adaptedform.replaceAll("\\$\\{"+tag+"\\}",value);
					//System.out.println("Resolved questionnaire author tag: "+tag);
					foundTags.add(tag);
				}
				else{
					System.err.println("Warning: could not resolve questionnaire author tag '"+tag+"'");
				}
			}
		}

		/*
				// add a welcome information page to be displayed before any questionnaire content provided by the 
				// questionnaire author is presented.
				String introPageName = "Welcome";
				String introPageInstructions = "First of all welcome and thank you for your participation in our survey '"+svin[1]+"'."+svin[2]+
				"Within this survey you will now be asked to fill in questionnaire '"+quin[1]+"' regarding community "+g.getName()+". "+quin[2]+" On the following "+quin[3]+" pages you will be asked to answer "+(Integer.parseInt(quin[5]) + Integer.parseInt(quin[6]) +Integer.parseInt(quin[7]))+" questions in total."+
				"In "+quin[5]+" questions you will be asked to decide between two alternatives (yes/no or the like). Another "+quin[6]+" questions will ask you to provide an ordinal scale ranking regarding certain properties of service "+ed.getName()+". "+
				"Another "+quin[7]+" questions will ask you to enter informal text (e.g. for personal comments). You can decide for yourself, if you want to answer or skip each individual question. However, please try to answer as many questions as possible. "+
				"If you do not understand a particular question you are advised to skip it. Let's go for it!";

				String introPageXml = "\n<qu:Page name=\""+introPageName+"\" xsi:type=\"qu:InformationPageType\">\n  <qu:Instructions>"+introPageInstructions+"</qu:Instructions>\n</qu:Page>\n";
				adaptedform = adaptedform.replaceFirst("<qu:Page",introPageXml+"<qu:Page");
		 */
		return adaptedform;
	}

	@POST
	@Path("surveys/{id}/questionnaire")
	public HttpResponse setSurveyQuestionnaire(@PathParam("id") int id, @ContentParam String content){

		try {
			int exown;
			exown = checkExistenceOwnership(id,0);

			// check if survey exists; if not, return 404.
			if(exown == -1){
				HttpResponse result = new HttpResponse("Survey " + id + " does not exist.");
				result.setStatus(404);
				return result;
			} 
			// if survey exists, check if active agent is owner. if not, return 401.
			else if (exown == 0){
				HttpResponse result = new HttpResponse("Survey " + id + " may only be managed by its owner.");
				result.setStatus(401);
				return result;
			}

			// if survey exists and active agent is owner, proceed.

			JSONObject o;

			// parse and validate content. If invalid, return 400 (bad request)
			try{
				o = (JSONObject) JSONValue.parseWithException(content);
			} catch (ParseException e){
				HttpResponse result = new HttpResponse(e.getMessage());
				result.setStatus(400);
				return result;
			}

			if(!(o.size() == 1 && o.keySet().contains("qid"))) {
				HttpResponse result = new HttpResponse("Invalid JSON for setting questionnaire! Must only contain one field qid!");
				result.setStatus(400);
				return result;
			}

			// now check if questionnaire really exists
			int qid = Integer.parseInt(o.get("qid")+"");
			HttpResponse qresp = getQuestionnaire(qid);

			if(qresp.getStatus()!=200){
				return qresp;
			}



			surveySetQuestionnaireStatement.clearParameters();
			surveySetQuestionnaireStatement.setInt(1, qid);
			surveySetQuestionnaireStatement.setInt(2, id);
			surveySetQuestionnaireStatement.executeUpdate();

			connection.commit();

			// furthermore, prepare a relational table for storing results

			// TODO: at this point we need to check, if answers were already provided. If so, these answers must be deleted.

			HttpResponse result = new HttpResponse("Questionnaire for survey " + id + " set.");
			result.setStatus(200);
			return result;

		} catch (Exception e) {
			e.printStackTrace();
			HttpResponse result = new HttpResponse("Internal Error: " + e.getMessage());
			result.setStatus(500);
			return result;
		}
	}

	/**
	 * Deletes a survey with a given id. The respective survey may only be deleted, if the active agent is the survey's owner.
	 * 
	 * @param id
	 */
	@DELETE
	@Path("surveys/{id}")
	public HttpResponse deleteSurvey(@PathParam("id") int id){

		try {

			int exown = checkExistenceOwnership(id,0);

			// check if survey exists; if not, return 404.
			if(exown == -1){
				HttpResponse result = new HttpResponse("Survey " + id + " does not exist.");
				result.setStatus(404);
				return result;
			} 
			// if survey exists, check if active agent is owner. if not, return 401.
			else if (exown == 0){
				HttpResponse result = new HttpResponse("Survey " + id + " may only be deleted by its owner.");
				result.setStatus(401);
				return result;
			}

			surveyDeleteStatement.clearParameters();
			surveyDeleteStatement.setInt(1, id);

			int r = surveyDeleteStatement.executeUpdate();
			connection.commit();

			HttpResponse result;

			result = new HttpResponse("Survey " + id + " deleted.");
			result.setStatus(200);

			return result;
		} catch (Exception e) {
			e.printStackTrace();
			HttpResponse result = new HttpResponse("");
			result.setStatus(500);
			return result;
		}
	}

	/**
	 * Retrieves a list of all questionnaires.
	 * @return
	 * @throws SQLException 
	 */
	@GET
	@Path("questionnaires")
	public HttpResponse getQuestionnaires(@QueryParam(defaultValue = "0", name = "f") int full, @QueryParam(defaultValue = "D", name = "q") String query) throws SQLException
	{
		JSONObject r = new JSONObject();
		JSONArray a = new JSONArray();

		if(full <=0){
			questionnairesQueryStatement.clearParameters();
			questionnairesQueryStatement.setString(1,query);

			ResultSet rs = questionnairesQueryStatement.executeQuery();

			while(rs.next()){
				String id = rs.getString("id");
				a.add(epUrl + "mobsos/questionnaires/" + id);
			}

			r.put("questionnaires", a);

			HttpResponse result = new HttpResponse(r.toJSONString());
			result.setStatus(200);
			return result;
		} else {
			questionnairesFullQueryStatement.clearParameters();
			questionnairesFullQueryStatement.setString(1,query);

			ResultSet rs = questionnairesFullQueryStatement.executeQuery();

			if (!rs.isBeforeFirst()){
				r.put("questionnaires", a);
				HttpResponse result = new HttpResponse(r.toJSONString());
				result.setStatus(200);
				return result;
			}

			while(rs.next()){
				JSONObject questionnaire = readQuestionnaireFromResultSet(rs);
				questionnaire.put("url", epUrl + "mobsos/questionnaires/" + questionnaire.get("id"));
				a.add(questionnaire);
			}

			r.put("questionnaires", a);

			HttpResponse result = new HttpResponse(r.toJSONString());
			result.setStatus(200);
			return result;
		}
	}

	/**
	 * Creates a new questionnaire.
	 * @param content
	 * @return
	 */
	@POST
	@Path("questionnaires")
	public HttpResponse createQuestionnaire(@ContentParam String content){
		try {
			JSONObject o;

			try{
				o = parseQuestionnaire(content);
			} catch (IllegalArgumentException | ParseException e){
				HttpResponse result = new HttpResponse(e.getMessage());
				result.setStatus(400);
				return result;
			}

			int qid = storeNewQuestionnaire(o);

			JSONObject r = new JSONObject();
			r.put("url",epUrl + "mobsos/questionnaires/" + qid);
			HttpResponse result = new HttpResponse(r.toJSONString());
			result.setStatus(201);

			return result;

		} catch (Exception e) {
			e.printStackTrace();
			HttpResponse result = new HttpResponse("");
			result.setStatus(500);
			return result;
		}
	}

	/**
	 * Deletes all questionnaires at once without any check for ownership. 
	 * This method should be only be used for development and with absolute caution!
	 */
	@DELETE
	@Path("questionnaires")
	public HttpResponse deleteQuestionnaires(){
		try {
			questionnairesDeleteStatement.executeUpdate();
			connection.commit();

			HttpResponse result = new HttpResponse("");
			result.setStatus(200);
			return result;
		} catch (Exception e) {
			e.printStackTrace();
			HttpResponse result = new HttpResponse("");
			result.setStatus(500);
			return result;
		}
	}

	/**
	 * Retrieves information for a given questionnaire.
	 * 
	 * @param id
	 * @return
	 */
	@GET
	@Path("questionnaires/{id}")
	public HttpResponse getQuestionnaire(@PathParam("id") int id){

		try {
			questionnaireQueryStatement.clearParameters();
			questionnaireQueryStatement.setInt(1, id);

			ResultSet rs = questionnaireQueryStatement.executeQuery();

			if (!rs.isBeforeFirst()){
				HttpResponse result = new HttpResponse("Questionnaire " + id + " does not exist!");
				result.setStatus(404);
				return result;
			}
			rs.next();
			JSONObject r = readQuestionnaireFromResultSet(rs);
			HttpResponse result = new HttpResponse(r.toJSONString());
			result.setStatus(200);
			return result;
		} catch (Exception e) {
			e.printStackTrace();
			HttpResponse result = new HttpResponse("Internal Error: " + e.getMessage());
			result.setStatus(500);
			return result;
		}
	}

	/**
	 * Updates a survey with a given id. The respective survey may only be deleted, if the active agent is the survey's owner.
	 * 
	 * @param id
	 * @return
	 */
	@POST
	@Path("questionnaires/{id}")
	public HttpResponse updateQuestionnaire(@PathParam("id") int id, @ContentParam String content){

		try {
			int exown;
			exown = checkExistenceOwnership(id,1);

			// check if questionnaire exists; if not, return 404.
			if(exown == -1){
				HttpResponse result = new HttpResponse("Questionnaire " + id + " does not exist.");
				result.setStatus(404);
				return result;
			} 
			// if questionnaire exists, check if active agent is owner. if not, return 401.
			else if (exown == 0){
				HttpResponse result = new HttpResponse("Questionnaire " + id + " may only be deleted by its owner.");
				result.setStatus(401);
				return result;
			}

			// if survey exists and active agent is owner, proceed.

			JSONObject o;
			// parse and validate content. If invalid, return 400 (bad request)
			try{
				o = parseQuestionnaire(content);
			} catch (IllegalArgumentException | ParseException e){
				HttpResponse result = new HttpResponse(e.getMessage());
				result.setStatus(400);
				return result;
			}

			questionnaireUpdateStatement.clearParameters();
			questionnaireUpdateStatement.setString(1, (String) o.get("organization") );
			questionnaireUpdateStatement.setString(2, (String) o.get("logo") );
			questionnaireUpdateStatement.setString(3, (String) o.get("name") );
			questionnaireUpdateStatement.setString(4, (String) o.get("description") );
			questionnaireUpdateStatement.setInt(5, id);

			questionnaireUpdateStatement.executeUpdate();
			connection.commit();

			HttpResponse result = new HttpResponse("Questionnaire " + id + " updated.");
			result.setStatus(200);
			return result;

		} catch (Exception e) {
			e.printStackTrace();
			HttpResponse result = new HttpResponse("Internal Error: " + e.getMessage());
			result.setStatus(500);
			return result;
		}
	}

	/**
	 * Deletes a questionnaire with a given id. The respective questionnaire may only be deleted, if the active agent is the questionnaire's owner.
	 * 
	 * @param id
	 */
	@DELETE
	@Path("questionnaires/{id}")
	public HttpResponse deleteQuestionnaire(@PathParam("id") int id){

		try {

			int exown = checkExistenceOwnership(id,1);

			// check if questionnaire exists; if not, return 404.
			if(exown == -1){
				HttpResponse result = new HttpResponse("Questionnaire " + id + " does not exist.");
				result.setStatus(404);
				return result;
			} 
			// if questionnaire exists, check if active agent is owner. if not, return 401.
			else if (exown == 0){
				HttpResponse result = new HttpResponse("Questionnaire " + id + " may only be deleted by its owner.");
				result.setStatus(401);
				return result;
			}

			questionnaireDeleteStatement.clearParameters();
			questionnaireDeleteStatement.setInt(1, id);

			int r = questionnaireDeleteStatement.executeUpdate();
			connection.commit();

			HttpResponse result;

			result = new HttpResponse("Questionnaire " + id + " deleted.");
			result.setStatus(200);

			return result;
		} catch (Exception e) {
			e.printStackTrace();
			HttpResponse result = new HttpResponse("");
			result.setStatus(500);
			return result;
		}
	}

	@GET
	@Path("questionnaires/{id}/form")
	public HttpResponse downloadQuestionnaireForm(@PathParam("id") int id){
		try{

			// check if questionnaire exists; if not, return 404.
			int exown = checkExistenceOwnership(id,1);

			// check if questionnaire exists; if not, return 404.
			if(exown == -1){
				HttpResponse result = new HttpResponse("Questionnaire " + id + " does not exist.");
				result.setStatus(404);
				return result;
			} 

			questionnaireDownloadFormStatement.clearParameters();
			questionnaireDownloadFormStatement.setInt(1, id);

			ResultSet rs = questionnaireDownloadFormStatement.executeQuery();

			if (!rs.isBeforeFirst()){
				HttpResponse result = new HttpResponse("Form for questionnaire " + id + " does not exist!");
				result.setStatus(404);
				return result;
			}

			rs.next();

			String formXml = rs.getString(1);
			// before returning form, make sure it's still valid (may be obsolete step...)
			try{
				validateQuestionnaireData(formXml);
				HttpResponse result = new HttpResponse(formXml);
				result.setStatus(200);
				return result;
			} catch(IOException e){
				e.printStackTrace();
				HttpResponse result = new HttpResponse("Internal error: " + e.getMessage());
				result.setStatus(500);
				return result;

			} catch (SAXException e){
				e.printStackTrace();
				HttpResponse result = new HttpResponse("Questionnaire form is invalid! Cause: " + e.getMessage());
				result.setStatus(400);
				return result;
			}

		} catch (Exception e){
			e.printStackTrace();
			HttpResponse result = new HttpResponse("Internal error: " + e.getMessage());
			result.setStatus(500);
			return result;
		}
	}

	@POST //TODO: replace by PUT as soon as new Web Connector version is available via ivy
	@Path("questionnaires/{id}/form")
	public HttpResponse uploadQuestionnaireForm(@PathParam("id") int id, @ContentParam String formXml){
		try {
			int exown = checkExistenceOwnership(id,1);

			// check if questionnaire exists; if not, return 404.
			if(exown == -1){
				HttpResponse result = new HttpResponse("Questionnaire " + id + " does not exist.");
				result.setStatus(404);
				return result;
			} 
			// if questionnaire exists, check if active agent is owner. if not, return 401.
			else if (exown == 0){
				HttpResponse result = new HttpResponse("Form for questionnaire " + id + "  may only be uploaded by its owner.");
				result.setStatus(401);
				return result;
			}

			Document form;
			// before storing to database validate questionnaire form
			try{
				form = validateQuestionnaireData(formXml);
				System.out.println("Document Element: " + form.getDocumentElement().getNodeName());
				if(!form.getDocumentElement().getNodeName().equals("qu:Questionnaire")){
					HttpResponse result = new HttpResponse("Questionnaire form is invalid! Cause: Document element must be 'qu:Questionnaire'.");
					result.setStatus(400);
					return result;
				}

			} catch (SAXException e){
				e.printStackTrace();
				HttpResponse result = new HttpResponse("Questionnaire form is invalid! Cause: " + e.getMessage());
				result.setStatus(400);
				return result;
			}

			// if form XML is valid, then store it to database
			questionnaireUploadFormStatement.clearParameters();
			questionnaireUploadFormStatement.setString(1, formXml);
			questionnaireUploadFormStatement.setInt(2, id);

			questionnaireUploadFormStatement.executeUpdate();

			connection.commit();

			HttpResponse result = new HttpResponse("Form upload for questionnaire " + id + " successful.");
			result.setStatus(200);
			return result;

		} catch(Exception e){
			e.printStackTrace();
			HttpResponse result = new HttpResponse("Internal error: " + e.getMessage());
			result.setStatus(500);
			return result;
		}
	}

	/*
	@POST
	@Path("surveys/{id}/answer")
	public HttpResponse submitQuestionnaireAnswer(@PathParam("id") int id, @ContentParam String answerXml){
	 */

	@GET
	@Path("surveys/{id}/answers/{cid}")
	public HttpResponse retrieveQuestionnaireAnswersForCommunity(@PathParam("id") int id, @PathParam("cid") long cid){
		try{
			int exown = checkExistenceOwnership(id,0);

			GroupAgent community;

			// if community not found, return 404
			try{
				community = (GroupAgent) this.getActiveNode().getAgent(cid);
			} catch (AgentNotKnownException e){
				HttpResponse result = new HttpResponse("Community " + cid + " does not exist!");
				result.setStatus(404);
				return result;
			} catch (ClassCastException e){
				HttpResponse result = new HttpResponse("Agent " + cid + " is not a community!");
				result.setStatus(400);
				return result;
			}

			// allow access to answer data only, if current user is either survey owner or member of given community
			if(exown == -1 || !community.isMember(this.getActiveAgent())){
				HttpResponse result = new HttpResponse("Access to answer information only for owner of survey " + id + " and members of community " + cid);
				result.setStatus(401);
				return result;
			}

			// retrieve survey id;
			int qid = getQuestionnaireIdForSurvey(id);

			if(qid == -1){
				HttpResponse result = new HttpResponse("No questionnaire defined for survey " + id + "!");
				result.setStatus(404);
				return result;
			}

			// retrieve questionnaire form for survey to do answer validation
			HttpResponse r = downloadQuestionnaireForm(qid); 

			if(200 != r.getStatus()){
				// if questionnaire form does not exist, pass on response containing error status
				return r;
			}

			Document form;

			// parse form to XML document incl. validation; will later on be necessary to build query for
			// questionnaire answer table
			try{
				form = validateQuestionnaireData(r.getResult());
			} catch (SAXException e){
				e.printStackTrace();
				HttpResponse result = new HttpResponse("Questionnaire form is invalid! Cause: " + e.getMessage());
				result.setStatus(400);
				return result;
			}

			// create query for result table

			// example query:
			// 
			//     select uid,
			//     MAX(IF(qkey = 'A.2.1', cast(qval as unsigned), NULL)) AS "A.2.1",
			//     MAX(IF(qkey = 'A.2.2', cast(qval as unsigned), NULL)) AS "A.2.2",
			//     MAX(IF(qkey = 'A.2.3', qval, NULL)) AS "A.2.3"
			//     from mobsos.survey_result where sid = 1 and cid = 1 group by uid;

			JSONObject questions = extractQuestionInformation(form);
			String sql = "select uid, \n"; 

			Iterator<String> it = questions.keySet().iterator();

			while(it.hasNext()){
				String key = it.next();

				JSONObject def = (JSONObject) questions.get(key);
				if("qu:FreeTextQuestionPageType".equals(def.get("type"))){
					sql += "  MAX(IF(qkey = '" + key + "', qval, NULL)) AS \"" + key + "\"";
				} else if("qu:DichotomousQuestionPageType".equals(def.get("type")) || 
						"qu:OrdinalScaleQuestionPageType".equals(def.get("type"))){
					sql += "  MAX(IF(qkey = '" + key + "', cast(qval as unsigned), NULL)) AS \"" + key + "\"";
				}
				if(it.hasNext()){
					sql += ",\n";
				} else {
					sql += "\n";
				}
			}

			sql += " from " + jdbcSchema + ".survey_result where sid ="+ id + " and cid =" + cid + " group by uid;";

			//System.out.println("SQL: \n" + sql);

			// execute generated query
			Statement s = connection.createStatement();
			ResultSet rs = s.executeQuery(sql);

			// format and return result
			String res = createCSVQuestionnaireResult(rs);

			HttpResponse result = new HttpResponse(res);
			result.setStatus(200);
			return result;

		} catch (Exception e){
			e.printStackTrace();
			HttpResponse result = new HttpResponse("Internal error: " + e.getMessage());
			result.setStatus(500);
			return result;
		}
	}

	@GET
	@Path("surveys/{id}/answers")
	public HttpResponse retrieveQuestionnaireAnswersNoCommunity(@PathParam("id") int id){
		try{
			int exown = checkExistenceOwnership(id,0);

			// allow access to answer data only, if current user is either survey owner or member of given community
			if(exown == -1){
				HttpResponse result = new HttpResponse("Access to answer information only for owner of survey " + id + ".");
				result.setStatus(401);
				return result;
			}

			// retrieve survey id;
			int qid = getQuestionnaireIdForSurvey(id);

			if(qid == -1){
				HttpResponse result = new HttpResponse("No questionnaire defined for survey " + id + "!");
				result.setStatus(404);
				return result;
			}

			// retrieve questionnaire form for survey to do answer validation
			HttpResponse r = downloadQuestionnaireForm(qid); 

			if(200 != r.getStatus()){
				// if questionnaire form does not exist, pass on response containing error status
				return r;
			}

			Document form;

			// parse form to XML document incl. validation; will later on be necessary to build query for
			// questionnaire answer table
			try{
				form = validateQuestionnaireData(r.getResult());
			} catch (SAXException e){
				e.printStackTrace();
				HttpResponse result = new HttpResponse("Questionnaire form is invalid! Cause: " + e.getMessage());
				result.setStatus(400);
				return result;
			}

			// create query for result table

			// example query:
			// 
			//     select uid,
			//     MAX(IF(qkey = 'A.2.1', cast(qval as unsigned), NULL)) AS "A.2.1",
			//     MAX(IF(qkey = 'A.2.2', cast(qval as unsigned), NULL)) AS "A.2.2",
			//     MAX(IF(qkey = 'A.2.3', qval, NULL)) AS "A.2.3"
			//     from mobsos.survey_result where sid = 1 and cid = 1 group by uid;

			JSONObject questions = extractQuestionInformation(form);
			String sql = "select uid, cid, \n"; 

			Iterator<String> it = questions.keySet().iterator();

			while(it.hasNext()){
				String key = it.next();

				JSONObject def = (JSONObject) questions.get(key);
				if("qu:FreeTextQuestionPageType".equals(def.get("type"))){
					sql += "  MAX(IF(qkey = '" + key + "', qval, NULL)) AS \"" + key + "\"";
				} else if("qu:DichotomousQuestionPageType".equals(def.get("type")) || 
						"qu:OrdinalScaleQuestionPageType".equals(def.get("type"))){
					sql += "  MAX(IF(qkey = '" + key + "', cast(qval as unsigned), NULL)) AS \"" + key + "\"";
				}
				if(it.hasNext()){
					sql += ",\n";
				} else {
					sql += "\n";
				}
			}

			sql += " from " + jdbcSchema + ".survey_result where sid ="+ id + " group by uid, cid;";

			//System.out.println("SQL: \n" + sql);

			// execute generated query
			Statement s = connection.createStatement();
			ResultSet rs = s.executeQuery(sql);

			// format and return result
			String res = createCSVQuestionnaireResult(rs);

			HttpResponse result = new HttpResponse(res);
			result.setStatus(200);
			return result;

		} catch (Exception e){
			e.printStackTrace();
			HttpResponse result = new HttpResponse("Internal error: " + e.getMessage());
			result.setStatus(500);
			return result;
		}
	}

	private String createCSVQuestionnaireResult(ResultSet rs) throws SQLException{
		int cols = rs.getMetaData().getColumnCount();

		String res = "";
		String headline = "";

		// first create header row
		for(int i=1;i<=cols;i++){
			headline += rs.getMetaData().getColumnName(i);
			if(i<cols) headline += ";";
		}
		res += headline + "\n";
		System.out.println(headline);

		// now compile answer data
		String data = "";
		while(rs.next()){
			for(int i=1;i<=cols;i++){
				Object o = rs.getObject(i);
				if(o != null){
					data += o.toString();
				}
				if(i<cols) data += ";";
			}
			data += "\n";
		}
		res += data.trim();
		return res;
	}

	private String createJSONQuestionnaireResult(ResultSet rs, JSONObject qinfo) throws SQLException{
		JSONObject res = new JSONObject();
		int cols = rs.getMetaData().getColumnCount();

		// first create header row by including all question information
		JSONArray header = new JSONArray();
		for(int i=1;i<=cols;i++){
			JSONObject o = (JSONObject) qinfo.get(rs.getMetaData().getColumnName(i));
			if(o == null){
				o = new JSONObject();
				o.put("id","uid");
				o.put("name","User Identifier");
			} else {
				o.put("id",rs.getMetaData().getColumnName(i));
			}
			header.add(o);
		}
		res.put("head",header);

		// now compile answer data
		JSONArray data = new JSONArray();
		while(rs.next()){

			JSONArray resrow = new JSONArray();

			for(int i=1;i<=cols;i++){
				Object o = rs.getObject(i);
				if(rs.getMetaData().getColumnType(i) == Types.VARCHAR){
					resrow.add(rs.getString(i));
				} else {
					resrow.add(rs.getBigDecimal(i));
				}
			}
			data.add(resrow);
		}
		return res.toJSONString();
	}

	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	@Path("surveys/{id}/answers")
	public HttpResponse submitQuestionnaireAnswerJSON(@PathParam("id") int id, @ContentParam String answerJSON){
		try{

			// retrieve survey id;
			int qid = getQuestionnaireIdForSurvey(id);

			if(qid == -1){
				HttpResponse result = new HttpResponse("No questionnaire defined for survey " + id + "!");
				result.setStatus(404);
				return result;
			}

			// retrieve questionnaire form for survey to do answer validation
			HttpResponse r = downloadQuestionnaireForm(qid); 

			if(200 != r.getStatus()){
				// if questionnaire form does not exist, pass on response containing error status
				return r;
			}

			Document form;
			JSONObject answer;

			// parse form to XML document incl. validation
			try{
				form = validateQuestionnaireData(r.getResult());
			} catch (SAXException e){
				HttpResponse result = new HttpResponse("Questionnaire form is invalid! Cause: " + e.getMessage());
				result.setStatus(400);
				return result;
			}

			try{
				answer = (JSONObject) JSONValue.parseWithException(answerJSON);	
			} catch (ParseException e){
				HttpResponse result = new HttpResponse("Questionnaire answer is not valid JSON! Cause: " + e.getMessage());
				result.setStatus(400);
				return result;
			}

			JSONObject answerFieldTable;

			// validate if answer matches form.
			try{
				answerFieldTable = validateAnswer(form,answer);
			} catch (IllegalArgumentException e){
				HttpResponse result = new HttpResponse("Questionnaire answer is invalid! Cause: " + e.getMessage());
				result.setStatus(400);
				return result;
			}

			int surveyId = id;
			long userId = this.getActiveAgent().getId();

			Iterator<String> it = answerFieldTable.keySet().iterator();
			while(it.hasNext()){

				String qkey = it.next();
				String qval = ""+answerFieldTable.get(qkey);

				submitQuestionAnswerStatementNoC.clearParameters();
				submitQuestionAnswerStatementNoC.setLong(1, userId);
				submitQuestionAnswerStatementNoC.setInt(2,surveyId);
				submitQuestionAnswerStatementNoC.setString(3, qkey);
				submitQuestionAnswerStatementNoC.setString(4, qval);
				submitQuestionAnswerStatementNoC.addBatch();

			}
			submitQuestionAnswerStatementNoC.executeBatch();

			connection.commit();

			HttpResponse result = new HttpResponse("Questionnaire answer stored successfully.");
			result.setStatus(200);
			return result;

		} catch(Exception e){
			e.printStackTrace();
			HttpResponse result = new HttpResponse("Internal error: " + e.getMessage());
			result.setStatus(500);
			return result;
		}
	}

	@POST
	@Consumes(MediaType.TEXT_XML)
	@Path("surveys/{id}/answers")
	public HttpResponse submitQuestionnaireAnswerXML(@PathParam("id") int id, @ContentParam String answerXml){
		try{
			Document answer;
			// parse answer to XML document incl. validation
			try{
				answer = validateQuestionnaireData(answerXml);
			} catch (SAXException e){
				HttpResponse result = new HttpResponse("Questionnaire form is invalid! Cause: " + e.getMessage());
				result.setStatus(400);
				return result;
			}

			return submitQuestionnaireAnswerJSON(id, convertAnswerXMLtoJSON(answer).toJSONString());

		} catch(Exception e){
			e.printStackTrace();
			HttpResponse result = new HttpResponse("Internal error: " + e.getMessage());
			result.setStatus(500);
			return result;
		}
	}

	/*
	@POST
	@Consumes(MediaType.TEXT_XML)
	@Path("surveys/{id}/answers")
	public HttpResponse submitQuestionnaireAnswerXML(@PathParam("id") int id, @ContentParam String answerXml){
		try{

			// retrieve survey id;
			int qid = getQuestionnaireIdForSurvey(id);

			if(qid == -1){
				HttpResponse result = new HttpResponse("No questionnaire defined for survey " + id + "!");
				result.setStatus(404);
				return result;
			}

			// retrieve questionnaire form for survey to do answer validation
			HttpResponse r = downloadQuestionnaireForm(qid); 

			if(200 != r.getStatus()){
				// if questionnaire form does not exist, pass on response containing error status
				return r;
			}

			Document form;
			Document answer;

			// parse form to XML document incl. validation
			try{
				form = validateQuestionnaireData(r.getResult());
			} catch (SAXException e){
				HttpResponse result = new HttpResponse("Questionnaire form is invalid! Cause: " + e.getMessage());
				result.setStatus(400);
				return result;
			}

			// parse answer to XML document incl. validation
			try{
				answer = validateQuestionnaireData(answerXml);
			} catch (SAXException e){
				HttpResponse result = new HttpResponse("Questionnaire form is invalid! Cause: " + e.getMessage());
				result.setStatus(400);
				return result;
			}

			JSONObject answerFieldTable;

			// validate if answer matches form.
			try{
				answerFieldTable = validateAnswer(form,convertAnswerXMLtoJSON(answer));
			} catch (IllegalArgumentException e){
				HttpResponse result = new HttpResponse("Questionnaire answer is invalid! Cause: " + e.getMessage());
				result.setStatus(400);
				return result;
			}

			int surveyId = id;
			long userId = this.getActiveAgent().getId();

			Iterator<String> it = answerFieldTable.keySet().iterator();
			while(it.hasNext()){

				String qkey = it.next();
				String qval = ""+answerFieldTable.get(qkey);

				submitQuestionAnswerStatementNoC.clearParameters();
				submitQuestionAnswerStatementNoC.setLong(1, userId);
				submitQuestionAnswerStatementNoC.setInt(2,surveyId);
				submitQuestionAnswerStatementNoC.setString(3, qkey);
				submitQuestionAnswerStatementNoC.setString(4, qval);
				submitQuestionAnswerStatementNoC.addBatch();

			}
			submitQuestionAnswerStatementNoC.executeBatch();

			connection.commit();

			HttpResponse result = new HttpResponse("Questionnaire answer stored successfully.");
			result.setStatus(200);
			return result;

		} catch(Exception e){
			e.printStackTrace();
			HttpResponse result = new HttpResponse("Internal error: " + e.getMessage());
			result.setStatus(500);
			return result;
		}
	}*/

	@POST
	@Consumes(MediaType.TEXT_XML)
	@Path("surveys/{id}/answers/{cid}")
	public HttpResponse submitQuestionnaireAnswerForCommunityXML(@PathParam("id") int id, @PathParam("cid") long cid, @ContentParam String answerXml){
		try{
			// if community not found, return 404
			try{
				this.getActiveNode().getAgent(cid);
			} catch (AgentNotKnownException e){
				HttpResponse result = new HttpResponse("Community " + cid + " does not exist!");
				result.setStatus(404);
				return result;
			}

			// retrieve survey id;
			int qid = getQuestionnaireIdForSurvey(id);

			if(qid == -1){
				HttpResponse result = new HttpResponse("No questionnaire defined for survey " + id + "!");
				result.setStatus(404);
				return result;
			}

			// retrieve questionnaire form for survey to do answer validation
			HttpResponse r = downloadQuestionnaireForm(qid); 

			if(200 != r.getStatus()){
				// if questionnaire form does not exist, pass on response containing error status
				return r;
			}

			Document form;
			Document answer;

			// parse form to XML document incl. validation
			try{
				form = validateQuestionnaireData(r.getResult());
			} catch (SAXException e){
				HttpResponse result = new HttpResponse("Questionnaire form is invalid! Cause: " + e.getMessage());
				result.setStatus(400);
				return result;
			}

			// parse answer to XML document incl. validation
			try{
				answer = validateQuestionnaireData(answerXml);
			} catch (SAXException e){
				HttpResponse result = new HttpResponse("Questionnaire form is invalid! Cause: " + e.getMessage());
				result.setStatus(400);
				return result;
			}

			JSONObject answerFieldTable;

			// validate if answer matches form.
			try{
				answerFieldTable = validateAnswer(form,convertAnswerXMLtoJSON(answer));
			} catch (IllegalArgumentException e){
				HttpResponse result = new HttpResponse("Questionnaire answer is invalid! Cause: " + e.getMessage());
				result.setStatus(400);
				return result;
			}

			int surveyId = id;
			long communityId = cid;
			long userId = this.getActiveAgent().getId();

			Iterator<String> it = answerFieldTable.keySet().iterator();
			while(it.hasNext()){

				String qkey = it.next();
				String qval = ""+answerFieldTable.get(qkey);

				submitQuestionAnswerStatement.clearParameters();
				submitQuestionAnswerStatement.setLong(1, userId);
				submitQuestionAnswerStatement.setLong(2, cid);
				submitQuestionAnswerStatement.setInt(3,surveyId);
				submitQuestionAnswerStatement.setString(4, qkey);
				submitQuestionAnswerStatement.setString(5, qval);
				submitQuestionAnswerStatement.addBatch();

			}
			submitQuestionAnswerStatement.executeBatch();

			connection.commit();

			HttpResponse result = new HttpResponse("Questionnaire answer stored successfully.");
			result.setStatus(200);
			return result;

		} catch(Exception e){
			e.printStackTrace();
			HttpResponse result = new HttpResponse("Internal error: " + e.getMessage());
			result.setStatus(500);
			return result;
		}
	}

	// ---------------------------- private helper methods -----------------------

	/**
	 * Checks if survey or questionnaire exists and active agent is owner.
	 * 
	 * @param id int survey or questionnaire id
	 * @return int -1 if survey does not exist, 0 if active agent is not owner, 1 if active agent is owner
	 * @throws SQLException 
	 */
	private int checkExistenceOwnership(int id, int type) throws SQLException{
		ResultSet rs;

		if(type == 0){
			surveyCheckOwnerStatement.clearParameters();
			surveyCheckOwnerStatement.setInt(1, id);
			rs = surveyCheckOwnerStatement.executeQuery();
		} else {
			questionnaireCheckOwnerStatement.clearParameters();
			questionnaireCheckOwnerStatement.setInt(1, id);
			rs = questionnaireCheckOwnerStatement.executeQuery();
		}

		// survey/questionnaire does not exist
		if (!rs.isBeforeFirst()){
			return -1; 
		}

		rs.next();
		String owner = rs.getString(1);

		// active agent is not owner.
		if(!owner.equals(""+this.getActiveAgent().getId())){
			return 0;
		} 
		// active agent is owner.
		else {
			return 1;
		}

	}

	/**
	 * Marshals survey data in a result set from the MobSOS database to a JSON representation.
	 */
	private JSONObject readSurveyFromResultSet(ResultSet rs) throws SQLException{

		JSONObject o = new JSONObject();

		o.put("id",rs.getInt("id"));
		o.put("name",rs.getString("name"));
		o.put("description",rs.getString("description"));
		o.put("owner",rs.getString("owner"));
		o.put("organization", rs.getString("organization"));
		o.put("logo", rs.getString("logo"));
		o.put("resource",rs.getString("resource"));
		o.put("qid",rs.getInt("qid"));


		long ts_start = rs.getTimestamp("start").getTime();
		long ts_end = rs.getTimestamp("end").getTime();

		DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
		dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

		String d_start = dateFormat.format(new Date(ts_start));
		String d_end = dateFormat.format(new Date(ts_end));

		//System.out.println(ts_start + " -> " + d_start);
		//System.out.println(ts_end + " -> " + d_end);

		o.put("start",d_start);
		o.put("end",d_end);

		return o;
	}

	/**
	 * Marshals questionnaire data in a result set from the MobSOS database to a JSON representation.
	 */
	private JSONObject readQuestionnaireFromResultSet(ResultSet rs) throws SQLException{

		JSONObject o = new JSONObject();

		o.put("id",rs.getInt("id"));
		o.put("name",rs.getString("name"));
		o.put("description",rs.getString("description"));
		o.put("owner",rs.getString("owner"));
		o.put("organization", rs.getString("organization"));
		o.put("logo", rs.getString("logo"));

		return o;
	}

	/**
	 * Retrieves identifier of questionnaire for given survey or -1 if no questionnaire was defined, yet.
	 */
	private int getQuestionnaireIdForSurvey(int sid) throws SQLException{
		surveyGetQuestionnaireIdStatement.clearParameters();
		surveyGetQuestionnaireIdStatement.setInt(1, sid);

		ResultSet rs = surveyGetQuestionnaireIdStatement.executeQuery();
		if(!rs.isBeforeFirst()){
			return -1;
		} else {
			rs.next();
			return rs.getInt("qid");
		}
	}

	/**
	 * Parses incoming content to a survey JSON representation including checks for completeness, illegal fields and values.
	 */
	private JSONObject parseSurvey(String content) throws ParseException, IllegalArgumentException {
		JSONObject o = (JSONObject) JSONValue.parseWithException(content);

		// check result for unknown illegal fields. If so, parsing fails.
		String[] fields = {"id","owner","organization","logo", "name","description","resource","start","end"};
		for (Object key: o.keySet()){
			if(!Arrays.asList(fields).contains(key)){

				throw new IllegalArgumentException("Illegal survey field '" + key + "' detected!");

			} else {
				if(key.equals("name") && !(o.get(key) instanceof String)){
					throw new IllegalArgumentException("Illegal value for survey field 'name'. Should be a string.");
				}
				else if(key.equals("description") && !(o.get(key) instanceof String)){
					throw new IllegalArgumentException("Illegal value for survey field 'description'. Should be a string.");
				}
				else if(key.equals("organization") && !(o.get(key) instanceof String)){
					throw new IllegalArgumentException("Illegal value for survey field 'organization'. Should be a string.");
				}
				else if(key.equals("logo")){
					try {
						URL u = new URL((String) o.get(key));
						HttpURLConnection con = (HttpURLConnection) u.openConnection();
						if(404 == con.getResponseCode()){
							throw new IllegalArgumentException("Illegal value for survey field logo. Should be a valid URL to an image resource.");
						}
						if(!con.getContentType().matches("image/.*")){
							throw new IllegalArgumentException("Illegal value for survey field logo. Should be a valid URL to an image resource.");
						}
					} catch (MalformedURLException e) {
						throw new IllegalArgumentException("Illegal value for survey field 'logo'. Should be a valid URL to an image resource.");
					} catch (IOException e) {
						throw new IllegalArgumentException("Illegal value for survey field 'logo'. Should be a valid URL to an image resource.");
					}
				} 
				else if(key.equals("resource")){
					try {
						URL u = new URL((String) o.get(key));
						HttpURLConnection con = (HttpURLConnection) u.openConnection();
						if(404 == con.getResponseCode()){
							throw new IllegalArgumentException("Illegal value for survey field 'resource'. Should be a valid URL.");
						}
					} catch (MalformedURLException e) {
						throw new IllegalArgumentException("Illegal value for survey field 'resource'. Should be a valid URL.");
					} catch (IOException e) {
						throw new IllegalArgumentException("Illegal value for survey field 'resource'. Should be a valid URL.");
					}
				}
				else if(key.equals("start")){
					try{
						DatatypeConverter.parseDateTime((String)o.get("start"));
					} catch (IllegalArgumentException e){
						throw new IllegalArgumentException("Illegal value for survey field 'start'. Should be an ISO-8601 formatted time string.");
					}
				}
				else if(key.equals("end")){
					try{
						DatatypeConverter.parseDateTime((String)o.get("end"));
					} catch (IllegalArgumentException e){
						throw new IllegalArgumentException("Illegal value for survey field 'start'. Should be an ISO-8601 formatted time string.");
					}
				} 
			}
		}

		// check if all necessary fields are specified.
		if(	o.get("name") == null || 
				o.get("organization") == null ||
				o.get("logo") == null ||
				o.get("description") == null || 
				o.get("resource") == null || 
				o.get("start") == null ||
				o.get("end") == null){
			throw new IllegalArgumentException("Survey data incomplete! All fields name, organization, logo, description, resource, start, and end must be defined!");
		}

		// finally check time integrity constraint: start must be before end (possibly not enforced by database; mySQL does not support this check)
		long d_start = DatatypeConverter.parseDateTime((String)o.get("start")).getTimeInMillis();
		long d_end = DatatypeConverter.parseDateTime((String)o.get("end")).getTimeInMillis();

		if(d_start >= d_end){
			throw new IllegalArgumentException("Survey data invalid! Start time must be before end time!");
		}

		return o;
	}

	/**
	 * Parses incoming content to a questionnaire JSON representation including checks for completeness, illegal fields and values.
	 */
	private JSONObject parseQuestionnaire(String content) throws ParseException, IllegalArgumentException {

		JSONObject o = (JSONObject) JSONValue.parseWithException(content);

		// check result for unknown illegal fields. If so, parsing fails.
		String[] fields = {"id","owner","organization","logo", "name","description"};
		for (Object key: o.keySet()){
			if(!Arrays.asList(fields).contains(key)){

				throw new IllegalArgumentException("Illegal questionnaire field '" + key + "' detected!");

			} else {
				if(key.equals("name") && !(o.get(key) instanceof String)){
					throw new IllegalArgumentException("Illegal value for questionnaire field 'name'. Should be a string.");
				}
				else if(key.equals("description") && !(o.get(key) instanceof String)){
					throw new IllegalArgumentException("Illegal value for questionnaire field 'description'. Should be a string.");
				}
				else if(key.equals("organization") && !(o.get(key) instanceof String)){
					throw new IllegalArgumentException("Illegal value for questionnaire field 'organization'. Should be a string.");
				}
				else if(key.equals("logo")){
					try {
						URL u = new URL((String) o.get(key));
						HttpURLConnection con = (HttpURLConnection) u.openConnection();
						if(404 == con.getResponseCode()){
							throw new IllegalArgumentException("Illegal value for questionnaire field logo. Should be a valid URL to an image resource.");
						}
						if(!con.getContentType().matches("image/.*")){
							throw new IllegalArgumentException("Illegal value for questionnaire field logo. Should be a valid URL to an image resource.");
						}
					} catch (MalformedURLException e) {
						throw new IllegalArgumentException("Illegal value for questionnaire field 'logo'. Should be a valid URL to an image resource.");
					} catch (IOException e) {
						throw new IllegalArgumentException("Illegal value for questionnaire field 'logo'. Should be a valid URL to an image resource.");
					}
				}
			}
		}

		// check if all necessary fields are specified.
		if(	o.get("name") == null || 
				o.get("organization") == null ||
				o.get("logo") == null ||
				o.get("description") == null
				){
			throw new IllegalArgumentException("Questionnaire data incomplete! All fields name, organization, logo, and description must be defined!");
		}

		return o;
	}

	/**
	 * Stores a new survey described with JSON into the MobSOS database.
	 * The MobSOS database thereby generates a new id returned by this method.
	 */
	private int storeNewSurvey(JSONObject survey) throws IllegalArgumentException, SQLException{

		surveyInsertStatement.clearParameters();
		surveyInsertStatement.setString(1, ""+this.getActiveAgent().getId()); // active agent becomes owner automatically
		surveyInsertStatement.setString(2, (String) survey.get("organization"));
		surveyInsertStatement.setString(3, (String) survey.get("logo"));
		surveyInsertStatement.setString(4, (String) survey.get("name"));
		surveyInsertStatement.setString(5, (String) survey.get("description"));
		surveyInsertStatement.setString(6, (String) survey.get("resource"));
		surveyInsertStatement.setTimestamp(7, new Timestamp(DatatypeConverter.parseDateTime((String)survey.get("start")).getTimeInMillis()));
		surveyInsertStatement.setTimestamp(8, new Timestamp(DatatypeConverter.parseDateTime((String)survey.get("end")).getTimeInMillis()));

		surveyInsertStatement.executeUpdate();
		ResultSet rs = surveyInsertStatement.getGeneratedKeys();

		connection.commit();

		if (rs.next()) {
			return rs.getInt(1);
		} else {
			throw new NoSuchElementException("No new id was created");
		}
	}

	/**
	 * Stores a new questionnaire described with JSON into the MobSOS database.
	 * The MobSOS database thereby generates a new id returned by this method.
	 */
	private int storeNewQuestionnaire(JSONObject questionnaire) throws IllegalArgumentException, SQLException{

		questionnaireInsertStatement.clearParameters();
		questionnaireInsertStatement.setString(1, ""+this.getActiveAgent().getId()); // active agent becomes owner automatically
		questionnaireInsertStatement.setString(2, (String) questionnaire.get("organization"));
		questionnaireInsertStatement.setString(3, (String) questionnaire.get("logo"));
		questionnaireInsertStatement.setString(4, (String) questionnaire.get("name"));
		questionnaireInsertStatement.setString(5, (String) questionnaire.get("description"));

		questionnaireInsertStatement.executeUpdate();
		ResultSet rs = questionnaireInsertStatement.getGeneratedKeys();

		connection.commit();

		if (rs.next()) {
			return rs.getInt(1);
		} else {
			throw new NoSuchElementException("No new id was created");
		}
	}

	/**
	 * Initializes the connection to the MobSOS database and all prepared statements used in service methods.
	 */
	private void initDatabaseConnection() throws ClassNotFoundException, SQLException{

		Class.forName(jdbcDriverClassName);
		connection = DriverManager.getConnection(jdbcUrl+jdbcSchema,jdbcLogin, jdbcPass);
		connection.setAutoCommit(false);

		surveyInsertStatement = connection.prepareStatement("insert into " + jdbcSchema + ".survey(owner, organization, logo, name, description, resource, start, end ) values (?,?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
		surveysQueryStatement = connection.prepareStatement("select id from " + jdbcSchema + ".survey");
		surveysFullQueryStatement = connection.prepareStatement("select * from " + jdbcSchema + ".survey order by name");
		surveysDeleteStatement = connection.prepareStatement("delete from "+ jdbcSchema + ".survey");

		surveyQueryStatement = connection.prepareStatement("select * from " + jdbcSchema + ".survey where id = ?");
		surveyCheckOwnerStatement = connection.prepareStatement("select owner from " + jdbcSchema + ".survey where id = ?");
		surveyUpdateStatement = connection.prepareStatement("update "+ jdbcSchema + ".survey set organization=?, logo=?, name=?, description=?, resource=?, start=?, end=? where id = ?");
		surveySetQuestionnaireStatement = connection.prepareStatement("update "+ jdbcSchema + ".survey set qid=? where id =?");
		surveyDeleteStatement = connection.prepareStatement("delete from "+ jdbcSchema + ".survey where id = ?");

		questionnaireInsertStatement = connection.prepareStatement("insert into " + jdbcSchema + ".questionnaire(owner, organization, logo, name, description,form) values (?,?,?,?,?,\"\")", Statement.RETURN_GENERATED_KEYS);
		questionnairesQueryStatement = connection.prepareStatement("select id from " + jdbcSchema + ".questionnaire where name like '?'");
		questionnairesFullQueryStatement = connection.prepareStatement("select * from " + jdbcSchema + ".questionnaire where name like '?' order by name");
		questionnairesDeleteStatement = connection.prepareStatement("delete from "+ jdbcSchema + ".questionnaire");

		questionnaireQueryStatement = connection.prepareStatement("select id, owner, name, description, organization, logo from " + jdbcSchema + ".questionnaire where id = ?");
		questionnaireCheckOwnerStatement = connection.prepareStatement("select owner from " + jdbcSchema + ".questionnaire where id = ?");
		questionnaireUpdateStatement = connection.prepareStatement("update "+ jdbcSchema + ".questionnaire set organization=?, logo=?, name=?, description=? where id = ?");
		questionnaireDeleteStatement = connection.prepareStatement("delete from "+ jdbcSchema + ".questionnaire where id = ?");

		questionnaireUploadFormStatement = connection.prepareStatement("update "+ jdbcSchema + ".questionnaire set form=? where id = ?");
		questionnaireDownloadFormStatement = connection.prepareStatement("select form from " + jdbcSchema + ".questionnaire where id = ?");

		surveyGetQuestionnaireIdStatement = connection.prepareStatement("select qid from " + jdbcSchema + ".survey where id = ?");
		submitQuestionAnswerStatement = connection.prepareStatement("insert into " + jdbcSchema + ".survey_result(uid,cid,sid,qkey,qval) values (?,?,?,?,?)");
		submitQuestionAnswerStatementNoC = connection.prepareStatement("insert into " + jdbcSchema + ".survey_result(uid,sid,qkey,qval) values (?,?,?,?)");
	}

	/**
	 * Initialize XML parser and validator for questionnaire forms and answers
	 * @throws SAXException 
	 * @throws ParserConfigurationException 
	 */
	private void initXMLInfrastructure() throws SAXException, ParserConfigurationException{
		SchemaFactory factory =
				SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
		Schema schema = factory.newSchema(new File(questionnaireSchemaPath));
		validator = schema.newValidator();

		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		dbf.setSchema(schema);
		dbf.setNamespaceAware(true);
		dbf.setValidating(false);

		parser = dbf.newDocumentBuilder();
	}

	/**
	 * Validates questionnaire forms and answers against the MobSOS Questionnaire XML Schema.
	 * Returns a valid document object in case input data is both well-formed and valid. Throws
	 * an exception in all other cases.
	 * 
	 * @param data
	 * @throws SAXException
	 * @throws IOException
	 */
	private Document validateQuestionnaireData(String data) throws SAXException, IOException{
		// parse and validate. 
		ByteArrayInputStream stringIS = new ByteArrayInputStream(data.getBytes());
		Document doc = parser.parse(stringIS);
		validator.validate(new DOMSource(doc));
		return doc;
	}

	private JSONObject convertAnswerXMLtoJSON(Document answer){

		JSONObject result = new JSONObject();

		NodeList qs = answer.getDocumentElement().getElementsByTagNameNS(MOBSOS_QUESTIONNAIRE_NS,"Question");

		for (int i = 0; i < qs.getLength(); i++) {
			Element q = (Element) qs.item(i);
			String key = q.getAttribute("qid");
			String val = q.getTextContent().trim();
			result.put(key, val);
		}
		return result;
	}

	private JSONObject validateAnswer(Document form, JSONObject answer){
		JSONObject result = new JSONObject();

		JSONObject questions = extractQuestionInformation(form);

		// then iterate over all question items in the submitted answer and check, if 
		// they fulfill all constraints.

		Iterator<String> ait = answer.keySet().iterator();
		while(ait.hasNext()){

			String qid = ait.next();
			String qval = (String) answer.get(qid);

			//System.out.println("Submitted Question ID: "+q.getAttribute("qid"));

			// if question provided in answer is not contained in questionnaire, the answer does not match the questionnaire.
			if(!questions.keySet().contains(qid)){
				throw new IllegalArgumentException("Questionnaire answer does not match form! Question ID "+qid+" is not defined in questionnaire.");
			}

			// if question provided in answer is contained in questionnaire, check further properties...
			JSONObject question = (JSONObject) questions.get(qid);

			// for each type check further constraints
			String type = (String) question.get("type");
			if(type.equals("qu:DichotomousQuestionPageType")){

				// for dichotomous questions the only two possible answers are 0 and 1.
				if(!qval.equals("0") && !qval.equals("1")){
					throw new IllegalArgumentException("Questionnaire answer does not match questionnaire! The value submitted for question "+qid+" is expected to be either 0 or 1, but was "+qval+"!");
				}
				else{
					// everything is ok with this question answer.
					// remove entry from hashtable, write entry to result object
					questions.remove(qid);
					result.put(qid, qval);
				}
			}
			else if(type.equals("qu:OrdinalScaleQuestionPageType")){
				// for ordinal scale questions the answer must be parseable as an integer,
				// which is 
				try{
					int qvali = Integer.parseInt(qval);

					if(qvali > (int) question.get("maxval") ||  qvali < (int) question.get("minval")){
						throw new IllegalArgumentException("Questionnaire answer does not match questionnaire! The value submitted for question "+qid+" is expected to be between "+question.get("minval")+" and " + question.get("maxval") +", but was "+qvali+"!");
					}
					else{
						// everything is ok with this question answer.
						// remove entry from hashtable
						questions.remove(qid);
						result.put(qid, qval);
					}
				}catch(NumberFormatException e){
					throw new IllegalArgumentException("Questionnaire answer does not match questionnaire! The value submitted for question "+qid+" is expected to be parseable as an integer!");
				}

			}
			else if(type.equals("qu:FreeTextQuestionPageType")){
				// nothing to check for freetext question pages. Any text can be entered.
				questions.remove(qid);
				result.put(qid, qval);
			}

		}

		Iterator<String> remainingqids = questions.keySet().iterator();
		while (remainingqids.hasNext()) {
			String qid = (String) remainingqids.next();
			int requireds = (int) ((JSONObject) questions.get(qid)).get("required");
			if(requireds == 1){
				throw new IllegalArgumentException("Questionnaire answer does not match questionnaire! The mandatory question "+qid+" was not answered!");
			}
		}
		return result;
	}

	/*
	private JSONObject validateAnswer(Document form, Document answer){
		JSONObject result = new JSONObject();

		JSONObject questions = extractQuestionInformation(form);

		System.out.println(convertAnswerXMLtoJSON(answer));

		// then iterate over all question items in the submitted answer and check, if 
		// they fulfill all constraints.
		NodeList qs = answer.getDocumentElement().getElementsByTagNameNS(MOBSOS_QUESTIONNAIRE_NS,"Question");

		for (int i = 0; i < qs.getLength(); i++) {
			Element q = (Element) qs.item(i);
			//System.out.println("Submitted Question ID: "+q.getAttribute("qid"));
			String qid = q.getAttribute("qid");
			// if question provided in answer is not contained in questionnaire, the answer does not match the questionnaire.
			if(!questions.keySet().contains(qid)){
				throw new IllegalArgumentException("Questionnaire answer does not match form! Question ID "+qid+" is not defined in questionnaire.");
			}

			// if question provided in answer is contained in questionnaire, check further properties...
			JSONObject question = (JSONObject) questions.get(qid);

			// for each type check further constraints
			String type = (String) question.get("type");
			if(type.equals("qu:DichotomousQuestionPageType")){
				String qval = q.getTextContent().trim();
				// for dichotomous questions the only two possible answers are 0 and 1.
				if(!qval.equals("0") && !qval.equals("1")){
					throw new IllegalArgumentException("Questionnaire answer does not match questionnaire! The value submitted for question "+qid+" is expected to be either 0 or 1, but was "+qval+"!");
				}
				else{
					// everything is ok with this question answer.
					// remove entry from hashtable, write entry to result object
					questions.remove(qid);
					result.put(qid, qval);
				}
			}
			else if(type.equals("qu:OrdinalScaleQuestionPageType")){
				// for ordinal scale questions the answer must be parseable as an integer,
				// which is 
				try{
					int qval = Integer.parseInt(q.getTextContent());

					if(qval > (int) question.get("maxval") ||  qval < (int) question.get("minval")){
						throw new IllegalArgumentException("Questionnaire answer does not match questionnaire! The value submitted for question "+qid+" is expected to be between "+question.get("minval")+" and " + question.get("maxval") +", but was "+qval+"!");
					}
					else{
						// everything is ok with this question answer.
						// remove entry from hashtable
						questions.remove(qid);
						result.put(qid, qval);
					}
				}catch(NumberFormatException e){
					throw new IllegalArgumentException("Questionnaire answer does not match questionnaire! The value submitted for question "+qid+" is expected to be parseable as an integer!");
				}

			}
			else if(type.equals("qu:FreeTextQuestionPageType")){
				// nothing to check for freetext question pages. Any text can be entered.
				questions.remove(qid);
				result.put(qid, q.getTextContent());
			}

		}

		Iterator<String> remainingqids = questions.keySet().iterator();
		while (remainingqids.hasNext()) {
			String qid = (String) remainingqids.next();
			int requireds = (int) ((JSONObject) questions.get(qid)).get("required");
			if(requireds == 1){
				throw new IllegalArgumentException("Questionnaire answer does not match questionnaire! The mandatory question "+qid+" was not answered!");
			}
		}
		return result;
	}*/

	/**
	 * Serializes a given Document to String.
	 * 
	 * @param doc
	 * @return
	 */
	private String getStringFromDoc(org.w3c.dom.Document doc)    {
		DOMImplementationLS domImplementation = (DOMImplementationLS) doc.getImplementation();
		LSSerializer lsSerializer = domImplementation.createLSSerializer();
		return lsSerializer.writeToString(doc);   
	}

	/**
	 * Extracts all question information from a questionnaire form given as XML document.
	 * Information is returned as a JSONObject of the following form:
	 * 
	 * {<QuestionID1>:<QuestionInfo1>,...}, where
	 * <QuestionInfoN> is again a JSONObject with the following fields:
	 * 	* name: name of the question (all)
	 *  * required: is an answer required or optional? (all)
	 *  * type: question type (one of qu:OrdinalScaleQuestionPageType, qu:DichotomousQuestionPageType, qu:FreeTextQuestionPageType)
	 *  * defval: default value (only for ordinal scale and dichotomous)
	 *  * min/maxvalue: minimum/maximum value (only for ordinal scale)
	 *  * min/maxlabel: label for minimum/maximum value (only for dichotomous and ordinal scale)
	 *  
	 * @param questionnaireDocument
	 * @return
	 */
	private JSONObject extractQuestionInformation(Document questionnaireDocument){

		JSONObject questions = new JSONObject();

		NodeList nodeList = questionnaireDocument.getElementsByTagNameNS(MOBSOS_QUESTIONNAIRE_NS, "Page");
		for (int i = 0; i < nodeList.getLength(); i++) {
			Node node = nodeList.item(i);
			if (node.getNodeType() == Node.ELEMENT_NODE) {
				Element e = (Element) node;
				if(e.getAttribute("xsi:type").endsWith("QuestionPageType")){
					JSONObject question = new JSONObject();
					String qtype = e.getAttribute("xsi:type");

					String required = e.getAttribute("required");
					if(required == null || required.equals("false")){
						question.put("required",0);
					} else if (required != null && required.equals("true")){
						question.put("required",1);
					}
					question.put("name", e.getAttribute("name"));
					question.put("type", e.getAttribute("xsi:type"));

					if("qu:OrdinalScaleQuestionPageType".equals(qtype) || "qu:DichotomousQuestionPageType".equals(qtype)){
						question.put("defval",Integer.parseInt(e.getAttribute("defval")));
						question.put("minlabel", e.getAttribute("minlabel"));
						question.put("maxlabel", e.getAttribute("maxlabel"));

						if( "qu:OrdinalScaleQuestionPageType".equals(qtype)){
							question.put("minval", Integer.parseInt(e.getAttribute("minval")));
							question.put("maxval", Integer.parseInt(e.getAttribute("maxval")));
						}
					}
					questions.put(e.getAttribute("qid"),question);
				}
			}
		}
		return questions;
	}

	/**
	 * This method is needed for every RESTful application in LAS2peer.
	 * Do not remove!
	 * 
	 * @return the mapping
	 */
	public String getRESTMapping()
	{
		String result="";
		try {
			result= RESTMapper.getMethodsAsXML(this.getClass());
		} catch (Exception e) {

			e.printStackTrace();
		}
		return result;
	}

}
