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
import i5.las2peer.p2p.NodeInformation;
import i5.las2peer.restMapper.HttpResponse;
import i5.las2peer.restMapper.RESTMapper;
import i5.las2peer.restMapper.annotations.ContentParam;
import i5.las2peer.restMapper.annotations.DELETE;
import i5.las2peer.restMapper.annotations.GET;
import i5.las2peer.restMapper.annotations.POST;
import i5.las2peer.restMapper.annotations.Path;
import i5.las2peer.restMapper.annotations.PathParam;
import i5.las2peer.restMapper.annotations.Version;
import i5.las2peer.security.UserAgent;
import i5.las2peer.tools.CryptoException;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
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
 * @author Dominik Renzel
 *
 */
//@Path("mobsos")
@Version("0.1")
//@Consumes("application/json")
//@Produces("application/json")
public class SurveyService extends Service {

	private Connection connection;
	private PreparedStatement surveyInsertStatement, surveysQueryStatement, surveysDeleteStatement;
	private PreparedStatement surveyQueryStatement, surveyDeleteStatement;

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

	@GET
	@Path("mobsos/surveys")
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
	@Path("mobsos/surveys")
	public HttpResponse createSurvey(@ContentParam String content)
	{
		try {
			int sid = storeSurvey(content);
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
	 * Deletes all surveys at once. This method should be used with caution!
	 */
	@DELETE
	@Path("mobsos/surveys")
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
	@Path("mobsos/surveys/{id}")
	public HttpResponse getSurvey(@PathParam("id") int id){

		try {
			surveyQueryStatement.clearParameters();
			surveyQueryStatement.setInt(1, id);

			ResultSet rs = surveyQueryStatement.executeQuery();

			if (rs == null){
				HttpResponse result = new HttpResponse("Survey " + id + " does not exist!");
				result.setStatus(404);
				return result;
			}

			JSONObject r = parseSurvey(rs);
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
	
	@DELETE
	@Path("mobsos/surveys/{id}")
	public HttpResponse deleteSurvey(@PathParam("id") int id){
		try {
			surveyDeleteStatement.clearParameters();
			surveyDeleteStatement.setInt(1, id);
			
			int r = surveyDeleteStatement.executeUpdate();
			System.out.println("Deletion result: " + r);
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

	@SuppressWarnings("unchecked")
	private JSONObject parseSurvey(ResultSet rs) throws SQLException{
		if(rs == null){
			return null; 
		}

		if(rs.next()){
			JSONObject o = new JSONObject();

			o.put("id",rs.getInt("id"));
			o.put("name",rs.getString("name"));
			o.put("description",rs.getString("description"));
			o.put("owner",rs.getString("owner"));
			o.put("resource",rs.getString("resource"));

			long ts_start = rs.getTimestamp("start").getTime();
			long ts_end = rs.getTimestamp("end").getTime();

			DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
			dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

			String d_start = dateFormat.format(new Date(ts_start));
			String d_end = dateFormat.format(new Date(ts_end));

			System.out.println(ts_start + " -> " + d_start);
			System.out.println(ts_end + " -> " + d_end);

			o.put("start",d_start);
			o.put("end",d_end);

			return o;

		} else {
			return null;
		}
	}

	/**
	 * @throws ClassNotFoundException
	 * @throws SQLException
	 */
	private void initDatabaseConnection() throws ClassNotFoundException, SQLException{

		Class.forName(jdbcDriverClassName);
		connection = DriverManager.getConnection(jdbcUrl+jdbcSchema,jdbcLogin, jdbcPass);
		connection.setAutoCommit(false);

		surveyInsertStatement = connection.prepareStatement("insert into " + jdbcSchema + ".survey(owner, name, description, resource, start, end ) values (?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
		surveysQueryStatement = connection.prepareStatement("select id from " + jdbcSchema + ".survey");
		surveysDeleteStatement = connection.prepareStatement("delete from "+ jdbcSchema + ".survey");
		
		surveyQueryStatement = connection.prepareStatement("select * from " + jdbcSchema + ".survey where id = ?");
		surveyDeleteStatement = connection.prepareStatement("delete from "+ jdbcSchema + ".survey where id = ?");

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

	private int storeSurvey(String content) throws IllegalArgumentException, SQLException{

		JSONObject o;

		try {
			o = (JSONObject) JSONValue.parseWithException(content);

			if(	o.get("name") == null || 
					o.get("description") == null || 
					o.get("resource") == null || 
					o.get("start") == null ||
					o.get("end") == null){
				throw new IllegalArgumentException("Invalid data format detected! Each survey data object must have fields.");
			}

			// maybe in future we are able to have OpenID Connect identities here...
			String owner;
			if(this.getActiveAgent() instanceof UserAgent){
				UserAgent u = (UserAgent) this.getActiveAgent();
				if(u.getEmail() != null){
					owner = u.getEmail();
				} else {
					owner = ""+u.getId();
				}
			} else {
				owner = "" + this.getActiveAgent().getId();
			}

			surveyInsertStatement.clearParameters();
			surveyInsertStatement.setString(1, ""+this.getActiveAgent().getId());
			surveyInsertStatement.setString(2, (String) o.get("name"));
			surveyInsertStatement.setString(3, (String) o.get("description"));
			surveyInsertStatement.setString(4, (String) o.get("resource"));
			surveyInsertStatement.setTimestamp(5, new Timestamp(DatatypeConverter.parseDateTime((String)o.get("start")).getTimeInMillis()));
			surveyInsertStatement.setTimestamp(6, new Timestamp(DatatypeConverter.parseDateTime((String)o.get("end")).getTimeInMillis()));

			surveyInsertStatement.executeUpdate();
			ResultSet rs = surveyInsertStatement.getGeneratedKeys();

			connection.commit();

			if (rs.next()) {
				return rs.getInt(1);
			} else {
				throw new NoSuchElementException("No new id was created");
			}

		} catch (ParseException e) {
			throw new IllegalArgumentException("Invalid data format detected! Data must be JSON-formatted. " + e.getMessage());	
		}

	}

}
