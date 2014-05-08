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
import i5.las2peer.restMapper.HttpResponse;
import i5.las2peer.restMapper.MediaType;
import i5.las2peer.restMapper.RESTMapper;
import i5.las2peer.restMapper.annotations.ContentParam;
import i5.las2peer.restMapper.annotations.DELETE;
import i5.las2peer.restMapper.annotations.GET;
import i5.las2peer.restMapper.annotations.POST;
import i5.las2peer.restMapper.annotations.PUT;
import i5.las2peer.restMapper.annotations.Path;
import i5.las2peer.restMapper.annotations.PathParam;
import i5.las2peer.restMapper.annotations.Version;
import i5.las2peer.security.UserAgent;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.NoSuchElementException;
import java.util.TimeZone;

import javax.xml.bind.DatatypeConverter;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.json.simple.parser.ParseException;


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

	private Connection connection;
	private PreparedStatement surveyInsertStatement, surveysQueryStatement, surveysDeleteStatement;
	private PreparedStatement surveyQueryStatement, surveyCheckOwnerStatement, surveyDeleteStatement, surveyUpdateStatement;

	private PreparedStatement questionnaireInsertStatement, questionnairesQueryStatement, questionnairesDeleteStatement;
	private PreparedStatement questionnaireQueryStatement, questionnaireCheckOwnerStatement, questionnaireDeleteStatement, questionnaireUpdateStatement;

	private String epUrl;
	private String jdbcDriverClassName;
	private String jdbcUrl, jdbcSchema;
	private String jdbcLogin, jdbcPass;

	public SurveyService(){
		// set values from configuration file
		this.setFieldValues();

		this.monitor = true;

		try {
			initDatabaseConnection();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		} catch (SQLException e) {
			e.printStackTrace();
		}

		// print out REST mapping for this service
		//System.out.println(getRESTMapping());
	}
	
	/**
	 * Retrieves a list of all surveys.
	 * @return
	 */
	@GET
	@Path("surveys")
	public HttpResponse getSurveys()
	{

		try {
			JSONObject r = new JSONObject();
			JSONArray a = new JSONArray();

			ResultSet rs = surveysQueryStatement.executeQuery();

			while(rs.next()){
				String id = rs.getString("id");
				a.add(epUrl + "mobsos/surveys/" + id);
			}

			r.put("surveys", a);

			HttpResponse result = new HttpResponse(r.toJSONString());
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
	@POST
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
			surveyUpdateStatement.setString(1, (String) o.get("organization") );
			surveyUpdateStatement.setString(2, (String) o.get("logo") );
			surveyUpdateStatement.setString(3, (String) o.get("name") );
			surveyUpdateStatement.setString(4, (String) o.get("description") );
			surveyUpdateStatement.setString(5, (String) o.get("resource") );
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
	 */
	@GET
	@Path("questionnaires")
	public HttpResponse getQuestionnaires()
	{

		try {
			JSONObject r = new JSONObject();
			JSONArray a = new JSONArray();

			ResultSet rs = questionnairesQueryStatement.executeQuery();

			while(rs.next()){
				String id = rs.getString("id");
				a.add(epUrl + "mobsos/questionnaires/" + id);
			}

			r.put("questionnaires", a);

			HttpResponse result = new HttpResponse(r.toJSONString());
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

	private JSONObject readSurveyFromResultSet(ResultSet rs) throws SQLException{
		if(rs == null){
			return null; 
		}

		if(rs.next()){
			JSONObject o = new JSONObject();

			o.put("id",rs.getInt("id"));
			o.put("name",rs.getString("name"));
			o.put("description",rs.getString("description"));
			o.put("owner",rs.getString("owner"));
			o.put("organization", rs.getString("organization"));
			o.put("logo", rs.getString("logo"));
			o.put("resource",rs.getString("resource"));

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

		} else {
			return null;
		}
	}

	private JSONObject readQuestionnaireFromResultSet(ResultSet rs) throws SQLException{
		if(rs == null){
			return null; 
		}

		if(rs.next()){
			JSONObject o = new JSONObject();

			o.put("id",rs.getInt("id"));
			o.put("name",rs.getString("name"));
			o.put("description",rs.getString("description"));
			o.put("owner",rs.getString("owner"));
			o.put("organization", rs.getString("organization"));
			o.put("logo", rs.getString("logo"));

			return o;

		} else {
			return null;
		}
	}

	/**
	 * Parses incoming content to a survey JSON Object including checks for completeness, illegal fields and values.
	 * 
	 * @param content
	 * @return
	 * @throws ParseException
	 * @throws IllegalArgumentException
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
	 * Parses incoming content to a questionnaire JSON Object including checks for completeness, illegal fields and values.
	 * 
	 * @param content
	 * @return
	 * @throws ParseException
	 * @throws IllegalArgumentException
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

	private int storeNewSurvey(JSONObject o) throws IllegalArgumentException, SQLException{

		surveyInsertStatement.clearParameters();
		surveyInsertStatement.setString(1, ""+this.getActiveAgent().getId()); // active agent becomes owner automatically
		surveyInsertStatement.setString(2, (String) o.get("organization"));
		surveyInsertStatement.setString(3, (String) o.get("logo"));
		surveyInsertStatement.setString(4, (String) o.get("name"));
		surveyInsertStatement.setString(5, (String) o.get("description"));
		surveyInsertStatement.setString(6, (String) o.get("resource"));
		surveyInsertStatement.setTimestamp(7, new Timestamp(DatatypeConverter.parseDateTime((String)o.get("start")).getTimeInMillis()));
		surveyInsertStatement.setTimestamp(8, new Timestamp(DatatypeConverter.parseDateTime((String)o.get("end")).getTimeInMillis()));

		surveyInsertStatement.executeUpdate();
		ResultSet rs = surveyInsertStatement.getGeneratedKeys();

		connection.commit();

		if (rs.next()) {
			return rs.getInt(1);
		} else {
			throw new NoSuchElementException("No new id was created");
		}
	}

	private int storeNewQuestionnaire(JSONObject o) throws IllegalArgumentException, SQLException{

		questionnaireInsertStatement.clearParameters();
		questionnaireInsertStatement.setString(1, ""+this.getActiveAgent().getId()); // active agent becomes owner automatically
		questionnaireInsertStatement.setString(2, (String) o.get("organization"));
		questionnaireInsertStatement.setString(3, (String) o.get("logo"));
		questionnaireInsertStatement.setString(4, (String) o.get("name"));
		questionnaireInsertStatement.setString(5, (String) o.get("description"));

		questionnaireInsertStatement.executeUpdate();
		ResultSet rs = questionnaireInsertStatement.getGeneratedKeys();

		connection.commit();

		if (rs.next()) {
			return rs.getInt(1);
		} else {
			throw new NoSuchElementException("No new id was created");
		}
	}

	private void initDatabaseConnection() throws ClassNotFoundException, SQLException{

		Class.forName(jdbcDriverClassName);
		connection = DriverManager.getConnection(jdbcUrl+jdbcSchema,jdbcLogin, jdbcPass);
		connection.setAutoCommit(false);

		surveyInsertStatement = connection.prepareStatement("insert into " + jdbcSchema + ".survey(owner, organization, logo, name, description, resource, start, end ) values (?,?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
		surveysQueryStatement = connection.prepareStatement("select id from " + jdbcSchema + ".survey");
		surveysDeleteStatement = connection.prepareStatement("delete from "+ jdbcSchema + ".survey");

		surveyQueryStatement = connection.prepareStatement("select * from " + jdbcSchema + ".survey where id = ?");
		surveyCheckOwnerStatement = connection.prepareStatement("select owner from " + jdbcSchema + ".survey where id = ?");
		surveyUpdateStatement = connection.prepareStatement("update "+ jdbcSchema + ".survey set organization=?, logo=?, name=?, description=?, resource=?, start=?, end=? where id = ?");
		surveyDeleteStatement = connection.prepareStatement("delete from "+ jdbcSchema + ".survey where id = ?");

		questionnaireInsertStatement = connection.prepareStatement("insert into " + jdbcSchema + ".questionnaire(owner, organization, logo, name, description,form) values (?,?,?,?,?,\"\")", Statement.RETURN_GENERATED_KEYS);
		questionnairesQueryStatement = connection.prepareStatement("select id from " + jdbcSchema + ".questionnaire");
		questionnairesDeleteStatement = connection.prepareStatement("delete from "+ jdbcSchema + ".questionnaire");

		questionnaireQueryStatement = connection.prepareStatement("select id, owner, name, description, organization, logo from " + jdbcSchema + ".questionnaire where id = ?");
		questionnaireCheckOwnerStatement = connection.prepareStatement("select owner from " + jdbcSchema + ".questionnaire where id = ?");
		questionnaireUpdateStatement = connection.prepareStatement("update "+ jdbcSchema + ".questionnaire set organization=?, logo=?, name=?, description=? where id = ?");
		questionnaireDeleteStatement = connection.prepareStatement("delete from "+ jdbcSchema + ".questionnaire where id = ?");

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
