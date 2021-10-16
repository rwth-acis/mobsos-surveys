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

package i5.las2peer.services.mobsos.surveys;

import i5.las2peer.api.Context;
import i5.las2peer.api.ManualDeployment;
import i5.las2peer.api.logging.MonitoringEvent;
import i5.las2peer.api.security.Agent;
import i5.las2peer.api.security.AnonymousAgent;
import i5.las2peer.api.security.GroupAgent;
import i5.las2peer.api.security.UserAgent;
import i5.las2peer.restMapper.RESTService;
import i5.las2peer.restMapper.annotations.ServicePath;
import io.swagger.annotations.*;
import org.apache.commons.dbcp2.BasicDataSource;
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

import javax.sql.DataSource;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.xml.XMLConstants;
import javax.xml.bind.DatatypeConverter;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.dom.DOMSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.sql.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.apache.commons.lang3.StringEscapeUtils.escapeHtml4;

/**
 * 
 * MobSOS Survey Service
 * 
 * A simple RESTful service for online survey management.
 * 
 * The data model behind this service consists of three main entities: surveys, questionnaires and responses. A
 * questionnaire is described by basic metadata and most essentially a form. Any questionnaire can be re-used in an
 * arbitrary number of surveys. Surveys serve as management contexts for the collection of responses to a given
 * questionnaire. A survey is described by basic metadata, a start and optional end-time, a subject URI linking to an
 * arbitrary resource being target of the survey, a reference to a predefined questionnaire, and a number of responses.
 * A response is described by an optional identifier of the survey participant, the time of submitting the response, and
 * a completed questionnaire form. For questionnaire forms, MobSOS comes with an XML Schema defining scale, dichotomous,
 * and free-text items, but - given the inherent extensibility of XML - being open for further extensions.
 * 
 * This service is part of the MobSOS toolset dedicated to exploring, modeling, and measuring Community Information
 * System (CIS) Success as a complex construct established by multiple dimensions and factors. As part of MobSOS, this
 * service enables to collect subjective data enabling qualitative and quantitative measurements of CIS Success.
 * 
 * However, the design of MobSOS Survey Service and its underlying data model is deliberately kept as generic and
 * independent as possible and should thus be applicable for any kind of online survey.
 * 
 * Survey service allows for i18n in its HTML resource representations.
 * 
 * @author Dominik Renzel
 *
 */
@ManualDeployment
@ServicePath("mobsos-surveys")
public class SurveyService extends RESTService {

	public final static String MOBSOS_QUESTIONNAIRE_NS = "http://dbis.rwth-aachen.de/mobsos/questionnaire.xsd";

	private static BasicDataSource dataSource;

	private DocumentBuilder parser;
	private Validator validator;

	// fields read from service configuration file
	private String epUrl, las2peerUrl, questionnaireSchemaPath;
	private String jdbcDriverClassName, jdbcUrl, jdbcSchema, jdbcLogin, jdbcPass;
	private String oidcSchema, oidcProviderName, oidcProviderLogo, oidcProviderUrl, oidcClientId;

	private String staticContentUrl;

	public SurveyService() {
		// set values from configuration file
		this.setFieldValues();

		// make sure las2peerUrl and staticContentUrl have trailing slash
		if (!las2peerUrl.endsWith("/")) {
			las2peerUrl += "/";
		}

		if (!epUrl.endsWith("/")) {
			epUrl += "/";
		}

		if (staticContentUrl == null || staticContentUrl.isEmpty()) {
			staticContentUrl = epUrl;
		} else {
			if (!staticContentUrl.endsWith("/")) {
				staticContentUrl += "/";
			}
		}



		// include this service into las2peer monitoring
		// this.monitor = true;

		// prepare database connection pooling and XML infrastructure
		try {
			setupDataSource();
			initXMLInfrastructure();
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	/**
	 * TODO: write documentation
	 * 
	 * @throws ClassNotFoundException
	 */
	private void setupDataSource() throws ClassNotFoundException {

		// request classloader to load JDBC driver class
		Class.forName(jdbcDriverClassName);

		// prepare and configure data source
		dataSource = new BasicDataSource();
		dataSource.setDefaultAutoCommit(true);
		dataSource.setDriverClassName(jdbcDriverClassName);
		dataSource.setUsername(jdbcLogin);
		dataSource.setPassword(jdbcPass);
		dataSource.setUrl(jdbcUrl + jdbcSchema);
		dataSource.setValidationQuery("select 1");
		dataSource.setDefaultQueryTimeout(1000);
		dataSource.setMaxConnLifetimeMillis(100000);
	}

	/**
	 * Initialize XML parser and validator for questionnaire forms and answers
	 * 
	 * @throws SAXException
	 * @throws ParserConfigurationException
	 */
	private void initXMLInfrastructure() throws SAXException, ParserConfigurationException {
		SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
		Schema schema = factory.newSchema(new File(questionnaireSchemaPath));
		validator = schema.newValidator();

		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		dbf.setSchema(schema);
		dbf.setNamespaceAware(true);
		dbf.setValidating(false);

		parser = dbf.newDocumentBuilder();
	}

	// ============= QUESTIONNAIRE-RELATED RESOURCES ==============
	@Override
	protected void initResources() {
		getResourceConfig().register(Resource.class);
	}

	// //////////////////////////////////////////////////////////////////////////////////////
	// Service methods.
	// //////////////////////////////////////////////////////////////////////////////////////
	@Path("/") // this is the root resource
	@Api
	@SwaggerDefinition(
			info = @Info(
					title = "MobSOS Surveys",
					version = "0.2",
					description = "<p>A simple RESTful service for online survey management.</p><p>MobSOS Surveys is part of the MobSOS "
							+ "Tool Set dedicated to exploring, modeling, and measuring Community Information System (CIS) "
							+ "Success as a complex construct established by multiple dimensions and factors. As part of "
							+ "MobSOS, this service enables to collect subjective data enabling qualitative and quantitative "
							+ "measurements of CIS Success.</p>",
					termsOfService = "",
					contact = @Contact(
							name = "Dominik Renzel",
							url = "",
							email = "renzel@dbis.rwth-aachen.de"),
					license = @License(
							name = "MIT",
							url = "https://github.com/rwth-acis/mobsos-survey/blob/master/LICENSE")))
	public static class Resource {
		private SurveyService service = (SurveyService) Context.getCurrent().getService();

		@GET
		@Produces(MediaType.TEXT_HTML)
		@Path("/questionnaires")
		public Response getQuestionnairesHTML(@HeaderParam("accept-language") String lang) {
			String onAction = "retrieving questionnaires HTML";
			// only respond with template; nothing to be adapted
			try {
				// load template
				Scanner scanner;
				scanner = new Scanner(new File("./etc/html/questionnaires-template.html"));
				String html = scanner.useDelimiter("\\A").next();
				scanner.close();

				// localize template
				html = i18n(html, lang);

				// fill in placeholders
				html = fillPlaceHolder(html, "EP_URL", service.epUrl);
				html = fillPlaceHolder(html, "L2P_URL", service.las2peerUrl);
				html = fillPlaceHolder(html, "SC_URL", service.staticContentUrl);

				html = fillPlaceHolder(html, "OIDC_PROV_NAME", service.oidcProviderName);
				html = fillPlaceHolder(html, "OIDC_PROV_LOGO", service.oidcProviderLogo);
				html = fillPlaceHolder(html, "OIDC_PROV_URL", service.oidcProviderUrl);
				html = fillPlaceHolder(html, "OIDC_CLNT_ID", service.oidcClientId);

				// finally return resulting HTML
				return Response.status(Status.OK).entity(html).build();
			} catch (FileNotFoundException e) {
				return internalError(onAction);
			}
		}

		/**
		 * TODO: write documentation Retrieves a list of all questionnaires.
		 * 
		 * @return
		 */
		@SuppressWarnings("unchecked")
		@GET
		@Path("/questionnaires")
		@Produces(MediaType.APPLICATION_JSON)
		@ApiResponses(
				value = { @ApiResponse(
						code = HttpURLConnection.HTTP_OK,
						message = "Questionnaires data (TODO: introduce Swagger models)"),
						@ApiResponse(
								code = HttpURLConnection.HTTP_NOT_FOUND,
								message = "Error - 404"),
						@ApiResponse(
								code = HttpURLConnection.HTTP_BAD_REQUEST,
								message = "Error Bad Request"), })
		@ApiOperation(
				value = "getQuestionnaires",
				notes = "Query parameter matches questionnaire name, description.")
		public Response getQuestionnaires(@QueryParam("full") @DefaultValue("1") int full,
				@QueryParam("q") @DefaultValue("") String query) {
			String onAction = "retrieving questionnaires";

			try {
				JSONObject r = new JSONObject(); // result to return in HTTP response
				JSONArray qs = new JSONArray(); // variable for collecting questionnaires from DB

				Connection c = null;
				PreparedStatement s = null;
				ResultSet rs = null;

				// use query for questionnaire id per default
				String sQuery = "select id from " + service.jdbcSchema
						+ ".questionnaire where name like ? or description like ? or organization like ?";

				// if query param full is provided greater 0, then use query for full questionnaire data set.
				if (full > 0) {
					sQuery = "select * from " + service.jdbcSchema
							+ ".questionnaire where name like ? or description like ? or organization like ? order by name";
				}

				// +++ dsi
				try {
					c = dataSource.getConnection();
					s = c.prepareStatement(sQuery);
					s.setString(1, "%" + query + "%");
					s.setString(2, "%" + query + "%");
					s.setString(3, "%" + query + "%");

					rs = s.executeQuery();

					// in case result set is empty...
					if (!rs.isBeforeFirst()) {
						r.put("questionnaires", qs);
						return Response.status(Status.OK).entity(r.toJSONString()).build();
					}

					// in case result set contains entries...
					while (rs.next()) {
						if (full > 0) {
							JSONObject questionnaire = readQuestionnaireFromResultSet(rs);
							questionnaire.put("url", service.epUrl + "questionnaires/" + questionnaire.get("id"));
							qs.add(questionnaire);
						} else {
							String id = rs.getString("id");
							qs.add(service.epUrl + "questionnaires/" + id);
						}
					}

				} catch (Exception e) {
					e.printStackTrace();
					return internalError(onAction);
				} finally {
					try {
						if (rs != null) {
							rs.close();
						}
					} catch (Exception e) {
						e.printStackTrace();
						return internalError(onAction);
					}
					try {
						if (s != null) {
							s.close();
						}
					} catch (Exception e) {
						e.printStackTrace();
						return internalError(onAction);
					}
					try {
						if (c != null) {
							c.close();
						}
					} catch (Exception e) {
						e.printStackTrace();
						return internalError(onAction);
					}
				}
				// --- dsi

				r.put("questionnaires", qs);
				return Response.status(Status.OK).entity(r.toJSONString()).build();
			} catch (Exception e) {
				e.printStackTrace();
				return internalError(onAction);
			}
		}

		/**
		 * TODO: write documentation
		 * 
		 * Creates a new questionnaire.
		 * 
		 * @param content
		 * @return
		 */
		@SuppressWarnings("unchecked")
		@POST
		@Consumes(MediaType.APPLICATION_JSON)
		@Produces({ MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN })
		@Path("questionnaires")
		@ApiOperation(
				value = "createQuestionnaire",
				notes = "Requires authentication.")
		@ApiResponses(
				value = { @ApiResponse(
						code = 201,
						message = "Questionnaire created successfully."),
						@ApiResponse(
								code = 400,
								message = "Questionnaire data invalid."),
						@ApiResponse(
								code = 401,
								message = "Questionnaire creation requires authentication."),
						@ApiResponse(
								code = 409,
								message = "Questionnaire already exists.") })
		public Response createQuestionnaire(String content) {

			if (Context.get().getMainAgent() instanceof AnonymousAgent) {
				return Response.status(Status.UNAUTHORIZED).entity("Please authenticate to create questionnaires!")
						.build();
			}
			String onAction = "creating new questionnaire";

			try {
				JSONObject o;

				// first parse passed questionnaire data
				try {
					o = parseQuestionnaire(content);
				} catch (IllegalArgumentException e) {
					// if passed data is invalid, respond error to user
					return Response.status(Status.BAD_REQUEST).entity("Invalid questionnaire " + e.getMessage())
							.type(MediaType.TEXT_PLAIN).build();
				}

				// store valid questionnaire to database
				try {
					int qid = storeNewQuestionnaire(o);

					// respond to user with newly created id/URL
					JSONObject r = new JSONObject();
					r.put("id", qid);
					r.put("url", service.epUrl + "questionnaires/" + qid);
					return Response.status(Status.CREATED).entity(r.toJSONString()).type(MediaType.APPLICATION_JSON)
							.build();
				} catch (SQLException e) {
					if (0 <= e.getMessage().indexOf("Duplicate")) {
						return Response.status(Status.CONFLICT).entity("Questionnaire already exists")
								.type(MediaType.TEXT_PLAIN).build();
					} else {
						e.printStackTrace();
						return internalError(onAction);
					}
				}

			} catch (Exception e) {
				e.printStackTrace();
				return internalError(onAction);
			}
		}

		/**
		 * TODO: write documentation
		 * 
		 * Deletes all questionnaires at once without any check for ownership. This method should be only be used for
		 * development and with absolute caution!
		 */
		@DELETE
		@Path("/questionnaires")
		public Response deleteQuestionnaires() {

			if (Context.get().getMainAgent() instanceof AnonymousAgent) {
				return Response.status(Status.UNAUTHORIZED).entity("Please autheticate to delete questionnaires!")
						.build();
			}

			String onAction = "deleting all questionnaires";

			Connection conn = null;
			PreparedStatement stmt = null;
			ResultSet rset = null;

			try {
				conn = dataSource.getConnection();
				stmt = conn.prepareStatement("delete from " + service.jdbcSchema + ".questionnaire");
				stmt.executeUpdate();

				return Response.status(Status.OK).entity("").build();

			} catch (SQLException | UnsupportedOperationException e) {
				return internalError(onAction);
			} finally {
				try {
					if (rset != null) {
						rset.close();
					}
				} catch (Exception e) {
					e.printStackTrace();
					return internalError(onAction);
				}
				try {
					if (stmt != null) {
						stmt.close();
					}
				} catch (Exception e) {
					e.printStackTrace();
					return internalError(onAction);
				}
				try {
					if (conn != null) {
						conn.close();
					}
				} catch (Exception e) {
					e.printStackTrace();
					return internalError(onAction);
				}
			}
		}

		@GET
		@Produces(MediaType.APPLICATION_JSON)
		@Path("userinfo")
		public Response getUserInfo() {

			String onAction = "retrieving user info";
			try {
				JSONObject uinfo = getActiveUserInfo();

				return Response.status(Status.OK).entity(uinfo.toJSONString()).build();

			} catch (ParseException e) {
				return internalError(onAction + ": " + e.getMessage());
			}
		}

		/**
		 * TODO: write documentation Retrieves information for a given questionnaire.
		 * 
		 * @param id
		 * @return
		 */
		@GET
		@Produces(MediaType.APPLICATION_JSON)
		@Path("questionnaires/{id}")
		@ApiOperation(
				value = "getQuestionnaire",
				notes = "Use parent resource to retrieve list of existing questionnaires.")
		@ApiResponses(
				value = { @ApiResponse(
						code = 200,
						message = "Questionnaire data (TODO: introduce Swagger models)"),
						@ApiResponse(
								code = 404,
								message = "Questionnaire does not exist.") })
		public Response getQuestionnaire(@PathParam("id") int id) {

			String onAction = "retrieving questionnaire " + id;

			try {

				Connection conn = null;
				PreparedStatement stmt = null;
				ResultSet rset = null;

				try {
					conn = dataSource.getConnection();
					stmt = conn.prepareStatement("select id, owner, name, description, organization, logo, lang from "
							+ service.jdbcSchema + ".questionnaire where id = ?");
					stmt.setInt(1, id);

					rset = stmt.executeQuery();

					if (!rset.isBeforeFirst()) {
						return Response.status(Status.NOT_FOUND).entity("Questionnaire " + id + " does not exist!")
								.build();
					}
					rset.next();
					JSONObject r = readQuestionnaireFromResultSet(rset);

					return Response.status(Status.OK).entity(r.toJSONString()).build();

				} catch (SQLException | UnsupportedOperationException e) {
					return internalError(onAction);
				} finally {
					try {
						if (rset != null) {
							rset.close();
						}
					} catch (Exception e) {
						e.printStackTrace();
						return internalError(onAction);
					}
					try {
						if (stmt != null) {
							stmt.close();
						}
					} catch (Exception e) {
						e.printStackTrace();
						return internalError(onAction);
					}
					try {
						if (conn != null) {
							conn.close();
						}
					} catch (Exception e) {
						e.printStackTrace();
						return internalError(onAction);
					}
				}
			}

			catch (Exception e) {
				e.printStackTrace();
				return internalError(onAction);
			}
		}

		/**
		 * TODO: write documentation
		 * 
		 * @param id
		 * @return
		 */
		@GET
		@Produces(MediaType.TEXT_HTML)
		@Path("questionnaires/{id}")
		public Response getQuestionnaireHTML(@HeaderParam("accept-language") @DefaultValue("en-US") String lang,
				@PathParam("id") int id) {

			String onAction = "retrieving individual questionnaire HTML";

			try {
				// if questionnaire does not exist, return 404.
				if (checkExistenceOwnership(id, 1) == -1) {
					return Response.status(Status.NOT_FOUND).entity("Questionnaire does not exist!").build();
				}
			} catch (Exception e1) {
				return internalError(onAction);
			}
			// adapt template to specific questionnaire
			try {
				// load template from file
				Scanner scanner = new Scanner(new File("./etc/html/questionnaire-id-template.html"));
				String html = scanner.useDelimiter("\\A").next();
				scanner.close();
				// localize template
				html = i18n(html, lang);

				// fill in placeholders with values
				html = fillPlaceHolder(html, "ID", "" + id);
				html = fillPlaceHolder(html, "EP_URL", service.epUrl);
				html = fillPlaceHolder(html, "L2P_URL", service.las2peerUrl);
				html = fillPlaceHolder(html, "SC_URL", service.staticContentUrl);
				html = fillPlaceHolder(html, "OIDC_PROV_NAME", service.oidcProviderName);
				html = fillPlaceHolder(html, "OIDC_PROV_LOGO", service.oidcProviderLogo);
				html = fillPlaceHolder(html, "OIDC_PROV_URL", service.oidcProviderUrl);
				html = fillPlaceHolder(html, "OIDC_CLNT_ID", service.oidcClientId);

				// finally return resulting HTML
				return Response.status(Status.OK).entity(html).build();
			} catch (FileNotFoundException e) {
				return internalError(onAction);
			}
		}

		/**
		 * TODO: write documentation
		 * 
		 * Updates a survey with a given id. The respective survey may only be deleted, if the active agent is the
		 * survey's owner.
		 * 
		 * @param id
		 * @return
		 */
		@PUT
		@Consumes(MediaType.APPLICATION_JSON)
		@Path("questionnaires/{id}")
		@ApiOperation(
				value = "updateQuestionnaire",
				notes = "Requires authentication. Use parent resource to retrieve list of existing questionnaires.")
		@ApiResponses(
				value = { @ApiResponse(
						code = 200,
						message = "Questionnaire updated successfully."),
						@ApiResponse(
								code = 400,
								message = "Questionnaire data invalid."),
						@ApiResponse(
								code = 401,
								message = "Questionnaire may only be updated by its owner."),
						@ApiResponse(
								code = 404,
								message = "Questionnaire does not exist.") })
		public Response updateQuestionnaire(@PathParam("id") int id, String content) {

			if (Context.get().getMainAgent() instanceof AnonymousAgent) {
				return Response.status(Status.UNAUTHORIZED).entity("Please authenticate to update questionnaire!")
						.build();
			}

			String onAction = "updating questionnaire " + id;

			try {
				Connection c = null;
				PreparedStatement s = null;
				ResultSet rs = null;

				// +++ dsi
				try {

					int exown;
					exown = checkExistenceOwnership(id, 1);

					// check if questionnaire exists; if not, return 404.
					if (exown == -1) {
						return Response.status(Status.NOT_FOUND).entity("Questionnaire " + id + " does not exist.")
								.build();
					}
					// if questionnaire exists, check if active agent is owner. if not, return 401.
					else if (exown == 0) {
						return Response.status(Status.UNAUTHORIZED)
								.entity("Questionnaire " + id + " may only be updated by its owner.").build();
					}

					// Proceed, if survey exists and active agent is owner

					// parse and validate content. If invalid, return 400 (bad request)
					JSONObject o;

					try {
						o = parseQuestionnaire(content);
					} catch (IllegalArgumentException e) {
						// respond with 400, if content for updated questionnaire is not valid
						return Response.status(Status.BAD_REQUEST)
								.entity("Invalid questionnaire data! " + e.getMessage()).build();
					}

					// if parsed content is ok, execute update
					c = dataSource.getConnection();
					s = c.prepareStatement("update " + service.jdbcSchema
							+ ".questionnaire set organization=?, logo=?, name=?, description=?, lang=? where id = ?");

					s.setString(1, (String) o.get("organization"));
					s.setString(2, (String) o.get("logo"));
					s.setString(3, (String) o.get("name"));
					s.setString(4, (String) o.get("description"));
					s.setString(5, (String) o.get("lang"));
					s.setInt(6, id);

					s.executeUpdate();
					return Response.status(Status.OK).entity("Questionnaire " + id + " updated successfully.").build();

				} catch (Exception e) {
					e.printStackTrace();
					return internalError(onAction);
				} finally {
					try {
						if (rs != null) {
							rs.close();
						}
					} catch (Exception e) {
						e.printStackTrace();
						return internalError(onAction);
					}
					try {
						if (s != null) {
							s.close();
						}
					} catch (Exception e) {
						e.printStackTrace();
						return internalError(onAction);
					}
					try {
						if (c != null) {
							c.close();
						}
					} catch (Exception e) {
						e.printStackTrace();
						return internalError(onAction);
					}
				}
				// --- dsi

			} catch (Exception e) {
				e.printStackTrace();
				return internalError(onAction);
			}
		}

		/**
		 * TODO: write documentation
		 * 
		 * Deletes a questionnaire with a given id. The respective questionnaire may only be deleted, if the active
		 * agent is the questionnaire's owner.
		 * 
		 * @param id
		 */
		@DELETE
		@Path("questionnaires/{id}")
		@ApiOperation(
				value = "deleteQuestionnaire",
				notes = "Requires authentication. Use parent resource to retrieve list of existing questionnaires.")
		@ApiResponses(
				value = { @ApiResponse(
						code = 200,
						message = "Questionnaire deleted successfully."),
						@ApiResponse(
								code = 401,
								message = "Questionnaire may only be deleted by its owner."),
						@ApiResponse(
								code = 404,
								message = "Questionnaire does not exist.") })
		public Response deleteQuestionnaire(@PathParam("id") int id) {

			if (Context.get().getMainAgent() instanceof AnonymousAgent) {
				return Response.status(Status.UNAUTHORIZED).entity("Please authenticate to delete questionnaire!")
						.build();
			}

			String onAction = "deleting questionnaire " + id;

			try {
				Connection c = null;
				PreparedStatement s = null;
				ResultSet rs = null;

				// +++ dsi
				try {

					// first check if questionnaire to be deleted exists and current agent is owner.
					int exown = checkExistenceOwnership(id, 1);

					// check if questionnaire exists; if not, return 404.
					if (exown == -1) {
						return Response.status(Status.NOT_FOUND).entity("Questionnaire " + id + " does not exist.")
								.build();
					}
					// if questionnaire exists, check if active agent is owner. if not, return 401.
					else if (exown == 0) {
						return Response.status(Status.UNAUTHORIZED)
								.entity("Questionnaire " + id + " may only be deleted by its owner!").build();
					}

					// Proceed, iff questionnaire exists and active agent is owner.
					c = dataSource.getConnection();
					s = c.prepareStatement("delete from " + service.jdbcSchema + ".questionnaire where id = ?");
					s.setInt(1, id);

					s.executeUpdate();
					return Response.status(Status.OK).entity("Questionnaire " + id + " deleted successfully.").build();

				} catch (Exception e) {
					e.printStackTrace();
					return internalError(onAction);
				} finally {
					try {
						if (rs != null) {
							rs.close();
						}
					} catch (Exception e) {
						e.printStackTrace();
						return internalError(onAction);
					}
					try {
						if (s != null) {
							s.close();
						}
					} catch (Exception e) {
						e.printStackTrace();
						return internalError(onAction);
					}
					try {
						if (c != null) {
							c.close();
						}
					} catch (Exception e) {
						e.printStackTrace();
						return internalError(onAction);
					}
				}
				// --- dsi

			} catch (Exception e) {
				e.printStackTrace();
				return internalError(onAction);
			}
		}

		/**
		 * TODO: write documentation
		 * 
		 * @param id
		 * @return
		 */

		@GET
		@Produces(MediaType.TEXT_XML)
		@Path("questionnaires/{id}/form")
		@ApiOperation(
				value = "downloadQuestionnaireForm",
				notes = "Use parent resource to retrieve list of existing surveys. Response body contains XML document compliant with the MobSOS Surveys XML Schema.")
		@ApiResponses(
				value = { @ApiResponse(
						code = 200,
						message = "Questionnaire form data."),
						@ApiResponse(
								code = 404,
								message = "Questionnaire does not exist -or- Questionnaire does not define a form.") })
		public Response downloadQuestionnaireForm(@PathParam("id") int id) {

			String onAction = "downloading form for questionnaire " + id;

			try {
				Connection conn = null;
				PreparedStatement stmt = null;
				ResultSet rset = null;

				try {

					// check if questionnaire exists; if not, return 404.
					int exown = checkExistenceOwnership(id, 1);

					// check if questionnaire exists; if not, return 404.
					if (exown == -1) {
						return Response.status(Status.NOT_FOUND).entity("Questionnaire " + id + " does not exist.")
								.build();
					}

					// if questionnaire exists, retrieve form
					conn = dataSource.getConnection();
					stmt = conn
							.prepareStatement("select form from " + service.jdbcSchema + ".questionnaire where id = ?");
					stmt.setInt(1, id);

					ResultSet rs = stmt.executeQuery();
					rs.next();
					String formXml = rs.getString(1);

					// if form field is empty, respond with not found.
					if (formXml == null || formXml.trim().isEmpty()) {
						return Response.status(Status.NOT_FOUND)
								.entity("Questionnaire " + id + " does not define a form!").build();
					}

					// before returning form, make sure it's still valid (TODO: think about removing check after
					// testing)
					try {
						validateQuestionnaireData(formXml);
						return Response.status(Status.OK).entity(formXml).build();
					} catch (IOException e) {
						e.printStackTrace();
						return internalError(onAction);

					} catch (SAXException e) {
						e.printStackTrace();
						return Response.status(Status.BAD_REQUEST)
								.entity("Questionnaire form is invalid! Cause: " + e.getMessage()).build();
					}

				} catch (SQLException | UnsupportedOperationException e) {
					return internalError(onAction);
				} finally {
					try {
						if (rset != null) {
							rset.close();
						}
					} catch (Exception e) {
						e.printStackTrace();
						return internalError(onAction);
					}
					try {
						if (stmt != null) {
							stmt.close();
						}
					} catch (Exception e) {
						e.printStackTrace();
						return internalError(onAction);
					}
					try {
						if (conn != null) {
							conn.close();
						}
					} catch (Exception e) {
						e.printStackTrace();
						return internalError(onAction);
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
				return internalError(onAction);
			}
		}

		/**
		 * TODO: write documentation
		 * 
		 * @param id
		 * @param formXml
		 * @return
		 */
		@PUT
		@Consumes(MediaType.TEXT_XML)
		@Path("questionnaires/{id}/form")
		@ApiOperation(
				value = "uploadQuestionnaireForm",
				notes = "Requires authentication and ownership of questionnaire.")
		@ApiResponses(
				value = { @ApiResponse(
						code = 200,
						message = "Questionnaire form upload successful."),
						@ApiResponse(
								code = 400,
								message = "Questionnaire form data invalid."),
						@ApiResponse(
								code = 401,
								message = "Questionnaire form may only be uploaded by owner. -or- Questionnaire form upload requires authentication."),
						@ApiResponse(
								code = 404,
								message = "Questionnaire does not exist.") })
		public Response uploadQuestionnaireForm(@PathParam("id") int id, String formXml) {

			if (Context.get().getMainAgent() instanceof AnonymousAgent) {
				return Response.status(Status.UNAUTHORIZED).entity("Please authenticate to upload questionnaire!")
						.build();
			}

			String onAction = "uploading form for questionnaire " + id;

			try {
				Connection conn = null;
				PreparedStatement stmt = null;
				ResultSet rset = null;

				try {

					//
					int exown = checkExistenceOwnership(id, 1);

					// check if questionnaire exists; if not, return 404.
					if (exown == -1) {
						return Response.status(Status.NOT_FOUND).entity("Questionnaire " + id + " does not exist.")
								.build();
					}
					// if questionnaire exists, check if active agent is owner. if not, return 401.
					else if (exown == 0) {
						return Response.status(Status.UNAUTHORIZED)
								.entity("Form for questionnaire " + id + "  may only be uploaded by its owner.")
								.build();
					}

					// before storing to database validate questionnaire form
					try {
						// validate form XML against MobSOS Survey XML Schema. Since the schema also defines valid
						// responses, a next check
						// is needed to make sure the passed and valid XML is a questionnaire form, and not a response.
						Document form = validateQuestionnaireData(formXml);

						if (!form.getDocumentElement().getNodeName().equals("qu:Questionnaire")) {
							return Response.status(Status.BAD_REQUEST)
									.entity("Document is not a questionnaire form! Cause: Document element must be 'qu:Questionnaire'.")
									.build();
						}

						// String lang = form.getDocumentElement().getAttribute("xml:lang");
						// System.out.println("Language detected: " + lang);

					} catch (SAXException e) {
						return Response.status(Status.BAD_REQUEST)
								.entity("Questionnaire form is invalid! Cause: " + e.getMessage()).build();
					}

					// store valid form to database
					conn = dataSource.getConnection();
					stmt = conn.prepareStatement(
							"update " + service.jdbcSchema + ".questionnaire set form=? where id = ?");

					stmt.setString(1, formXml);
					stmt.setInt(2, id);
					stmt.executeUpdate();

					// respond to user
					return Response.status(Status.OK).entity("Form upload for questionnaire " + id + " successful.")
							.build();

				} catch (SQLException | UnsupportedOperationException e) {
					return internalError(onAction);
				} finally {
					try {
						if (rset != null) {
							rset.close();
						}
					} catch (Exception e) {
						e.printStackTrace();
						return internalError(onAction);
					}
					try {
						if (stmt != null) {
							stmt.close();
						}
					} catch (Exception e) {
						e.printStackTrace();
						return internalError(onAction);
					}
					try {
						if (conn != null) {
							conn.close();
						}
					} catch (Exception e) {
						e.printStackTrace();
						return internalError(onAction);
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
				return internalError(onAction);
			}
		}

		// ============= SURVEY-RELATED RESOURCES ==============

		@GET
		@Produces(MediaType.TEXT_HTML)
		@Path("surveys")
		public Response getSurveysHTML(@HeaderParam("accept-language") @DefaultValue("en-US") String lang) {

			String onAction = "retrieving surveys HTML";

			// only respond with template; nothing to be adapted
			try {
				// load template from file
				Scanner scanner = new Scanner(new File("./etc/html/surveys-template.html"));
				String html = scanner.useDelimiter("\\A").next();
				scanner.close();

				// localize template
				html = i18n(html, lang);

				// fill in placeholders
				html = fillPlaceHolder(html, "EP_URL", service.epUrl);
				html = fillPlaceHolder(html, "L2P_URL", service.las2peerUrl);
				html = fillPlaceHolder(html, "SC_URL", service.staticContentUrl);
				html = fillPlaceHolder(html, "OIDC_PROV_NAME", service.oidcProviderName);
				html = fillPlaceHolder(html, "OIDC_PROV_LOGO", service.oidcProviderLogo);
				html = fillPlaceHolder(html, "OIDC_PROV_URL", service.oidcProviderUrl);
				html = fillPlaceHolder(html, "OIDC_CLNT_ID", service.oidcClientId);

				// finally return resulting HTML
				return Response.status(Status.OK).entity(html).build();
			} catch (FileNotFoundException e) {
				return internalError(onAction);
			}
		}

		/**
		 * TODO: write documentation Retrieves a list of all surveys.
		 * 
		 * @return
		 */
		/**
		 * TODO: write documentation
		 * 
		 * @param full
		 * @param query
		 * @return
		 */
		@SuppressWarnings("unchecked")
		@GET
		@Produces(MediaType.APPLICATION_JSON)
		@Path("surveys")
		@ApiOperation(
				value = "getSurveys",
				notes = "Query parameter matches questionnaire name, description.")
		@ApiResponses(
				value = { @ApiResponse(
						code = 200,
						message = "Questionnaires data (TODO: introduce Swagger models)"), })
		public Response getSurveys(@QueryParam("full") @DefaultValue("1") int full,
				@QueryParam("q") @DefaultValue("") String query) {

			String onAction = "retrieving surveys";

			try {
				JSONObject r = new JSONObject(); // result to return in HTTP response
				JSONArray qs = new JSONArray(); // variable for collecting surveys from DB

				Connection c = null;
				PreparedStatement s = null;
				ResultSet rs = null;

				// use query for survey id per default
				String sQuery = "select id from survey where name like ? or description like ? or organization like ?";

				// if query parameter full is provided greater 0, then use query for full questionnaire data set.
				if (full > 0) {
					sQuery = "select * from survey where name like ? or description like ? or organization like ? order by name";
				}

				try {
					c = dataSource.getConnection();
					s = c.prepareStatement(sQuery);
					s.setString(1, "%" + query + "%");
					s.setString(2, "%" + query + "%");
					s.setString(3, "%" + query + "%");

					rs = s.executeQuery();

					// in case result set is empty...
					if (!rs.isBeforeFirst()) {
						r.put("surveys", qs);
						return Response.status(Status.OK).entity(r.toJSONString()).build();
					}

					// in case result set contains entries...
					while (rs.next()) {
						if (full > 0) {
							JSONObject survey = readSurveyFromResultSet(rs);
							survey.put("url", service.epUrl + "surveys/" + survey.get("id"));
							qs.add(survey);
						} else {
							String id = rs.getString("id");
							qs.add(service.epUrl + "surveys/" + id);
						}
					}

				} catch (Exception e) {
					e.printStackTrace();
					return internalError(onAction);
				} finally {
					try {
						if (rs != null) {
							rs.close();
						}
					} catch (Exception e) {
						e.printStackTrace();
						return internalError(onAction);
					}
					try {
						if (s != null) {
							s.close();
						}
					} catch (Exception e) {
						e.printStackTrace();
						return internalError(onAction);
					}
					try {
						if (c != null) {
							c.close();
						}
					} catch (Exception e) {
						e.printStackTrace();
						return internalError(onAction);
					}
				}

				r.put("surveys", qs);
				return Response.status(Status.OK).entity(r.toJSONString()).build();

			} catch (Exception e) {
				e.printStackTrace();
				return internalError(onAction);
			}
		}

		/**
		 * TODO: write documentation
		 * 
		 * @param data
		 * 
		 */
		@SuppressWarnings("unchecked")
		@POST
		@Consumes(MediaType.APPLICATION_JSON)
		@Produces(MediaType.APPLICATION_JSON)
		@Path("surveys")
		@ApiOperation(
				value = "createSurvey",
				notes = "Requires authentication.")
		@ApiResponses(
				value = { @ApiResponse(
						code = 201,
						message = "Survey URL & ID (TODO: introduce Swagger models)"),
						@ApiResponse(
								code = 400,
								message = "Survey data invalid."),
						@ApiResponse(
								code = 401,
								message = "Survey creation requires authentication."),
						@ApiResponse(
								code = 409,
								message = "Survey already exists.") })
		public Response createSurvey(String data) {
			if (Context.get().getMainAgent() instanceof AnonymousAgent) {
				return Response.status(Status.UNAUTHORIZED).entity("Please authenticate to create surveys!").build();
			}

			String onAction = "creating new survey";

			try {
				JSONObject o;

				// first parse survey data passed by user
				try {
					o = parseSurvey(data);
				} catch (IllegalArgumentException e) {
					// if passed content is invalid for some reason, notify user
					System.out.println("Invalid survey data! ");
					return Response.status(Status.BAD_REQUEST).entity("Invalid survey data! " + e.getMessage()).build();
				}

				try {
					// if passed content is valid, store as new survey
					int sid = storeNewSurvey(o);
					System.out.println("Stored survey: " + sid);
					// respond to user with newly generated survey id/URL
					JSONObject r = new JSONObject();
					r.put("id", sid);
					r.put("url", service.epUrl + "surveys/" + sid);

					return Response.status(Status.CREATED).entity(r.toJSONString()).build();
				} catch (SQLException e) {

					if (0 <= e.getMessage().indexOf("Duplicate")) {
						System.out.println("Survey already exists");
						return Response.status(Status.CONFLICT).entity("Survey already exists").build();
					} else {
						e.printStackTrace();
						return internalError(onAction);
					}
				}

			} catch (Exception e) {
				e.printStackTrace();
				return internalError(onAction);
			}
		}

		/**
		 * TODO: write documentation with clear warning!!!! Deletes all surveys at once without any check for ownership.
		 * This method should be only be used for development and with absolute caution!
		 */
		@DELETE
		@Path("surveys")
		public Response deleteSurveys() {

			if (Context.get().getMainAgent() instanceof AnonymousAgent) {
				return Response.status(Status.UNAUTHORIZED).entity("Please authenticate to delete surveys!").build();
			}

			String onAction = "deleting surveys";

			try {
				Connection c = null;
				PreparedStatement s = null;
				ResultSet rs = null;

				// +++ dsi
				try {
					c = dataSource.getConnection();
					s = c.prepareStatement("delete from " + service.jdbcSchema + ".survey");

					s.executeUpdate();
					return Response.status(Status.OK).entity("").build();

				} catch (Exception e) {
					e.printStackTrace();
					return internalError(onAction);
				} finally {
					try {
						if (rs != null) {
							rs.close();
						}
					} catch (Exception e) {
						e.printStackTrace();
						return internalError(onAction);
					}
					try {
						if (s != null) {
							s.close();
						}
					} catch (Exception e) {
						e.printStackTrace();
						return internalError(onAction);
					}
					try {
						if (c != null) {
							c.close();
						}
					} catch (Exception e) {
						e.printStackTrace();
						return internalError(onAction);
					}
				}
				// --- dsi

			} catch (Exception e) {
				e.printStackTrace();
				return internalError(onAction);
			}
		}

		/**
		 * TODO: write documentation Retrieves information for a given survey
		 * 
		 * @param id
		 * @return
		 */
		@GET
		@Produces(MediaType.APPLICATION_JSON)
		@Path("surveys/{id}")
		@ApiOperation(
				value = "getSurvey",
				notes = "Use <b>/surveys</b> to retrieve list of existing surveys.")
		@ApiResponses(
				value = { @ApiResponse(
						code = 200,
						message = "Survey data (TODO: introduce Swagger models)"),
						@ApiResponse(
								code = 404,
								message = "Survey does not exist.") })
		public Response getSurvey(@PathParam("id") int id) {

			String onAction = "retrieving survey " + id;

			try {
				JSONObject r = new JSONObject(); // result to return in HTTP response

				Connection c = null;
				PreparedStatement s = null;
				ResultSet rs = null;

				// +++ dsi
				try {
					// query for given survey
					c = dataSource.getConnection();

					// TODO: restore, as soon as resource information comes from an external source
					s = c.prepareStatement("select * from " + service.jdbcSchema + ".survey where id = ?");

					// TODO: replace by external source for retrieving resource information
					// s = c.prepareStatement("select s.*, r.name as rname, r.description as rdesc from " + jdbcSchema +
					// ".survey s left join " + jdbcSchema + ".resource r on (s.resource = r.uri) where id = ?");
					s.setInt(1, id);

					rs = s.executeQuery();

					// if survey does not exist, respond to user with not found
					if (!rs.isBeforeFirst()) {
						return Response.status(Status.NOT_FOUND).entity("Survey " + id + " does not exist!").build();
					}

					// if survey was found, respond to user with JSON result
					rs.next();
					r = readSurveyFromResultSet(rs);

					// TODO: replace by external resource information
					// String resource_name = rs.getString("rname");
					// String resource_description = rs.getString("rdesc");

					// String resource_uri = (String) r.get("resource");

					// JSONObject res = new JSONObject();
					// res.put("uri",resource_uri);
					// res.put("name", resource_name);
					// res.put("description", resource_description);

					// r.put("resource", res);

					// before, try to retrieve information on resource

					return Response.status(Status.OK).entity(r.toJSONString()).build();

				} catch (Exception e) {
					e.printStackTrace();
					return internalError(onAction);
				} finally {
					try {
						if (rs != null) {
							rs.close();
						}
					} catch (Exception e) {
						e.printStackTrace();
						return internalError(onAction);
					}
					try {
						if (s != null) {
							s.close();
						}
					} catch (Exception e) {
						e.printStackTrace();
						return internalError(onAction);
					}
					try {
						if (c != null) {
							c.close();
						}
					} catch (Exception e) {
						e.printStackTrace();
						return internalError(onAction);
					}
				}
				// --- dsi

			} catch (Exception e) {
				e.printStackTrace();
				return internalError(onAction);
			}

		}

		@GET
		@Produces(MediaType.TEXT_HTML)
		@Path("surveys/{id}")
		public Response getSurveyHTML(@HeaderParam("accept-language") String lang, @PathParam("id") int id) {
			String onAction = "retrieving individual survey HTML";

			try {
				// if survey does not exist, return 404.
				if (checkExistenceOwnership(id, 0) == -1) {
					return Response.status(Status.NOT_FOUND).entity("Survey does not exist!").build();
				}
			} catch (Exception e1) {
				return internalError(onAction);
			}

			// adapt template to specific survey
			try {
				Scanner scanner = new Scanner(new File("./etc/html/survey-id-template.html"));
				String html = scanner.useDelimiter("\\A").next();
				scanner.close();
				// localize template
				html = i18n(html, lang);

				// fill in placeholders with concrete values
				html = fillPlaceHolder(html, "ID", "" + id);
				html = fillPlaceHolder(html, "EP_URL", service.epUrl);
				html = fillPlaceHolder(html, "L2P_URL", service.las2peerUrl);
				html = fillPlaceHolder(html, "SC_URL", service.staticContentUrl);
				html = fillPlaceHolder(html, "OIDC_PROV_NAME", service.oidcProviderName);
				html = fillPlaceHolder(html, "OIDC_PROV_LOGO", service.oidcProviderLogo);
				html = fillPlaceHolder(html, "OIDC_PROV_URL", service.oidcProviderUrl);
				html = fillPlaceHolder(html, "OIDC_CLNT_ID", service.oidcClientId);

				// finally return resulting HTML
				return Response.status(Status.OK).entity(html).build();
			} catch (FileNotFoundException e) {
				return internalError(onAction);
			}
		}

		/**
		 * TODO: write documentation Updates a survey with a given id. The respective survey may only be deleted, if the
		 * active agent is the survey's owner.
		 * 
		 * @param id
		 * @return
		 */
		@PUT
		@Consumes(MediaType.APPLICATION_JSON)
		@Path("surveys/{id}")
		@ApiOperation(
				value = "updateSurvey",
				notes = "Requires authentication. Use parent resource to retrieve list of existing surveys.")
		@ApiResponses(
				value = { @ApiResponse(
						code = 200,
						message = "Survey updated successfully."),
						@ApiResponse(
								code = 400,
								message = "Survey data invalid."),
						@ApiResponse(
								code = 401,
								message = "Survey may only be updated by its owner."),
						@ApiResponse(
								code = 404,
								message = "Survey does not exist.") })
		public Response updateSurvey(@PathParam("id") int id, String content) {

			if (Context.get().getMainAgent() instanceof AnonymousAgent) {
				return Response.status(Status.UNAUTHORIZED).entity("Please authenticate to update survey!").build();
			}

			String onAction = "updating survey " + id;

			try {
				Connection c = null;
				PreparedStatement s = null;
				ResultSet rs = null;

				// +++ dsi
				try {

					int exown;
					// survey may only be updated if survey exists and active agent is owner
					exown = checkExistenceOwnership(id, 0);

					// check if survey exists; if not, return 404.
					if (exown == -1) {
						return Response.status(Status.NOT_FOUND).entity("Survey " + id + " does not exist.").build();
					}
					// if survey exists, check if active agent is owner. if not, return 401.
					else if (exown == 0) {
						return Response.status(Status.UNAUTHORIZED)
								.entity("Survey " + id + " may only be deleted by its owner.").build();
					}

					// if survey exists and active agent is owner, proceed.

					JSONObject o;
					// parse and validate content. If invalid, return 400 (bad request)
					try {
						o = parseSurvey(content);
					} catch (IllegalArgumentException e) {
						return Response.status(Status.BAD_REQUEST).entity("Invalid survey data! " + e.getMessage())
								.build();
					}

					c = dataSource.getConnection();
					s = c.prepareStatement("update " + service.jdbcSchema
							+ ".survey set organization=?, logo=?, name=?, description=?, resource=?, start=?, end=?, lang=?, resource_label=? where id = ?");

					s.setString(1, (String) o.get("organization"));
					s.setString(2, (String) o.get("logo"));
					s.setString(3, (String) o.get("name"));
					s.setString(4, (String) o.get("description"));
					s.setString(5, (String) o.get("resource"));
					s.setTimestamp(6,
							new Timestamp(DatatypeConverter.parseDateTime((String) o.get("start")).getTimeInMillis()));
					s.setTimestamp(7,
							new Timestamp(DatatypeConverter.parseDateTime((String) o.get("end")).getTimeInMillis()));
					s.setString(8, (String) o.get("lang"));
					s.setString(9, (String) o.get("resource-label"));
					s.setInt(10, id);

					s.executeUpdate();

					return Response.status(Status.OK).entity("Survey " + id + " updated successfully.").build();

				} catch (Exception e) {
					e.printStackTrace();
					return internalError(onAction);
				} finally {
					try {
						if (rs != null) {
							rs.close();
						}
					} catch (Exception e) {
						e.printStackTrace();
						return internalError(onAction);
					}
					try {
						if (s != null) {
							s.close();
						}
					} catch (Exception e) {
						e.printStackTrace();
						return internalError(onAction);
					}
					try {
						if (c != null) {
							c.close();
						}
					} catch (Exception e) {
						e.printStackTrace();
						return internalError(onAction);
					}
				}
				// --- dsi

			} catch (Exception e) {
				e.printStackTrace();
				return internalError(onAction);
			}

		}

		/**
		 * TODO: write documentation Deletes a survey with a given id. The respective survey may only be deleted, if the
		 * active agent is the survey's owner.
		 * 
		 * @param id
		 */
		@DELETE
		@Path("surveys/{id}")
		@ApiOperation(
				value = "deleteSurvey",
				notes = "Requires authentication. Use parent resource to retrieve list of existing surveys.")
		@ApiResponses(
				value = { @ApiResponse(
						code = 200,
						message = "Survey deleted successfully."),
						@ApiResponse(
								code = 401,
								message = "Survey may only be deleted by its owner."),
						@ApiResponse(
								code = 404,
								message = "Survey does not exist.") })
		public Response deleteSurvey(@PathParam("id") int id) {

			if (Context.get().getMainAgent() instanceof AnonymousAgent) {
				return Response.status(Status.UNAUTHORIZED).entity("Please authenticate to delete survey!").build();
			}

			String onAction = "deleting survey " + id;

			try {
				Connection c = null;
				PreparedStatement s = null;
				ResultSet rs = null;

				// +++ dsi
				try {

					// first check if survey to be deleted exists and current agent is owner.
					int exown = checkExistenceOwnership(id, 0);

					// check if survey exists; if not, return 404.
					if (exown == -1) {
						return Response.status(Status.NOT_FOUND).entity("Survey " + id + " does not exist.").build();
					}
					// if survey exists, check if active agent is owner. if not, return 401.
					else if (exown == 0) {
						return Response.status(Status.UNAUTHORIZED)
								.entity("Survey " + id + " may only be deleted by its owner.").build();
					}

					// if survey exists and active agent is owner, perform deletion
					c = dataSource.getConnection();
					s = c.prepareStatement("delete from " + service.jdbcSchema + ".survey where id = ?");
					s.setInt(1, id);

					s.executeUpdate();

					// TODO: check return value of update to see if deletion really occurred
					// System.out.println("Result: " + r);

					return Response.status(Status.OK).entity("Survey " + id + " deleted successfully.").build();

				} catch (Exception e) {
					e.printStackTrace();
					return internalError(onAction);
				} finally {
					try {
						if (rs != null) {
							rs.close();
						}
					} catch (Exception e) {
						e.printStackTrace();
						return internalError(onAction);
					}
					try {
						if (s != null) {
							s.close();
						}
					} catch (Exception e) {
						e.printStackTrace();
						return internalError(onAction);
					}
					try {
						if (c != null) {
							c.close();
						}
					} catch (Exception e) {
						e.printStackTrace();
						return internalError(onAction);
					}
				}
				// --- dsi

			} catch (Exception e) {
				e.printStackTrace();
				return internalError(onAction);
			}
		}

		/**
		 * TODO: write documentation
		 * 
		 * @param id
		 * @return
		 */
		@GET
		@Produces(MediaType.TEXT_HTML)
		@Path("surveys/{id}/questionnaire")
		@ApiOperation(
				value = "getSurveyQuestionnaireFormHTML",
				notes = "Can be used with or without authentication, including response submission.")
		@ApiResponses(
				value = { @ApiResponse(
						code = 200,
						message = "Survey questionnaire HTML representation."),
						@ApiResponse(
								code = 400,
								message = "Survey questionnaire form invalid. Cause: ..."),
						@ApiResponse(
								code = 404,
								message = "Questionnaire does not exist. <b>-or-</b> Survey questionnaire not set. <b>-or-</b> Survey questionnaire does not define form.") })
		public Response getSurveyQuestionnaireFormHTML(
				@HeaderParam("accept-language") @DefaultValue("en-US") String lang, @PathParam("id") int id) {

			String onAction = "downloading questionnaire form for survey " + id;

			try {
				// if survey does not exist, return 404.
				if (checkExistenceOwnership(id, 0) == -1) {
					return Response.status(Status.NOT_FOUND).entity("Survey does not exist!").build();
				}
			} catch (Exception e1) {
				return internalError(onAction);
			}

			Connection conn = null;
			PreparedStatement stmt = null;
			ResultSet rset = null;
			String formXml;

			// -----------------

			try {

				// retrieve survey data; if survey does not exist, return 404.
				Response r = getSurvey(id);
				if (200 != r.getStatus()) {
					System.err.println(r.getEntity());
					return r;
				}

				JSONObject survey = (JSONObject) JSONValue.parse((String) r.getEntity());

				// check if survey has the questionnaire id field qid set. If not, return not found.
				if (null == survey.get("qid")) {
					return Response.status(Status.NOT_FOUND).entity("Questionnaire not set for survey " + id + ".")
							.build();
				}

				// if questionnaire was found, download questionnaire form
				long qid = (Long) survey.get("qid");

				try {
					conn = dataSource.getConnection();
					stmt = conn
							.prepareStatement("select form from " + service.jdbcSchema + ".questionnaire where id = ?");
					stmt.setLong(1, qid);
					ResultSet rs = stmt.executeQuery();

					// if no form was uploaded for questionnaire, respond to user with not found
					if (!rs.isBeforeFirst()) {
						return Response.status(Status.NOT_FOUND)
								.entity("Form for questionnaire " + qid + " does not exist!").build();
					}

					rs.next();

					formXml = rs.getString(1);

				} catch (SQLException | UnsupportedOperationException e) {
					return internalError(onAction);
				} finally {
					try {
						if (rset != null) {
							rset.close();
						}
					} catch (Exception e) {
						e.printStackTrace();
						return internalError(onAction);
					}
					try {
						if (stmt != null) {
							stmt.close();
						}
					} catch (Exception e) {
						e.printStackTrace();
						return internalError(onAction);
					}
					try {
						if (conn != null) {
							conn.close();
						}
					} catch (Exception e) {
						e.printStackTrace();
						return internalError(onAction);
					}
				}

				// adapt form template to concrete survey and user
				String adaptedFormXml = adaptForm(formXml, survey, Context.getCurrent().getMainAgent(),
						null);

				// String adaptedFormXml = formXml;

				Document form;
				// before returning form, make sure it's still valid (may be obsolete step...)
				try {
					form = validateQuestionnaireData(adaptedFormXml);
				} catch (IOException e) {
					e.printStackTrace();
					return internalError(onAction);
				} catch (SAXException e) {
					e.printStackTrace();
					return Response.status(Status.BAD_REQUEST)
							.entity("Questionnaire form is invalid! Cause: " + e.getMessage()).build();
				}

				// now start to transform XML into ready-to-use HTML

				// start off with template
				Scanner scanner = new Scanner(new File("./etc/html/survey-form-template.html"));
				String html = scanner.useDelimiter("\\A").next();
				scanner.close();

				// fill in placeholders
				html = fillPlaceHolder(html, "EP_URL", service.epUrl);
				html = fillPlaceHolder(html, "L2P_URL", service.las2peerUrl);
				html = fillPlaceHolder(html, "SC_URL", service.staticContentUrl);
				html = fillPlaceHolder(html, "OIDC_PROV_NAME", service.oidcProviderName);
				html = fillPlaceHolder(html, "OIDC_PROV_LOGO", service.oidcProviderLogo);
				html = fillPlaceHolder(html, "OIDC_PROV_URL", service.oidcProviderUrl);
				html = fillPlaceHolder(html, "OIDC_CLNT_ID", service.oidcClientId);

				// do all adaptation to user and survey
				String adaptHtml = adaptForm(html, survey, Context.getCurrent().getMainAgent(), null);

				adaptHtml = i18n(adaptHtml, lang);

				// String adaptText = text;

				// add HTML elements for all questionnaire items accordingly
				Vector<String> qpages = new Vector<>();
				Vector<String> navpills = new Vector<>();

				NodeList nodeList = form.getElementsByTagNameNS(MOBSOS_QUESTIONNAIRE_NS, "Page");

				// then iterate over all question pages
				for (int i = 0; i < nodeList.getLength(); i++) {

					Node node = nodeList.item(i);
					if (node.getNodeType() == Node.ELEMENT_NODE) {
						Element e = (Element) node;

						// set first page and navpill item to active
						String active = "";

						if (i == 0) {
							active = " class='active'";
						}

						// differentiate between possible item types and add HTML accordingly
						if (e.getAttribute("xsi:type").endsWith("InformationPageType")) {
							// first add navpill item
							String navpill = "\t\t\t\t\t<li" + active + "><a href=\"#step-" + i
									+ "\"><span class=\"list-group-item-heading\">" + i + "</span></a></li>\n";
							navpills.add(navpill);

							// then add information page
							String qpage = "\t\t<div class=\"row setup-content\" id=\"step-" + i
									+ "\"><div class=\"col-xs-12\"><div class=\"col-md-12 well text-center\">\n";

							String name = e.getAttribute("name");

							qpage += "\t\t\t<h4><b>" + name + "</b></h4>\n";

							String instr = escapeHtml4(e.getElementsByTagNameNS(MOBSOS_QUESTIONNAIRE_NS, "Instructions")
									.item(0).getTextContent().trim());

							qpage += "\t\t\t<p>\n\t\t\t\t" + instr + "\n" + "\t\t\t</p>\n";
							qpage += "\t\t</div></div></div>\n";
							qpages.add(qpage);

						} else if (e.getAttribute("xsi:type").endsWith("QuestionPageType")) {

							// first add nav pill item
							String navpill = "\t\t\t\t\t<li" + active + "><a href=\"#step-" + i
									+ "\"><span class=\"list-group-item-heading\">" + i + "</span></a></li>\n";
							navpills.add(navpill);

							// then add question page
							String qpage = "\t\t<div class=\"row setup-content\" id=\"step-" + i
									+ "\"><div class=\"col-xs-12\"><div class=\"col-md-12 text-center\">\n";

							String name = e.getAttribute("name");
							String quid = e.getAttribute("qid");

							qpage += "\t\t\t<h4><b>" + name + " (" + quid + ")</b></h4>\n";

							String instr = escapeHtml4(e.getElementsByTagNameNS(MOBSOS_QUESTIONNAIRE_NS, "Instructions")
									.item(0).getTextContent());

							String cssClass = "question";

							if (e.getAttribute("required") != null && e.getAttribute("required").equals("true")) {
								cssClass += " required";
								instr += " (<i>" + i18n("${required}", lang) + "</i>)";
							}

							qpage += "\t\t\t<div class=\"" + cssClass + "\" style='text-align: justify; white-space: pre-line;'>" + instr
									+ "</div><p/>\n";

							String qtype = e.getAttribute("xsi:type");

							if ("qu:OrdinalScaleQuestionPageType".equals(qtype)) {

								// TODO: do something with default value, if set.
								// int defval = Integer.parseInt(e.getAttribute("defval"));
								String minlabel = escapeHtml4(e.getAttribute("minlabel"));
								String maxlabel = escapeHtml4(e.getAttribute("maxlabel"));
								int minval = Integer.parseInt(e.getAttribute("minval"));
								int maxval = Integer.parseInt(e.getAttribute("maxval"));

								// do UI in a button style (not really nice in terms of responsive design)
								/*
								qpage += "\t\t\t<div class=\"btn-group\" data-toggle=\"buttons\">\n";
								qpage += "\t\t\t\t<span class=\"btn\">" + minlabel + "</span>\n";
								for(int k=minval;k<=maxval;k++){
									qpage += "\t\t\t\t<label class=\"btn btn-primary\">\n";
									qpage += "\t\t\t\t\t<input name=\""+ quid + "\" type=\"radio\" value=\""+k + "\">" + k + "\n";
									qpage += "\t\t\t\t</label>\n";
								}
								qpage += "\t\t\t\t<span class=\"btn\">" + maxlabel + "</span>\n";
								qpage += "\t\t\t</div>\n";
								 */
								// --- end UI button style

								// do UI in range slider style (better responsive design)
								/*
								<div class="row well">
								
										<input class="col-md-12 col-xs-12 scale" name="SQ.N.1" type="range" min="1" max="7" step="1" list="SQ.N.1-scale"/><br>
										<datalist id="SQ.N.1-scale">
											<option>1</option>
											<option>2</option>
											<option>3</option>
											<option>4</option>
											<option>5</option>
											<option>6</option>
											<option>7</option>
										</datalist>
								
										<span class="col-md-4 col-xs-4">Totally disagree</span><span name="SQ.N.1" class="col-md-4 col-xs-4 text-center h4 response scale-response alert" data-toggle="tooltip" data-placement="left" title="Click to reset to n/a.">n/a</span> <span class="col-md-4 col-xs-4 pull-right text-right">Totally agree</span>
								
								</div>*/
								qpage += "\t\t\t<div class='row well'>\n";
								qpage += "\t\t\t\t<input class='col-md-12 col-xs-12 scale' name='" + quid
										+ "' type='range' min='" + minval + "' max='" + maxval + "' step='1' list='"
										+ quid.replace(".", "-") + "-scale'/><br>\n";
								qpage += "\t\t\t\t<datalist id='" + quid.replace(".", "-") + "-scale'>\n";
								for (int k = minval; k <= maxval; k++) {
									qpage += "\t\t\t\t\t<option>" + k + "</option>\n";
								}
								qpage += "\t\t\t\t</datalist>";
								qpage += "<span class='col-md-4 col-xs-5 text-left'>" + minlabel + "</span><span name='"
										+ quid
										+ "' class='col-md-4 col-xs-2 text-center h2 response scale-response' data-toggle='tooltip' data-placement='left' title='Click to reset to n/a.'>n/a</span> <span class='col-md-4 col-xs-5 pull-right text-right'>"
										+ maxlabel + "</span>";
								qpage += "\t\t\t</div>\n";
								// --- end UI range slider style

							} else if ("qu:DichotomousQuestionPageType".equals(qtype)) {

								// TODO: do something with default value, if set.
								// int defval = Integer.parseInt(e.getAttribute("defval"));
								String minlabel = e.getAttribute("minlabel");
								String maxlabel = e.getAttribute("maxlabel");

								qpage += "\t\t\t<div class=\"btn-group\" data-toggle=\"buttons\">\n";
								qpage += "\t\t\t\t<label class=\"btn btn-primary\">\n";
								qpage += "\t\t\t\t\t<input name=\"" + quid + "\" type=\"radio\" value=\"0\">" + minlabel
										+ "\n";
								qpage += "\t\t\t\t</label>\n";
								qpage += "\t\t\t\t<label class=\"btn btn-primary\">\n";
								qpage += "\t\t\t\t\t<input name=\"" + quid + "\" type=\"radio\" value=\"1\">" + maxlabel
										+ "\n";
								qpage += "\t\t\t\t</label>\n";
								qpage += "\t\t\t\t<input type=\"hidden\" class='response' name=\"" + quid + "\">\n";
								qpage += "\t\t\t</div>\n";

							} else if ("qu:FreeTextQuestionPageType".equals(qtype)) {
								qpage += "\t\t\t<textarea name=\"" + quid
										+ "\" class=\"form-control response freetext-response\" rows=\"3\"></textarea>\n";
							}

							qpage += "\t\t</div></div></div>\n";
							qpages.add(qpage);
						}
					}
				}

				// now that all questions are extracted and transformed to HTML, append nav pill items and question
				// pages to
				// final HTML.

				// first serialize nav pill items
				String navpillItems = "";
				for (int j = 0; j < navpills.size(); j++) {
					navpillItems += navpills.get(j);
				}

				// then serialize question page divs
				String questionDivs = "";
				for (int j = 0; j < qpages.size(); j++) {
					questionDivs += qpages.get(j);
				}

				// then generate answer link
				URL answerUrl = new URL(service.epUrl + "surveys/" + id + "/answers");
				String answerLink = "<a href=\"" + answerUrl + "\" id=\"return-url\" class=\"hidden\" ></a>";

				// finally insert all generated parts into the resulting adapted HTML
				adaptHtml = adaptHtml.replaceAll("<!-- NAVPILLS -->", navpillItems);
				adaptHtml = adaptHtml.replaceAll("<!-- QUESTIONPAGES -->", questionDivs);
				adaptHtml = adaptHtml.replaceAll("<!-- ANSWERLINK -->", answerLink);

				// return adapted HTML
				return Response.status(Status.OK).entity(adaptHtml).build();

			} catch (Exception e) {
				e.printStackTrace();
				return internalError(onAction);
			}

		}

		/**
		 * TODO: write documentation
		 * 
		 * @param id
		 * @param content
		 * @return
		 */
		@POST
		@Consumes(MediaType.APPLICATION_JSON)
		@Path("surveys/{id}/questionnaire")
		@ApiResponses(
				value = { @ApiResponse(
						code = 200,
						message = "Survey questionnaire set."),
						@ApiResponse(
								code = 400,
								message = "Invalid JSON for setting survey questionnaire."),
						@ApiResponse(
								code = 404,
								message = "Survey or questionnaire to be set does not exist.") })
		public Response setSurveyQuestionnaire(@PathParam("id") int id, String content) {

			if (Context.get().getMainAgent() instanceof AnonymousAgent) {
				return Response.status(Status.UNAUTHORIZED)
						.entity("Please authenticate to set questionnaire for survey!").build();
			}

			String onAction = "setting questionnaire for survey " + id;

			try {
				Connection conn = null;
				PreparedStatement stmt = null;
				ResultSet rset = null;

				try {

					int exown;
					exown = checkExistenceOwnership(id, 0);

					// check if survey exists; if not, return 404.
					if (exown == -1) {
						return Response.status(Status.NOT_FOUND).entity("Survey " + id + " does not exist.").build();
					}
					// if survey exists, check if active agent is owner. if not, return 401.
					else if (exown == 0) {
						return Response.status(Status.UNAUTHORIZED)
								.entity("Survey " + id + " may only be managed by its owner.").build();
					}

					// if survey exists and active agent is owner, proceed.

					JSONObject o;

					// parse and validate content. If invalid, return 400 (bad request)
					try {
						o = (JSONObject) JSONValue.parseWithException(content);
					} catch (ParseException e) {
						return Response.status(Status.BAD_REQUEST).entity(e.getMessage()).build();
					}

					if (!(o.size() == 1 && o.keySet().contains("qid"))) {
						return Response.status(Status.BAD_REQUEST)
								.entity("Invalid JSON for setting questionnaire! Must only contain one field qid!")
								.build();
					}

					// now check if questionnaire really exists
					int qid = Integer.parseInt(o.get("qid") + "");
					Response qresp = getQuestionnaire(qid);

					if (qresp.getStatus() != 200) {
						return qresp;
					}

					// if questionnaire exists, check if questionnaire form is defined already
					// if no form is defined, yet, return a not found.
					Response qformresp = downloadQuestionnaireForm(qid);
					if (qformresp.getStatus() == 404) {
						return qformresp;
					}

					// TODO: at this point we need to check, if users already submitted responses. What to do in this
					// case
					// to avoid data loss?
					// Responses should under no circumstances be deleted! Idea: respond with a forbidden error that
					// asks
					// user to first clear all
					// responses before changing questionnaire. Requires DELETE support in resource
					// surveys/{id}/responses.

					int responses = countResponses(id);

					if (responses > 0) {
						String msg = "Forbidden to change questionnaire, because end-user responses exist! "
								+ "To resolve this problem, first make sure to export existing survey response data. "
								+ "Then delete existing responses data with a DELETE to resource surveys/" + id
								+ "/responses." + "Then try again to change questionnaire.";
						return Response.status(Status.FORBIDDEN).entity(msg).build();
					}

					// if no responses are available, continue and change questionnaire
					conn = dataSource.getConnection();
					stmt = conn.prepareStatement("update " + service.jdbcSchema + ".survey set qid=? where id =?");

					stmt.setInt(1, qid);
					stmt.setInt(2, id);
					stmt.executeUpdate();

					return Response.status(Status.OK).entity("Questionnaire for survey " + id + " set successfully.")
							.build();

				} catch (SQLException | UnsupportedOperationException e) {
					return internalError(onAction);
				} finally {
					try {
						if (rset != null) {
							rset.close();
						}
					} catch (Exception e) {
						e.printStackTrace();
						return internalError(onAction);
					}
					try {
						if (stmt != null) {
							stmt.close();
						}
					} catch (Exception e) {
						e.printStackTrace();
						return internalError(onAction);
					}
					try {
						if (conn != null) {
							conn.close();
						}
					} catch (Exception e) {
						e.printStackTrace();
						return internalError(onAction);
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
				return internalError(onAction);
			}
		}

		@GET
		@Produces(MediaType.TEXT_HTML)
		@Path("surveys/{id}/responses")
		public Response getSurveyResponsesHTML(@HeaderParam("accept-language") @DefaultValue("en-US") String lang,
				@PathParam("id") int id) {

			String onAction = "retrieving responses HTML for survey " + id;

			try {
				// if survey does not exist, return 404.
				if (checkExistenceOwnership(id, 0) == -1) {
					return Response.status(Status.NOT_FOUND).entity("Survey does not exist!").build();
				}
			} catch (Exception e) {
				return internalError(onAction);
			}

			// adapt template to specific survey
			try {
				Scanner scanner = new Scanner(new File("./etc/html/survey-id-responses-template.html"));
				String html = scanner.useDelimiter("\\A").next();
				scanner.close();

				// localize template
				html = i18n(html, lang);

				// fill in placeholders with concrete values
				html = fillPlaceHolder(html, "ID", "" + id);
				html = fillPlaceHolder(html, "EP_URL", service.epUrl);
				html = fillPlaceHolder(html, "L2P_URL", service.las2peerUrl);
				html = fillPlaceHolder(html, "SC_URL", service.staticContentUrl);
				html = fillPlaceHolder(html, "OIDC_PROV_NAME", service.oidcProviderName);
				html = fillPlaceHolder(html, "OIDC_PROV_LOGO", service.oidcProviderLogo);
				html = fillPlaceHolder(html, "OIDC_PROV_URL", service.oidcProviderUrl);
				html = fillPlaceHolder(html, "OIDC_CLNT_ID", service.oidcClientId);

				// finally return resulting HTML
				return Response.status(Status.OK).entity(html).header("Access-Control-Allow-Origin", "*")
						.header("Access-Control-Allow-Headers", "Origin, X-Requested-With, Content-Type, Accept")
						.build();
			} catch (FileNotFoundException e) {
				return internalError(onAction);
			}
		}

		@GET
		@Produces(MediaType.TEXT_HTML)
		@Path("xml-generator")
		public Response getXmlGeneratorHTML(@HeaderParam("accept-language") @DefaultValue("en-US") String lang) {

			String onAction = "retrieving XML generator HTML";

			// adapt template to specific survey
			try {
				Scanner scanner = new Scanner(new File("./etc/html/xml-generator.html"));
				String html = scanner.useDelimiter("\\A").next();
				scanner.close();

				// localize template
				html = i18n(html, lang);

				// fill in placeholders with concrete values
				html = fillPlaceHolder(html, "EP_URL", service.epUrl);
				html = fillPlaceHolder(html, "L2P_URL", service.las2peerUrl);
				html = fillPlaceHolder(html, "SC_URL", service.staticContentUrl);
				html = fillPlaceHolder(html, "OIDC_PROV_NAME", service.oidcProviderName);
				html = fillPlaceHolder(html, "OIDC_PROV_LOGO", service.oidcProviderLogo);
				html = fillPlaceHolder(html, "OIDC_PROV_URL", service.oidcProviderUrl);
				html = fillPlaceHolder(html, "OIDC_CLNT_ID", service.oidcClientId);

				// finally return resulting HTML
				return Response.status(Status.OK).entity(html).header("Access-Control-Allow-Origin", "*")
						.header("Access-Control-Allow-Headers", "Origin, X-Requested-With, Content-Type, Accept")
						.build();
			} catch (FileNotFoundException e) {
				return internalError(onAction);
			}
		}

		/**
		 * TODO: write documentation
		 * 
		 * @param id
		 * @return
		 */
		@GET
		@Produces("text/csv")
		@Path("surveys/{id}/responses")
		@ApiOperation(
				value = "getSurveyResponses",
				notes = "Use resource <i>/surveys</i> to retrieve list of existing surveys.")
		@ApiResponses(
				value = { @ApiResponse(
						code = 200,
						message = "Survey response data in CSV format."),
						@ApiResponse(
								code = 404,
								message = "Survey does not exist -or- No questionnaire defined for survey.") })
		public Response getSurveyResponses(@PathParam("id") int id,
				@QueryParam("sepline") @DefaultValue("0") int sepline,
				@QueryParam("sep") @DefaultValue(",") String sep) {

			String onAction = "retrieving responses for survey " + id;

			try {
				int exown = checkExistenceOwnership(id, 0);

				// check if survey exists. If not, respond with not found.
				if (exown == -1) {
					return Response.status(Status.NOT_FOUND).entity("Survey " + id + " does not exist.").build();
				}

				// check if a questionnaire for survey is defined. If not, respond with not found.
				int qid = getQuestionnaireIdForSurvey(id);
				if (qid == -1) {
					return Response.status(Status.NOT_FOUND).entity("No questionnaire defined for survey " + id + "!")
							.build();
				}

				// retrieve questionnaire form for survey to do answer validation
				Response r = downloadQuestionnaireForm(qid);

				// if questionnaire form does not exist, pass on response containing error status
				if (200 != r.getStatus()) {
					return r;
				}

				// parse form to XML document incl. validation; will later on be necessary to build query for
				// questionnaire answer table
				Document form;

				try {
					form = validateQuestionnaireData((String) r.getEntity());
				} catch (SAXException e) {
					e.printStackTrace();
					return Response.status(Status.BAD_REQUEST)
							.entity("Questionnaire form is invalid! Cause: " + e.getMessage()).build();
				}

				// now check, if a survey response view exists. If not, create it.
				if (!existsResponseView(id)) {
					createResponseView(id, form);
				}

				// execute generated query
				Connection conn = null;
				PreparedStatement stmt = null;
				ResultSet rset = null;

				try {
					conn = dataSource.getConnection();
					stmt = conn.prepareStatement("select * from " + service.jdbcSchema + ".responses_survey_" + id);
					rset = stmt.executeQuery();

					// format and return result
					String res = createCSVQuestionnaireResult(rset, sep);

					if (sepline > 0) {
						// add separator declaration
						res = "sep=" + sep + "\r\n" + res;
					}
					return Response.status(Status.OK).entity(res).build();

				} catch (SQLException | UnsupportedOperationException e) {
					return internalError(onAction);
				} finally {
					try {
						if (rset != null) {
							rset.close();
						}
					} catch (Exception e) {
						e.printStackTrace();
						return internalError(onAction);
					}
					try {
						if (stmt != null) {
							stmt.close();
						}
					} catch (Exception e) {
						e.printStackTrace();
						return internalError(onAction);
					}
					try {
						if (conn != null) {
							conn.close();
						}
					} catch (Exception e) {
						e.printStackTrace();
						return internalError(onAction);
					}
				}

			} catch (Exception e) {
				e.printStackTrace();
				return internalError(onAction + "cause: " + e.getMessage());
			}
		}

		@SuppressWarnings("unchecked")
		@POST
		@Consumes(MediaType.APPLICATION_JSON)
		@Path("surveys/{id}/responses")
		@ApiResponses(
				value = { @ApiResponse(
						code = 200,
						message = "Survey response submitted successfully."),
						@ApiResponse(
								code = 400,
								message = "Survey response invalid -or- questionnaire form invalid. Cause: ..."),
						@ApiResponse(
								code = 404,
								message = "Survey does not exist -or- No questionnaire defined for survey."),
						@ApiResponse(
								code = 400,
								message = "Survey response already submitted."), })
		public Response submitSurveyResponseJSON(@PathParam("id") int id, String answerJSON) {
			Date now = new Date();
			String onAction = "submitting response to survey " + id;
			try {

				// retrieve survey by id;
				Response rs = getSurvey(id);
				if (rs.getStatus() != 200) {
					return rs;
				}

				JSONObject s = (JSONObject) JSONValue.parse((String) rs.getEntity());

				// check if survey expired/not started
				SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
				df.setTimeZone(TimeZone.getTimeZone("GMT"));

				Date start = df.parse((String) s.get("start"));
				Date end = df.parse((String) s.get("end"));

				if (now.getTime() > end.getTime()) {
					return Response.status(Status.FORBIDDEN).entity("Cannot submit response. Survey expired.").build();
				} else if (now.getTime() < start.getTime()) {
					return Response.status(Status.FORBIDDEN)
							.entity("Cannot submit response. Survey has not begun, yet.").build();
				}

				// check for questionnaire form
				int qid = Integer.parseInt(s.get("qid") + "");

				if (qid == -1) {
					return Response.status(Status.NOT_FOUND).entity("No questionnaire defined for survey " + id + "!")
							.build();
				}

				// retrieve questionnaire form for survey to do answer validation
				Response r = downloadQuestionnaireForm(qid);

				if (200 != r.getStatus()) {
					// if questionnaire form does not exist, pass on response containing error status
					return r;
				}

				Document form;
				JSONObject answer;

				// parse form to XML document incl. validation
				try {
					form = validateQuestionnaireData((String) r.getEntity());
				} catch (SAXException e) {
					return Response.status(Status.BAD_REQUEST)
							.entity("Questionnaire form is invalid! Cause: " + e.getMessage()).build();
				}

				try {
					// System.out.println(answerJSON);

					answer = (JSONObject) JSONValue.parseWithException(answerJSON);
				} catch (ParseException e) {
					return Response.status(Status.BAD_REQUEST)
							.entity("Survey response is not valid JSON! Cause: " + e.getMessage()).build();
				}

				JSONObject answerFieldTable;

				// validate if answer matches form.
				try {
					answerFieldTable = validateResponse(form, answer);
				} catch (IllegalArgumentException e) {
					return Response.status(Status.BAD_REQUEST)
							.entity("Survey response is invalid! Cause: " + e.getMessage()).build();
				}

				// after all validation finally persist survey response in database
				int surveyId = id;

				String sub = (String) getActiveUserInfo().get("sub");

				if (Context.get().getMainAgent() instanceof AnonymousAgent) {
					sub += now.getTime();
				}

				Connection conn = null;
				PreparedStatement stmt = null;
				ResultSet rset = null;

				this.logAnswersToMobSOS(surveyId, form, answerFieldTable);

				try {
					conn = dataSource.getConnection();
					stmt = conn.prepareStatement("insert into " + service.jdbcSchema
							+ ".response(uid,sid,qkey,qval,time) values (?,?,?,?,?)");

					Iterator<String> it = answerFieldTable.keySet().iterator();
					while (it.hasNext()) {

						String qkey = it.next();
						String qval = "" + answerFieldTable.get(qkey);

						stmt.setString(1, sub);
						stmt.setInt(2, surveyId);
						stmt.setString(3, qkey);
						stmt.setString(4, qval);
						stmt.setTimestamp(5, new Timestamp(now.getTime()));
						stmt.addBatch();

					}
					stmt.executeBatch();
					return Response.status(Status.OK).entity("Response to survey " + id + " submitted successfully.")
							.build();

				} catch (SQLException | UnsupportedOperationException e) {
					if (0 <= e.getMessage().indexOf("Duplicate")) {
						return Response.status(Status.CONFLICT).entity("Survey response already submitted!").build();
					} else {
						e.printStackTrace();
						return internalError(onAction);
					}
				} finally {
					try {
						if (rset != null) {
							rset.close();
						}
					} catch (Exception e) {
						e.printStackTrace();
						return internalError(onAction);
					}
					try {
						if (stmt != null) {
							stmt.close();
						}
					} catch (Exception e) {
						e.printStackTrace();
						return internalError(onAction);
					}
					try {
						if (conn != null) {
							conn.close();
						}
					} catch (Exception e) {
						e.printStackTrace();
						return internalError(onAction);
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
				return internalError(onAction);
			}
		}

		private void logAnswersToMobSOS(int surveyId, Document form, JSONObject answerFieldTable) {
			JSONObject questions = extractQuestionInformation(form);
			Iterator<String> it = answerFieldTable.keySet().iterator();
			while (it.hasNext()) {
				String qkey = it.next();
				String qval = "" + answerFieldTable.get(qkey);
				if(qval.equals("NaN")){
					// comment fields may return NaN if empty and must be disregarded
					continue;
				}
				JSONObject question = (JSONObject) questions.get(qkey);

				JSONObject message = new JSONObject();
				message.put("sid", surveyId);
				message.put("qkey", qkey);
				message.put("qval", qval);
				message.put("instructions", question.get("instructions"));
				Context.get().monitorEvent(MonitoringEvent.SERVICE_CUSTOM_MESSAGE_1, message.toJSONString() );
			}
		}

		/**
		 * TODO: write documentation
		 * 
		 * @param id
		 * @param answerXml
		 * @return
		 */
		@POST
		@Consumes(MediaType.TEXT_XML)
		@Path("surveys/{id}/responses")
		public Response submitSurveyResponseXML(@PathParam("id") int id, String answerXml) {

			String onAction = "submitting response to survey " + id;

			try {
				Document answer;
				// parse answer to XML document incl. validation
				try {
					answer = validateQuestionnaireData(answerXml);
				} catch (SAXException e) {
					return Response.status(Status.BAD_REQUEST)
							.entity("Questionnaire form is invalid! Cause: " + e.getMessage()).build();
				}

				return submitSurveyResponseJSON(id, convertResponseXMLtoJSON(answer).toJSONString());

			} catch (Exception e) {
				e.printStackTrace();
				return internalError(onAction);
			}
		}

		@DELETE
		@Path("surveys/{id}/responses")
		@ApiResponses(
				value = { @ApiResponse(
						code = 200,
						message = "Survey responses deleted successfully."),
						@ApiResponse(
								code = 401,
								message = "Survey responses may only be deleted by survey owner."),
						@ApiResponse(
								code = 404,
								message = "Survey does not exist."), })
		public Response deleteSurveyResponses(@PathParam("id") int id) {

			if (Context.get().getMainAgent() instanceof AnonymousAgent) {
				return Response.status(Status.UNAUTHORIZED)
						.entity("Please authenticate to delete responses for survey!").build();
			}

			String onAction = "deleting responses for survey " + id;

			try {

				int exown;
				// survey may only be updated if survey exists and active agent is owner
				exown = checkExistenceOwnership(id, 0);

				// check if survey exists; if not, return 404.
				if (exown == -1) {
					return Response.status(Status.NOT_FOUND).entity("Survey does not exist!").build();
				}
				// if survey exists, check if active agent is owner. if not, return 401.
				else if (exown == 0) {
					return Response.status(Status.UNAUTHORIZED)
							.entity("Survey responses may only be deleted by survey owner.").build();
				}

				Connection c = null;
				PreparedStatement s = null;
				ResultSet rs = null;

				// +++ dsi
				try {
					c = dataSource.getConnection();
					s = c.prepareStatement("delete from " + service.jdbcSchema + ".response where sid = ?");
					s.setInt(1, id);
					s.executeUpdate();

					return Response.status(Status.OK).entity("Responses to survey " + id + " deleted successfully.")
							.build();

				} catch (Exception e) {
					e.printStackTrace();
					return internalError(onAction);
				} finally {
					try {
						if (rs != null) {
							rs.close();
						}
					} catch (Exception e) {
						e.printStackTrace();
						return internalError(onAction);
					}
					try {
						if (s != null) {
							s.close();
						}
					} catch (Exception e) {
						e.printStackTrace();
						return internalError(onAction);
					}
					try {
						if (c != null) {
							c.close();
						}
					} catch (Exception e) {
						e.printStackTrace();
						return internalError(onAction);
					}
				}
				// --- dsi

			} catch (Exception e) {
				e.printStackTrace();
				return internalError(onAction);
			}
		}

		@GET
		@Produces(MediaType.TEXT_HTML)
		@Path("redirect")
		public Response redirectCallback(@HeaderParam("accept-language") String lang) {
			String onAction = "processing OpenID Connect redirect Callback";
			String html = "";
			// start off with template
			try {
				Scanner scanner = new Scanner(new File("./etc/html/redirect-callback.html"));
				html = scanner.useDelimiter("\\A").next();
				scanner.close();
			} catch (FileNotFoundException e) {
				return internalError(onAction);
			}

			// localize template
			html = i18n(html, lang);

			html = fillPlaceHolder(html, "EP_URL", service.staticContentUrl);
			html = fillPlaceHolder(html, "L2P_URL", service.las2peerUrl);
			html = fillPlaceHolder(html, "SC_URL", service.staticContentUrl);
			html = fillPlaceHolder(html, "OIDC_PROV_NAME", service.oidcProviderName);
			html = fillPlaceHolder(html, "OIDC_PROV_LOGO", service.oidcProviderLogo);
			html = fillPlaceHolder(html, "OIDC_PROV_URL", service.oidcProviderUrl);
			html = fillPlaceHolder(html, "OIDC_CLNT_ID", service.oidcClientId);

			return Response.status(Status.OK).entity(html).build();
		}

		@GET
		@Produces(MediaType.TEXT_HTML)
		public Response serveIndexPage(@HeaderParam("accept-language") String lang) {
			String onAction = "serving index page";
			String html = "";
			// start off with template
			try {
				Scanner scanner = new Scanner(new File("./etc/html/index.html"));
				html = scanner.useDelimiter("\\A").next();
				scanner.close();
			} catch (FileNotFoundException e) {
				return internalError(onAction);
			}

			// localize template
			html = i18n(html, lang);

			// fill in placeholders with concrete values
			html = fillPlaceHolder(html, "EP_URL", service.epUrl);
			html = fillPlaceHolder(html, "L2P_URL", service.las2peerUrl);
			html = fillPlaceHolder(html, "SC_URL", service.staticContentUrl);

			html = fillPlaceHolder(html, "OIDC_PROV_NAME", service.oidcProviderName);
			html = fillPlaceHolder(html, "OIDC_PROV_LOGO", service.oidcProviderLogo);
			html = fillPlaceHolder(html, "OIDC_PROV_URL", service.oidcProviderUrl);
			html = fillPlaceHolder(html, "OIDC_CLNT_ID", service.oidcClientId);

			return Response.status(Status.OK).entity(html).build();

		}

		// ================= Swagger Resource Listing & API Declarations =====================
		/*
		@GET
		@Path("api-docs")
		@ApiResponses(value={
				@ApiResponse(code = 200, message = "Swagger 1.2 compliant resource listing"),
				@ApiResponse(code = 404, message = "Swagger resource listing not available due to missing annotations."),
		})
		@Produces(MediaType.APPLICATION_JSON)
		public Response getSwaggerResourceListing(){
			return RESTMapper.getSwaggerResourceListing(this.getClass());
		}
		
		@GET
		@Path("api-docs/{tlr}")
		@Produces(MediaType.APPLICATION_JSON)
		@ApiResponses(value={
				@ApiResponse(code = 200, message = "Swagger 1.2 compliant API declaration"),
				@ApiResponse(code = 404, message = "Swagger API declaration not available due to missing annotations."),
		})
		public Response getSwaggerApiDeclaration(@PathParam("tlr") String tlr){
			return RESTMapper.getSwaggerApiDeclaration(this.getClass(),tlr, epUrl);
		}
		*/
		// ================= Static Content Hosting ===================
		//
		// The browser frontend part of this service, in particular the HTML code generated from templates in ./etc/html
		// links to static content (JS/CSS). This static content should be hosted on a regular, however highly available
		// Web server. Therefore, this service foresees a configuration parameter staticContentUrl, which allows to
		// specify
		// a URL linking to a folder under which this static content is expected to be hosted. As a fallback solution,
		// MobSOS Surveys can host this static content on its own with the two methods below. However, the built-in
		// mechanism
		// should not be used with productive installations due to performance issues!

		@GET
		@Path("/js/{filename:.+}")
		public Response serveJS(@PathParam("filename") String filename) {

			// String onAction = "serving JavaScript";

			try {
				Scanner scanner = new Scanner(new File("./etc/webapp/js/" + filename));
				String js = scanner.useDelimiter("\\A").next();
				scanner.close();
				String responseType = MediaType.TEXT_PLAIN;
				if (filename.contains(".html")) {
					responseType = MediaType.TEXT_HTML;
				}
				return Response.ok(js, responseType).build();

			} catch (FileNotFoundException e) {
				return Response.status(Status.NOT_FOUND).entity(filename + " not found!").build();
			}
		}

		@GET
		@Produces(MediaType.TEXT_PLAIN)
		@Path("/css/{filename}")
		public Response serveCSS(@PathParam("filename") String filename) {

			// String onAction = "serving CSS";

			try {
				Scanner scanner = new Scanner(new File("./etc/webapp/css/" + filename));
				String js = scanner.useDelimiter("\\A").next();
				scanner.close();
				return Response.status(Status.OK).entity(js).build();

			} catch (FileNotFoundException e) {
				return Response.status(Status.NOT_FOUND).entity(filename + " not found!").build();
			}
		}

		// ============= RESOURCE INFORMATION (WORKAROUND) ==============
		@SuppressWarnings("unchecked")
		@POST
		@Produces(MediaType.APPLICATION_JSON)
		@Path("resource-meta")
		public Response getResourceMetadata(String uri) {

			String onAction = "retrieving resource metadata for URI " + uri;

			try {

				Connection conn = null;
				PreparedStatement stmt = null;
				ResultSet rset = null;

				try {
					conn = dataSource.getConnection();
					stmt = conn.prepareStatement("select * from " + service.jdbcSchema + ".resource where uri = ?");
					stmt.setString(1, uri);

					rset = stmt.executeQuery();

					if (!rset.isBeforeFirst()) {
						return Response.status(Status.NOT_FOUND).entity("No metadata found for resource " + uri + "!")
								.build();
					}
					rset.next();

					JSONObject meta = new JSONObject();

					meta.put("name", rset.getString("name"));
					meta.put("description", rset.getString("description"));

					return Response.status(Status.OK).entity(meta.toJSONString()).build();

				} catch (SQLException | UnsupportedOperationException e) {
					return internalError(onAction);
				} finally {
					try {
						if (rset != null) {
							rset.close();
						}
					} catch (Exception e) {
						e.printStackTrace();
						return internalError(onAction);
					}
					try {
						if (stmt != null) {
							stmt.close();
						}
					} catch (Exception e) {
						e.printStackTrace();
						return internalError(onAction);
					}
					try {
						if (conn != null) {
							conn.close();
						}
					} catch (Exception e) {
						e.printStackTrace();
						return internalError(onAction);
					}
				}
			}

			catch (Exception e) {
				e.printStackTrace();
				return internalError(onAction);
			}
		}

		@SuppressWarnings("unchecked")
		@GET
		@Produces(MediaType.APPLICATION_JSON)
		@Path("oidc/clients")
		public Response getClientsMetadata() {

			String onAction = "retrieving metadata for clients";

			JSONArray result = new JSONArray();

			try {

				Connection conn = null;
				PreparedStatement stmt = null;
				ResultSet rset = null;

				try {
					conn = dataSource.getConnection();
					stmt = conn.prepareStatement(
							"select client_id, client_name, client_description, client_uri, logo_uri from "
									+ service.oidcSchema + ".client_details order by client_name asc");

					rset = stmt.executeQuery();

					while (rset.next()) {
						JSONObject meta = new JSONObject();
						meta.put("id", rset.getString("client_id"));
						meta.put("name", rset.getString("client_name"));
						meta.put("description", rset.getString("client_description"));
						meta.put("uri", rset.getString("client_uri"));
						meta.put("logo", rset.getString("logo_uri"));
						result.add(meta);
					}

					return Response.status(Status.OK).entity(result.toJSONString()).build();

				} catch (SQLException | UnsupportedOperationException e) {
					return internalError(onAction + e.getMessage());
				} finally {
					try {
						if (rset != null) {
							rset.close();
						}
					} catch (Exception e) {
						e.printStackTrace();
						return internalError(onAction);
					}
					try {
						if (stmt != null) {
							stmt.close();
						}
					} catch (Exception e) {
						e.printStackTrace();
						return internalError(onAction);
					}
					try {
						if (conn != null) {
							conn.close();
						}
					} catch (Exception e) {
						e.printStackTrace();
						return internalError(onAction);
					}
				}
			}

			catch (Exception e) {
				e.printStackTrace();
				return internalError(onAction + " " + e.getMessage());
			}
		}

		@SuppressWarnings("unchecked")
		@GET
		@Produces(MediaType.APPLICATION_JSON)
		@Path("oidc/clients/{id}")
		public Response getClientMetadata(@PathParam(
				value = "id") String id) {

			String onAction = "retrieving metadata for client " + id;

			try {

				Connection conn = null;
				PreparedStatement stmt = null;
				ResultSet rset = null;

				try {
					conn = dataSource.getConnection();
					stmt = conn.prepareStatement(
							"select client_id, client_name, client_description, client_uri, logo_uri from "
									+ service.oidcSchema + ".client_details where client_id = ?");
					stmt.setString(1, id);

					rset = stmt.executeQuery();

					if (!rset.isBeforeFirst()) {
						return Response.status(Status.NOT_FOUND).entity("No metadata found for client " + id + "!")
								.build();
					}
					rset.next();

					JSONObject meta = new JSONObject();
					meta.put("id", rset.getString("client_id"));
					meta.put("name", rset.getString("client_name"));
					meta.put("description", rset.getString("client_description"));
					meta.put("uri", rset.getString("client_uri"));
					meta.put("logo", rset.getString("logo_uri"));

					return Response.status(Status.OK).entity(meta.toJSONString()).build();

				} catch (SQLException | UnsupportedOperationException e) {
					return internalError(onAction);
				} finally {
					try {
						if (rset != null) {
							rset.close();
						}
					} catch (Exception e) {
						e.printStackTrace();
						return internalError(onAction);
					}
					try {
						if (stmt != null) {
							stmt.close();
						}
					} catch (Exception e) {
						e.printStackTrace();
						return internalError(onAction);
					}
					try {
						if (conn != null) {
							conn.close();
						}
					} catch (Exception e) {
						e.printStackTrace();
						return internalError(onAction);
					}
				}
			}

			catch (Exception e) {
				e.printStackTrace();
				return internalError(onAction);
			}
		}

		// ============= OVERALL RATING ===========================
		@GET
		@Produces(MediaType.TEXT_PLAIN)
		@Path("surveys/{id}/responses")
		@ApiOperation(
				value = "getFeedback",
				notes = "Use resource <i>/surveys</i> to retrieve list of existing surveys.")
		@ApiResponses(
				value = { @ApiResponse(
						code = 200,
						message = "Client feedback data"),
						@ApiResponse(
								code = 404,
								message = "Client does not exist.") })
		public Response getFeedback(@PathParam(
				value = "client_id") String cid, @QueryParam("sep") String sep, @QueryParam("sepline") int sepline) {

			String onAction = "retrieving client feedback";

			try {
				// authentication required for submitting response
				if (Context.get().getMainAgent() instanceof AnonymousAgent) {
					return Response.status(Status.UNAUTHORIZED)
							.entity("Client feedback retrieval requires authentication.").build();
				} else {
					// JSONObject r = new JSONObject(); //result to return in HTTP response

					Connection c = null;
					PreparedStatement s = null;
					ResultSet rs = null;

					// +++ dsi
					try {
						// query for given survey
						c = dataSource.getConnection();
						s = c.prepareStatement("select * from " + service.jdbcSchema + ".feedback where client_id = ?");
						s.setString(1, cid);

						rs = s.executeQuery();

						String res = createCSVQuestionnaireResult(rs, sep);

						if (sepline > 0) {
							// add separator declaration
							res = "sep=" + sep + "\r\n" + res;
						}

						return Response.status(Status.OK).entity(res).build();

					} catch (Exception e) {
						e.printStackTrace();
						return internalError(onAction);
					} finally {
						try {
							if (rs != null) {
								rs.close();
							}
						} catch (Exception e) {
							e.printStackTrace();
							return internalError(onAction);
						}
						try {
							if (s != null) {
								s.close();
							}
						} catch (Exception e) {
							e.printStackTrace();
							return internalError(onAction);
						}
						try {
							if (c != null) {
								c.close();
							}
						} catch (Exception e) {
							e.printStackTrace();
							return internalError(onAction);
						}
					}
					// --- dsi
				}
			} catch (Exception e) {
				e.printStackTrace();
				return internalError(onAction);
			}
		}

		@PUT
		@Path("feedback/{client_id}")
		@Consumes(MediaType.APPLICATION_JSON)
		@ApiResponses(
				value = { @ApiResponse(
						code = 200,
						message = "Client feedback submission complete."),
						@ApiResponse(
								code = 400,
								message = "Client feedback data invalid"),
						// @ApiResponse(code = 401, message = "Client feedback submission requires authentication."),
						@ApiResponse(
								code = 404,
								message = "Client does not exist.") })
		public Response sumbitFeedback(@PathParam(
				value = "client_id") String cid, String data) {

			UserAgent me = (UserAgent) Context.getCurrent().getMainAgent();

			JSONObject fb;

			try {
				fb = (JSONObject) JSONValue.parseWithException(data);
			} catch (ParseException e) {
				return Response.status(Status.BAD_REQUEST).entity("Feedback data required in JSON format!").build();
			}

			if (fb.get("rating") == null && fb.get("comment") == null) {
				return Response.status(Status.BAD_REQUEST).entity("Feedback data must include rating or comment!")
						.build();
			}

			Integer rating = null;
			String comment = null;

			if (fb.get("rating") != null) {
				try {
					rating = Integer.parseInt(fb.get("rating").toString());
				} catch (NumberFormatException e) {
					return Response.status(Status.BAD_REQUEST).entity("Feedback rating must be number (0 <= x <= 5)!")
							.build();
				}
				if (rating > 5 || rating < 0) {
					return Response.status(Status.BAD_REQUEST).entity("Feedback rating must be number (0 <= x <= 5)!")
							.build();
				}
			}

			if (fb.get("comment") != null) {
				comment = fb.get("comment").toString();
			}

			System.out.println("Feedback received from " + me.getEmail());
			System.out.println(" - Rating: " + rating);
			System.out.println(" - Comment: " + comment);

			return Response.status(Status.OK).entity("Client feedback submission complete").build();

		}

		// ============= COMMUNITY EXTENSIONS (TODO) ==============

		/*
		@POST
		@Consumes(MediaType.TEXT_XML)
		@Path("surveys/{id}/answers/{cid}")
		public Response submitQuestionnaireAnswerForCommunityXML(@PathParam("id") int id, @PathParam("cid") long cid,  String answerXml){
			try{
				// if community not found, return 404
				try{
					this.Context.getCurrent().getLocalNode().getAgent(cid);
				} catch (AgentNotKnownException e){
					Response result = new Response("Community " + cid + " does not exist!");
					result.setStatus(404);
					return result;
				}
		
				// retrieve survey id;
				int qid = getQuestionnaireIdForSurvey(id);
		
				if(qid == -1){
					Response result = new Response("No questionnaire defined for survey " + id + "!");
					result.setStatus(404);
					return result;
				}
		
				// retrieve questionnaire form for survey to do answer validation
				Response r = downloadQuestionnaireForm(qid); 
		
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
					Response result = new Response("Questionnaire form is invalid! Cause: " + e.getMessage());
					result.setStatus(400);
					return result;
				}
		
				// parse answer to XML document incl. validation
				try{
					answer = validateQuestionnaireData(answerXml);
				} catch (SAXException e){
					Response result = new Response("Questionnaire form is invalid! Cause: " + e.getMessage());
					result.setStatus(400);
					return result;
				}
		
				JSONObject answerFieldTable;
		
				// validate if answer matches form.
				try{
					answerFieldTable = validateAnswer(form,convertAnswerXMLtoJSON(answer));
				} catch (IllegalArgumentException e){
					Response result = new Response("Questionnaire answer is invalid! Cause: " + e.getMessage());
					result.setStatus(400);
					return result;
				}
		
				int surveyId = id;
				long communityId = cid;
				long userId = this.Context.get().getMainAgent().getIdentifier();
		
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
		
				Response result = new Response("Questionnaire answer stored successfully.");
				result.setStatus(200);
				return result;
		
			} catch(Exception e){
				e.printStackTrace();
				Response result = new Response("Internal error: " + e.getMessage());
				result.setStatus(500);
				return result;
		
			}
		}
		
		@GET
		@Produces(MediaType.TEXT_HTML)
		@Path("surveys/{id}/questionnaire/{cid}")
		public Response getSurveyQuestionnaireFormForCommunityHTML(@PathParam("id") int id, @PathParam("cid") long cid){
			try{
		
				GroupAgent community;
				// if community not found, return 404
				try{
		
					Agent a = this.Context.getCurrent().getLocalNode().getAgent(cid);
					if(!(a instanceof GroupAgent)){
						Response result = new Response("Agent " + cid + " does not represent a community!");
						result.setStatus(400);
						return result;
					}
					community = (GroupAgent) a;
				} catch (AgentNotKnownException e){
					e.printStackTrace();
					Response result = new Response("Community " + cid + " does not exist!");
					result.setStatus(404);
					return result;
				}
		
				// retrieve survey data; if survey does not exist, return 404.
				Response r = getSurvey(id);
				if(200 != r.getStatus()){
					System.err.println(r.getResult());
					return r;
				}
		
				JSONObject survey = (JSONObject) JSONValue.parse(r.getResult());
		
				// check if survey has the questionnaire id field qid set. If not, return 404 with a respective message.
				if(null == survey.get("qid")){
					Response result = new Response("Questionnaire not set for survey " + id + ".");
					result.setStatus(404);
					return result;
				}
		
				long qid = (Long) survey.get("qid");
		
				// now download questionnaire form from database
				questionnaireDownloadFormStatement.clearParameters();
				questionnaireDownloadFormStatement.setLong(1, qid);
		
				ResultSet rs = questionnaireDownloadFormStatement.executeQuery();
		
				if (!rs.isBeforeFirst()){
					Response result = new Response("Form for questionnaire " + qid + " does not exist!");
					result.setStatus(404);
					return result;
				}
		
				rs.next();
		
				String formXml = rs.getString(1);
		
				String adaptedFormXml = adaptForm(formXml,survey, (UserAgent) this.Context.getCurrent().getMainAgent(),community);
		
				Document form;
				// before returning form, make sure it's still valid (may be obsolete step...)
				try{
					form = validateQuestionnaireData(adaptedFormXml);
				} catch(IOException e){
					e.printStackTrace();
					Response result = new Response("Internal error: " + e.getMessage());
					result.setStatus(500);
					return result;
				} catch (SAXException e){
					e.printStackTrace();
					Response result = new Response("Questionnaire form is invalid! Cause: " + e.getMessage());
					result.setStatus(400);
					return result;
				}
		
				String text = new Scanner(new File("./etc/html/questionnaire-template.html")).useDelimiter("\\A").next();
		
				String adaptText = adaptForm(text, survey, (UserAgent) this.Context.getCurrent().getMainAgent(), community);
		
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
							String navpill = "\t\t\t\t\t<li><a href=\"#step-" + i +"\"><h5 class=\"list-group-item-heading\">" + i + "</h5></a></li>\n";
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
				Response result = new Response(adaptText);
				result.setStatus(200);
				return result;
		
			} catch (Exception e){
				e.printStackTrace();
				Response result = new Response("Internal error: " + e.getMessage());
				result.setStatus(500);
				return result;
			}
		
		}
		
		@GET
		@Produces(MediaType.TEXT_XML)
		@Path("surveys/{id}/questionnaire/{cid}")
		public Response getSurveyQuestionnaireFormForCommunityXML(@PathParam("id") int id, @PathParam("cid") long cid){
			try{
		
				GroupAgent community;
				// if community not found, return 404
				try{
		
					Agent a = this.Context.getCurrent().getLocalNode().getAgent(cid);
					if(!(a instanceof GroupAgent)){
						Response result = new Response("Agent " + cid + " does not represent a community!");
						result.setStatus(400);
						return result;
					}
					community = (GroupAgent) a;
				} catch (AgentNotKnownException e){
					e.printStackTrace();
					Response result = new Response("Community " + cid + " does not exist!");
					result.setStatus(404);
					return result;
				}
		
				// retrieve survey data; if survey does not exist, return 404.
				Response r = getSurvey(id);
				if(200 != r.getStatus()){
					System.err.println(r.getResult());
					return r;
				}
		
				JSONObject survey = (JSONObject) JSONValue.parse(r.getResult());
		
				// check if survey has the questionnaire id field qid set. If not, return 404 with a respective message.
				if(null == survey.get("qid")){
					Response result = new Response("Questionnaire not set for survey " + id + ".");
					result.setStatus(404);
					return result;
				}
		
				long qid = (Long) survey.get("qid");
		
				// now download questionnaire form from database
				questionnaireDownloadFormStatement.clearParameters();
				questionnaireDownloadFormStatement.setLong(1, qid);
		
				ResultSet rs = questionnaireDownloadFormStatement.executeQuery();
		
				if (!rs.isBeforeFirst()){
					Response result = new Response("Form for questionnaire " + qid + " does not exist!");
					result.setStatus(404);
					return result;
				}
		
				rs.next();
		
				String formXml = rs.getString(1);
		
				String adaptedFormXml = adaptForm(formXml,survey, (UserAgent) this.Context.getCurrent().getMainAgent(),community);
		
				// before returning form, make sure it's still valid (may be obsolete step...)
				try{
					validateQuestionnaireData(adaptedFormXml);
					Response result = new Response(adaptedFormXml);
					result.setStatus(200);
					return result;
				} catch(IOException e){
					e.printStackTrace();
					Response result = new Response("Internal error: " + e.getMessage());
					result.setStatus(500);
					return result;
				} catch (SAXException e){
					e.printStackTrace();
					Response result = new Response("Questionnaire form is invalid! Cause: " + e.getMessage());
					result.setStatus(400);
					return result;
				}
			} catch (Exception e){
				e.printStackTrace();
				Response result = new Response("Internal error: " + e.getMessage());
				result.setStatus(500);
				return result;
			}
		}
		
		@GET
		@Path("surveys/{id}/answers/{cid}")
		public Response retrieveQuestionnaireAnswersForCommunity(@PathParam("id") int id, @PathParam("cid") long cid){
			try{
				int exown = checkExistenceOwnership(id,0);
		
				GroupAgent community;
		
				// if community not found, return 404
				try{
					community = (GroupAgent) this.Context.getCurrent().getLocalNode().getAgent(cid);
				} catch (AgentNotKnownException e){
					Response result = new Response("Community " + cid + " does not exist!");
					result.setStatus(404);
					return result;
				} catch (ClassCastException e){
					Response result = new Response("Agent " + cid + " is not a community!");
					result.setStatus(400);
					return result;
				}
		
				// allow access to answer data only, if current user is either survey owner or member of given community
				if(exown == -1 || !community.isMember(this.Context.getCurrent().getMainAgent())){
					Response result = new Response("Access to answer information only for owner of survey " + id + " and members of community " + cid);
					result.setStatus(401);
					return result;
				}
		
				// retrieve survey id;
				int qid = getQuestionnaireIdForSurvey(id);
		
				if(qid == -1){
					Response result = new Response("No questionnaire defined for survey " + id + "!");
					result.setStatus(404);
					return result;
				}
		
				// retrieve questionnaire form for survey to do answer validation
				Response r = downloadQuestionnaireForm(qid); 
		
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
					Response result = new Response("Questionnaire form is invalid! Cause: " + e.getMessage());
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
				//     from mobsos.response where sid = 1 and cid = 1 group by uid;
		
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
		
				sql += " from " + jdbcSchema + ".response where sid ="+ id + " and cid =" + cid + " group by uid;";
		
				//System.out.println("SQL: \n" + sql);
		
				// execute generated query
				Statement s = connection.createStatement();
				ResultSet rs = s.executeQuery(sql);
		
				// format and return result
				String res = createCSVQuestionnaireResult(rs);
		
				Response result = new Response(res);
				result.setStatus(200);
				return result;
		
			} catch (Exception e){
				e.printStackTrace();
				Response result = new Response("Internal error: " + e.getMessage());
				result.setStatus(500);
				return result;
			}
		}
		 */

		// ============= Private helper methods ===================

		private String fillPlaceHolder(String data, String placeholder, String value) {
			// detect all tags used by questionnaire author throughout the form
			// and replace them by the respective values.
			Pattern p = Pattern.compile("\\$\\{" + placeholder + "\\}");
			Matcher m = p.matcher(data);

			String adaptedform = new String(data);

			// replace any occurring author tags within questionnaire form
			// Vector<String> foundTags = new Vector<String>();
			while (m.find()) {
				String tag = m.group().substring(2, m.group().length() - 1);
				adaptedform = adaptedform.replaceAll("\\$\\{" + tag + "\\}", value);
			}

			return adaptedform;
		}

		/**
		 * Adapts a given questionnaire form before being administered to a requesting user in a given community
		 * context. For adaptation purposes, questionnaire authors can make use of a set of author tags, which are
		 * replaced by this method. The syntax for author tags is ${AT}. The following values for AT are supported:
		 * <ul>
		 * <li>USER.ID - identifier of requesting user</li>
		 * <li>USER.NAME - login name of requesting user</li>
		 * <li>USER.MAIL - email address of requesting user</li>
		 * <li>COMMUNITY.ID - identifier of community, in whose context questionnaire form is requested</li>
		 * <li>SURVEY.ID - identifier of survey, in whose context questionnaire form is requested</li>
		 * <li>SURVEY.NAME - name of survey, in whose context questionnaire form is requested</li>
		 * <li>SURVEY.DESCRIPTION - description of survey, in whose context questionnaire form is requested</li>
		 * <li>SURVEY.RESOURCE - resource under consideration in survey, in whose context questionnaire form is
		 * requested</li>
		 * <li>SURVEY.START - ISO-8601 compliant UTC start time of survey, in whose context questionnaire form is
		 * requested</li>
		 * <li>SURVEY.END - ISO-8601 compliant UTC end time of survey, in whose context questionnaire form is requested
		 * </li>
		 * <li>SURVEY.OWNER - owner of survey</li>
		 * <li>SURVEY.ORGANIZATION - organization owner of survey belongs to</li>
		 * <li>SURVEY.LOGO - logo to be shown in survey header, e.g. organization logo</li>
		 * </ul>
		 * 
		 * @param originalFormXml
		 * @param survey
		 * @param user
		 * @param community
		 * @return
		 */
		private String adaptForm(String originalFormXml, JSONObject survey, Agent user, GroupAgent community) {
			// detect all tags used by questionnaire author throughout the form
			// and replace them by the respective values.
			Pattern p = Pattern.compile("\\$\\{([^\\}])+\\}");
			Matcher m = p.matcher(originalFormXml);

			String adaptedform = new String(originalFormXml);

			// replace any occurring author tags within questionnaire form
			Vector<String> foundTags = new Vector<>();
			while (m.find()) {
				String tag = m.group().substring(2, m.group().length() - 1);
				String value = null;
				if (!foundTags.contains(tag)) {
					if (tag.startsWith("USER")) {
						if (tag.endsWith("ID")) {
							value = "" + user.getIdentifier();
						} else if (tag.endsWith("NAME")) {
							if (user instanceof AnonymousAgent) {
								value = "(?)";
							} else {
								value = ((UserAgent) user).getLoginName();
							}
						} else if (tag.endsWith("MAIL")) {
							if (user instanceof AnonymousAgent) {
								value = "(?)";
							} else {
								value = ((UserAgent) user).getEmail();
							}
						}
					} else if (tag.startsWith("COMMUNITY")) {
						if (tag.endsWith("ID")) {
							if (community != null) {
								value = "" + community.getIdentifier();
							} else {
								value = "(no community context)";
							}
						}
					} else if (tag.startsWith("SURVEY.")) {
						if (tag.endsWith("ID")) {
							value = survey.get("id") + "";
						} else if (tag.endsWith("NAME")) {
							value = (String) survey.get("name");
						} else if (tag.endsWith("DESCRIPTION")) {
							value = (String) survey.get("description");
						} else if (tag.endsWith("RESOURCE")) {
							String id = (String) survey.get("resource-label");
							Response r = getClientMetadata(id);
							if (r.getStatus() == 200) {
								JSONObject meta = (JSONObject) JSONValue.parse((String) r.getEntity());
								value = (String) meta.get("name");
							} else {
								value = id;
							}

							// JSONObject res = (JSONObject) survey.get("resource");
							// String res_name = (String) res.get("name");
							// value = res_name;
						} else if (tag.endsWith("START")) {
							value = (String) survey.get("start");
						} else if (tag.endsWith("END")) {
							value = (String) survey.get("end");
						} else if (tag.endsWith("OWNER")) {
							value = (String) survey.get("owner");
						} else if (tag.endsWith("ORGANIZATION")) {
							value = (String) survey.get("organization");
						} else if (tag.endsWith("LOGO")) {
							value = (String) survey.get("logo");
						}
					}

					// TODO: add more author tags, if necessary.

					if (value != null) {
						adaptedform = adaptedform.replaceAll("\\$\\{" + tag + "\\}", value);
						// System.out.println("Resolved questionnaire author tag: "+tag);
						foundTags.add(tag);
					} else {
						System.err.println("Warning: could not resolve questionnaire author tag '" + tag + "'");
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

		/**
		 * Checks if survey or questionnaire exists and active agent is owner.
		 * 
		 * @param id int survey or questionnaire id
		 * @param type int 0 for survey, 1 for questionnaire
		 * @return int -1 if questionnaire/survey does not exist, 0 if active agent is not owner, 1 if active agent is
		 *         owner
		 * @throws Exception
		 */
		private int checkExistenceOwnership(int id, int type) throws Exception {

			try {
				Connection c = null;
				PreparedStatement s = null;
				ResultSet rs = null;

				// +++ dsi
				try {
					c = dataSource.getConnection();

					if (type == 0) {
						s = c.prepareStatement("select owner from " + service.jdbcSchema + ".survey where id = ?");
					} else {
						s = c.prepareStatement(
								"select owner from " + service.jdbcSchema + ".questionnaire where id = ?");
					}

					s.setInt(1, id);
					rs = s.executeQuery();

					// survey/questionnaire does not exist
					if (!rs.isBeforeFirst()) {
						return -1;
					}

					rs.next();
					String owner = rs.getString(1);

					// active agent is not owner.

					String sub = (String) getActiveUserInfo().get("sub");

					if (!owner.equals(sub)) {
						return 0;
					}
					// active agent is owner.
					else {
						return 1;
					}

				} catch (Exception e) {
					throw e;
				} finally {
					try {
						if (rs != null) {
							rs.close();
						}
					} catch (Exception e) {
						throw e;
					}
					try {
						if (s != null) {
							s.close();
						}
					} catch (Exception e) {
						throw e;
					}
					try {
						if (c != null) {
							c.close();
						}
					} catch (Exception e) {
						throw e;
					}
				}
				// --- dsi

			} catch (Exception e) {
				throw e;
			}

		}

		/**
		 * TODO: documentation
		 * 
		 * @param rs
		 * @return
		 * @throws SQLException
		 */
		private String createCSVQuestionnaireResult(ResultSet rs, String sep) throws SQLException {
			int cols = rs.getMetaData().getColumnCount();

			String res = "";
			String headline = "";

			// first create header row
			for (int i = 1; i <= cols; i++) {
				headline += rs.getMetaData().getColumnName(i);
				rs.getMetaData().getColumnTypeName(i);
				if (i < cols) {
					headline += sep;
				}
			}
			res += headline + "\r\n";

			// now compile answer data; stick to RFC 4180
			String data = "";
			while (rs.next()) {
				for (int i = 1; i <= cols; i++) {
					Object o = rs.getObject(i);
					if (o != null) {

						// Fields containing line breaks (CRLF), double quotes, and commas should be enclosed in
						// double-quotes.
						if (o.toString().contains(",") || o.toString().contains("\"") || o.toString().contains("\n")) {

							String fval = o.toString();

							// If double-quotes are used to enclose fields, then a double-quote appearing inside a field
							// must be escaped by preceding it with another double quote.
							fval = fval.replaceAll("\"", "\"\\\"");

							data += "\"" + fval + "\"";
						} else {
							data += o.toString();
						}
					}
					if (i < cols) {
						data += sep;
					}
				}
				data += "\r\n";
			}
			res += data.trim();

			return res;
		}

		/**
		 * TODO: documentation
		 * 
		 * @param rs
		 * @param qinfo
		 * @return
		 * @throws SQLException
		 */
		/*
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
		}*/

		/**
		 * Marshals survey data in a result set from the MobSOS database to a JSON representation.
		 */
		@SuppressWarnings("unchecked")
		private JSONObject readSurveyFromResultSet(ResultSet rs) throws SQLException {

			JSONObject o = new JSONObject();

			o.put("id", rs.getInt("id"));
			o.put("name", rs.getString("name"));
			o.put("description", rs.getString("description"));
			o.put("owner", rs.getString("owner"));
			o.put("organization", rs.getString("organization"));
			o.put("logo", rs.getString("logo"));
			o.put("resource", rs.getString("resource"));
			o.put("resource-label", rs.getString("resource_label"));
			o.put("qid", rs.getInt("qid"));

			long ts_start = rs.getTimestamp("start").getTime();
			long ts_end = rs.getTimestamp("end").getTime();

			DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
			dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

			String d_start = dateFormat.format(new Date(ts_start));
			String d_end = dateFormat.format(new Date(ts_end));

			o.put("start", d_start);
			o.put("end", d_end);
			o.put("lang", rs.getString("lang"));

			return o;
		}

		/**
		 * Marshals questionnaire data in a result set from the MobSOS database to a JSON representation.
		 */
		@SuppressWarnings("unchecked")
		private JSONObject readQuestionnaireFromResultSet(ResultSet rs) throws SQLException {

			JSONObject o = new JSONObject();
			o.put("id", rs.getInt("id"));
			o.put("name", rs.getString("name"));
			o.put("description", rs.getString("description"));
			o.put("owner", rs.getString("owner"));
			o.put("organization", rs.getString("organization"));
			o.put("logo", rs.getString("logo"));
			o.put("lang", rs.getString("lang"));

			return o;
		}

		/**
		 * Checks if for a given survey users have submitted responses and how many.
		 * 
		 * @param sid
		 * @return int number of responses submitted
		 */
		private int countResponses(int sid) {
			// TODO: implement method
			// some query with "select count(distinct()) from response where "...
			return 0;
		}

		/**
		 * Retrieves identifier of questionnaire for given survey or -1 if no questionnaire was defined, yet.
		 */
		private int getQuestionnaireIdForSurvey(int sid) throws SQLException {

			Connection conn = null;
			PreparedStatement stmt = null;
			ResultSet rs = null;

			try {
				conn = dataSource.getConnection();
				stmt = conn.prepareStatement("select qid from " + service.jdbcSchema + ".survey where id = ?");
				stmt.setInt(1, sid);

				rs = stmt.executeQuery();

				if (!rs.isBeforeFirst()) {
					return -1;
				} else {
					rs.next();
					return rs.getInt("qid");
				}

			} catch (SQLException | UnsupportedOperationException e) {
				throw e;
			} finally {
				try {
					if (rs != null) {
						rs.close();
					}
				} catch (Exception e) {
					throw e;
				}
				try {
					if (stmt != null) {
						stmt.close();
					}
				} catch (Exception e) {
					throw e;
				}
				try {
					if (conn != null) {
						conn.close();
					}
				} catch (Exception e) {
					throw e;
				}
			}
		}

		/**
		 * Parses incoming content to a survey JSON representation including checks for completeness, illegal fields and
		 * values.
		 */
		private JSONObject parseSurvey(String content) throws IllegalArgumentException {

			JSONObject o;
			try {
				o = (JSONObject) JSONValue.parseWithException(content);
			} catch (ParseException e1) {
				throw new IllegalArgumentException("Survey data *" + content + "* is not valid JSON!");
			}
			// check result for unknown illegal fields. If so, parsing fails.
			String[] fields = { "id", "owner", "organization", "logo", "name", "description", "resource",
					"resource-label", "start", "end", "lang", "qid" };
			for (Object key : o.keySet()) {
				if (!Arrays.asList(fields).contains(key)) {

					throw new IllegalArgumentException("Illegal survey field '" + key + "' detected!");

				} else {
					if (key.equals("name") && !(o.get(key) instanceof String)) {
						throw new IllegalArgumentException(
								"Illegal value for survey field 'name'. Should be a string.");
					} else if (key.equals("description") && !(o.get(key) instanceof String)) {
						throw new IllegalArgumentException(
								"Illegal value for survey field 'description'. Should be a string.");
					} else if (key.equals("organization") && !(o.get(key) instanceof String)) {
						throw new IllegalArgumentException(
								"Illegal value for survey field 'organization'. Should be a string.");
					} else if (key.equals("logo")) {
						try {
							URL u = new URL((String) o.get(key));
							HttpURLConnection con = (HttpURLConnection) u.openConnection();
							if (404 == con.getResponseCode()) {
								throw new IllegalArgumentException(
										"Illegal value for survey field logo. Should be a valid URL to an image resource.");
							}
							if (!con.getContentType().matches("image/.*")) {
								throw new IllegalArgumentException(
										"Illegal value for survey field logo. Should be a valid URL to an image resource.");
							}
						} catch (MalformedURLException e) {
							throw new IllegalArgumentException(
									"Illegal value for survey field 'logo'. Should be a valid URL to an image resource.");
						} catch (IOException e) {
							throw new IllegalArgumentException(
									"Illegal value for survey field 'logo'. Should be a valid URL to an image resource.");
						}
					} else if (key.equals("resource")) {
						Response h = getClientMetadata((String) o.get(key));
						if (h.getStatus() == 404) {
							throw new IllegalArgumentException(
									"Illegal value for survey field 'resource'. Should be an existing OpenID Client ID.");
						}
						/*try {
							URL u = new URL((String) o.get(key));
							HttpURLConnection con = (HttpURLConnection) u.openConnection();
							if(404 == con.getResponseCode()){
								throw new IllegalArgumentException("Illegal value for survey field 'resource'. Should be a valid URL.");
							}
						} catch (MalformedURLException e) {
							throw new IllegalArgumentException("Illegal value for survey field 'resource'. Should be a valid URL.");
						} catch (IOException e) {
							throw new IllegalArgumentException("Illegal value for survey field 'resource'. Should be a valid URL.");
						}*/
					} else if (key.equals("resource-label") && !(o.get(key) instanceof String)) {
						throw new IllegalArgumentException(
								"Illegal value for survey field 'resource-label'. Should be a string.");
					}else if (key.equals("start")) {
						try {
							DatatypeConverter.parseDateTime((String) o.get("start"));
						} catch (IllegalArgumentException e) {
							throw new IllegalArgumentException(
									"Illegal value for survey field 'start'. Should be an ISO-8601 formatted time string.");
						}
					} else if (key.equals("end")) {
						try {
							DatatypeConverter.parseDateTime((String) o.get("end"));
						} catch (IllegalArgumentException e) {
							throw new IllegalArgumentException(
									"Illegal value for survey field 'end'. Should be an ISO-8601 formatted time string.");
						}
					} else if (key.equals("lang")) {

						String lang = (String) o.get(key);

						Pattern p = Pattern.compile("[a-z]+-[A-Z]+");
						Matcher m = p.matcher(lang);

						// do not iterate over all locales found, but only use first option with highest preference.

						@SuppressWarnings("unused")
						Locale l = null;

						if (m.find()) {
							String[] tokens = m.group().split("-");
							l = new Locale(tokens[0], tokens[1]);
						} else {
							throw new IllegalArgumentException(
									"Illegal value for survey field 'lang'. Should be a valid locale such as 'en-US' or 'de-DE'.");
						}
					}
				}
			}

			// check if all necessary fields are specified.
			if (o.get("name") == null || o.get("organization") == null || o.get("logo") == null
					|| o.get("description") == null || o.get("resource") == null || o.get("resource-label") == null
					|| o.get("start") == null || o.get("end") == null || o.get("lang") == null) {
				throw new IllegalArgumentException(
						"Survey data incomplete! All fields name, organization, logo, description, resource, resource-label, start, end, and lang must be defined!");
			}

			try {
				// Convert times to UTC
				String utcStart = Utils.convertTimeToUTC((String) o.get("start"), TimeZone.getTimeZone("Europe/Berlin"));
				String utcEnd = Utils.convertTimeToUTC((String) o.get("end"), TimeZone.getTimeZone("Europe/Berlin"));
				o.put("start", utcStart);
				o.put("end", utcEnd);
			} catch (java.text.ParseException e) {
				throw new IllegalArgumentException("Error parsing start/end times");
			}

			// finally check time integrity constraint: start must be before end (possibly not enforced by database;
			// mySQL
			// does not support this check)
			long d_start = DatatypeConverter.parseDateTime((String) o.get("start")).getTimeInMillis();
			long d_end = DatatypeConverter.parseDateTime((String) o.get("end")).getTimeInMillis();

			if (d_start >= d_end) {
				throw new IllegalArgumentException("Survey data invalid! Start time must be before end time!");
			}

			return o;
		}

		/**
		 * Parses incoming content to a questionnaire JSON representation including checks for completeness, illegal
		 * fields and values.
		 */
		private JSONObject parseQuestionnaire(String content) throws IllegalArgumentException {

			JSONObject o;
			try {
				o = (JSONObject) JSONValue.parseWithException(content);
			} catch (ParseException e1) {
				throw new IllegalArgumentException("Questionnaire data *" + content + "* is not valid JSON!");
			}

			// check result for unknown illegal fields. If so, parsing fails.
			String[] fields = { "id", "owner", "organization", "logo", "name", "description", "lang" };
			for (Object key : o.keySet()) {
				if (!Arrays.asList(fields).contains(key)) {

					throw new IllegalArgumentException("Illegal questionnaire field '" + key + "' detected!");

				} else {
					if (key.equals("name") && !(o.get(key) instanceof String)) {
						throw new IllegalArgumentException(
								"Illegal value for questionnaire field 'name'. Should be a string.");
					} else if (key.equals("description") && !(o.get(key) instanceof String)) {
						throw new IllegalArgumentException(
								"Illegal value for questionnaire field 'description'. Should be a string.");
					} else if (key.equals("organization") && !(o.get(key) instanceof String)) {
						throw new IllegalArgumentException(
								"Illegal value for questionnaire field 'organization'. Should be a string.");
					} else if (key.equals("logo")) {
						try {
							URL u = new URL((String) o.get(key));
							HttpURLConnection con = (HttpURLConnection) u.openConnection();
							if (404 == con.getResponseCode()) {
								throw new IllegalArgumentException(
										"Illegal value for questionnaire field 'logo'. Should be a valid URL to an image resource.");
							}
							if (!con.getContentType().matches("image/.*")) {
								throw new IllegalArgumentException(
										"Illegal value for questionnaire field 'logo'. Should be a valid URL to an image resource.");
							}
						} catch (MalformedURLException e) {
							throw new IllegalArgumentException(
									"Illegal value for questionnaire field 'logo'. Should be a valid URL to an image resource.");
						} catch (IOException e) {
							throw new IllegalArgumentException(
									"Illegal value for questionnaire field 'logo'. Should be a valid URL to an image resource.");
						}
					} else if (key.equals("lang")) {

						String lang = (String) o.get(key);

						Pattern p = Pattern.compile("[a-z]+-[A-Z]+");
						Matcher m = p.matcher(lang);

						// do not iterate over all locales found, but only use first option with highest preference.

						@SuppressWarnings("unused")
						Locale l = null;

						if (m.find()) {
							String[] tokens = m.group().split("-");
							l = new Locale(tokens[0], tokens[1]);
							// l = new Locale("zz","ZZ");
							// System.out.println("Locale: " + l.getDisplayCountry() + " " + l.getDisplayLanguage());
						} else {
							throw new IllegalArgumentException(
									"Illegal value for questionnaire field 'lang'. Should be a valid locale such as en-US or de-DE");
						}
					}
				}
			}

			// check if all necessary fields are specified.
			if (o.get("name") == null || o.get("organization") == null || o.get("logo") == null
					|| o.get("description") == null || o.get("lang") == null) {
				throw new IllegalArgumentException(
						"Questionnaire data incomplete! All fields name, organization, logo, description, and lang must be defined!");
			}

			return o;
		}

		/**
		 * Stores a new survey described with JSON into the MobSOS database. The MobSOS database thereby generates a new
		 * id returned by this method.
		 * 
		 * @throws ParseException
		 */
		private int storeNewSurvey(JSONObject survey) throws IllegalArgumentException, SQLException, ParseException {

			Connection conn = null;
			PreparedStatement stmt = null;
			ResultSet rset = null;

			String sub = (String) getActiveUserInfo().get("sub");

			try {
				conn = dataSource.getConnection();
				stmt = conn.prepareStatement(
						"insert into " + service.jdbcSchema
								+ ".survey(owner, organization, logo, name, description, resource, resource_label, start, end, lang ) values (?,?,?,?,?,?,?,?,?,?)",
						Statement.RETURN_GENERATED_KEYS);

				stmt.clearParameters();
				stmt.setString(1, sub); // active agent becomes owner automatically
				stmt.setString(2, (String) survey.get("organization"));
				stmt.setString(3, (String) survey.get("logo"));
				stmt.setString(4, (String) survey.get("name"));
				stmt.setString(5, (String) survey.get("description"));
				stmt.setString(6, (String) survey.get("resource"));
				stmt.setString(7, (String) survey.get("resource-label"));
				stmt.setTimestamp(8,
						new Timestamp(DatatypeConverter.parseDateTime((String) survey.get("start")).getTimeInMillis()));
				stmt.setTimestamp(9,
						new Timestamp(DatatypeConverter.parseDateTime((String) survey.get("end")).getTimeInMillis()));
				stmt.setString(10, (String) survey.get("lang"));

				stmt.executeUpdate();
				ResultSet rs = stmt.getGeneratedKeys();

				if (rs.next()) {
					return rs.getInt(1);
				} else {
					throw new NoSuchElementException("No new survey was created!");
				}

			} catch (UnsupportedOperationException e) {
				e.printStackTrace();
			} finally {
				try {
					if (rset != null) {
						rset.close();
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
				try {
					if (stmt != null) {
						stmt.close();
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
				try {
					if (conn != null) {
						conn.close();
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			throw new NoSuchElementException("No new survey was created!");
		}

		/**
		 * Stores a new questionnaire described with JSON into the MobSOS database. The MobSOS database thereby
		 * generates a new id returned by this method.
		 * 
		 * @throws UnsupportedEncodingException
		 * @throws ParseException
		 */
		private int storeNewQuestionnaire(JSONObject questionnaire)
				throws IllegalArgumentException, SQLException, UnsupportedEncodingException, ParseException {

			String sub = (String) getActiveUserInfo().get("sub");

			Connection conn = null;
			PreparedStatement stmt = null;
			ResultSet rset = null;

			try {
				conn = dataSource.getConnection();
				stmt = conn.prepareStatement(
						"insert into " + service.jdbcSchema
								+ ".questionnaire(owner, organization, logo, name, description, lang) values (?,?,?,?,?,?)",
						Statement.RETURN_GENERATED_KEYS);

				stmt.clearParameters();
				stmt.setString(1, sub); // active agent becomes owner automatically
				stmt.setString(2, (String) questionnaire.get("organization"));
				stmt.setString(3, (String) questionnaire.get("logo"));
				stmt.setString(4, (String) questionnaire.get("name"));
				stmt.setString(5, (String) questionnaire.get("description"));
				stmt.setString(6, (String) questionnaire.get("lang"));

				stmt.executeUpdate();
				ResultSet rs = stmt.getGeneratedKeys();

				if (rs.next()) {
					return rs.getInt(1);
				} else {
					throw new NoSuchElementException("No new questionnaire was created!");
				}

			} catch (UnsupportedOperationException e) {
				e.printStackTrace();
			} finally {
				try {
					if (rset != null) {
						rset.close();
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
				try {
					if (stmt != null) {
						stmt.close();
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
				try {
					if (conn != null) {
						conn.close();
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			throw new NoSuchElementException("No new questionnaire was created!");
		}

		/**
		 * Validates questionnaire forms and answers against the MobSOS Questionnaire XML Schema. Returns a valid
		 * document object in case input data is both well-formed and valid. Throws an exception in all other cases.
		 * 
		 * @param data
		 * @throws SAXException
		 * @throws IOException
		 */
		private Document validateQuestionnaireData(String data) throws SAXException, IOException {
			// parse and validate.
			ByteArrayInputStream stringIS = new ByteArrayInputStream(data.getBytes());
			Document doc = service.parser.parse(stringIS);
			service.validator.validate(new DOMSource(doc));
			return doc;
		}

		/**
		 * TODO: write documentation
		 * 
		 * @param response
		 * @return
		 */
		@SuppressWarnings("unchecked")
		private JSONObject convertResponseXMLtoJSON(Document response) {

			JSONObject result = new JSONObject();

			NodeList qs = response.getDocumentElement().getElementsByTagNameNS(MOBSOS_QUESTIONNAIRE_NS, "Question");

			for (int i = 0; i < qs.getLength(); i++) {
				Element q = (Element) qs.item(i);
				String key = q.getAttribute("qid");
				String val = q.getTextContent().trim();
				result.put(key, val);
			}
			return result;
		}

		/**
		 * TODO: write documentation
		 * 
		 * @param form
		 * @param response
		 * @return
		 */
		@SuppressWarnings("unchecked")
		private JSONObject validateResponse(Document form, JSONObject response) {
			JSONObject result = new JSONObject();

			JSONObject questions = extractQuestionInformation(form);

			// then iterate over all question items in the submitted answer and check, if
			// they fulfill all constraints.

			Iterator<String> ait = response.keySet().iterator();
			while (ait.hasNext()) {

				String qid = ait.next();
				String qval = (String) response.get(qid);

				// System.out.println("Submitted Question ID: "+q.getAttribute("qid"));

				// if question provided in answer is not contained in questionnaire, the answer does not match the
				// questionnaire.
				if (!questions.keySet().contains(qid)) {
					throw new IllegalArgumentException("Questionnaire answer does not match form! Question ID " + qid
							+ " is not defined in questionnaire.");
				}

				// if question provided in answer is contained in questionnaire, check further properties...
				JSONObject question = (JSONObject) questions.get(qid);

				// for each type check further constraints
				String type = (String) question.get("type");
				if (type.equals("qu:DichotomousQuestionPageType")) {

					// for dichotomous questions the only two possible answers are 0 and 1.
					if (!qval.equals("0") && !qval.equals("1")) {
						throw new IllegalArgumentException(
								"Questionnaire answer does not match questionnaire! The value submitted for question "
										+ qid + " is expected to be either 0 or 1, but was " + qval + "!");
					} else {
						// everything is ok with this question answer.
						// remove entry from hashtable, write entry to result object
						questions.remove(qid);
						result.put(qid, qval);
					}
				} else if (type.equals("qu:OrdinalScaleQuestionPageType")) {
					// for ordinal scale questions the answer must be parseable as an integer,
					// which is
					try {
						int qvali = Integer.parseInt(qval);

						if (qvali > (int) question.get("maxval") || qvali < (int) question.get("minval")) {
							throw new IllegalArgumentException(
									"Questionnaire answer does not match questionnaire! The value submitted for question "
											+ qid + " is expected to be between " + question.get("minval") + " and "
											+ question.get("maxval") + ", but was " + qvali + "!");
						} else {
							// everything is ok with this question answer.
							// remove entry from hashtable
							questions.remove(qid);
							result.put(qid, qval);
						}
					} catch (NumberFormatException e) {
						throw new IllegalArgumentException(
								"Questionnaire answer does not match questionnaire! The value submitted for question "
										+ qid + " is expected to be parseable as an integer!");
					}

				} else if (type.equals("qu:FreeTextQuestionPageType")) {
					// nothing to check for freetext question pages. Any text can be entered.
					questions.remove(qid);
					result.put(qid, qval);
				}

			}

			Iterator<String> remainingqids = questions.keySet().iterator();
			while (remainingqids.hasNext()) {
				String qid = remainingqids.next();
				int requireds = (int) ((JSONObject) questions.get(qid)).get("required");
				if (requireds == 1) {
					throw new IllegalArgumentException(
							"Questionnaire answer does not match questionnaire! The mandatory question " + qid
									+ " was not answered!");
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
		@SuppressWarnings("unused")
		private String getStringFromDoc(org.w3c.dom.Document doc) {
			DOMImplementationLS domImplementation = (DOMImplementationLS) doc.getImplementation();
			LSSerializer lsSerializer = domImplementation.createLSSerializer();
			return lsSerializer.writeToString(doc);
		}

		/**
		 * Extracts all question information from a questionnaire form given as XML document. Information is returned as
		 * a JSONObject of the following form:
		 * 
		 * {<QuestionID1>:<QuestionInfo1>,...}, where <QuestionInfoN> is again a JSONObject with the following fields: *
		 * name: name of the question (all) * required: is an answer required or optional? (all) * type: question type
		 * (one of qu:OrdinalScaleQuestionPageType, qu:DichotomousQuestionPageType, qu:FreeTextQuestionPageType) *
		 * defval: default value (only for ordinal scale and dichotomous) * min/maxvalue: minimum/maximum value (only
		 * for ordinal scale) * min/maxlabel: label for minimum/maximum value (only for dichotomous and ordinal scale)
		 * 
		 * @param questionnaireDocument
		 * @return
		 */
		@SuppressWarnings("unchecked")
		private JSONObject extractQuestionInformation(Document questionnaireDocument) {

			JSONObject questions = new JSONObject();

			NodeList nodeList = questionnaireDocument.getElementsByTagNameNS(MOBSOS_QUESTIONNAIRE_NS, "Page");
			for (int i = 0; i < nodeList.getLength(); i++) {
				Node node = nodeList.item(i);
				if (node.getNodeType() == Node.ELEMENT_NODE) {
					Element e = (Element) node;
					if (e.getAttribute("xsi:type").endsWith("QuestionPageType")) {
						JSONObject question = new JSONObject();
						String qtype = e.getAttribute("xsi:type");

						String required = e.getAttribute("required");
						if (required == null || required.equals("false")) {
							question.put("required", 0);
						} else if (required != null && required.equals("true")) {
							question.put("required", 1);
						}
						question.put("name", e.getAttribute("name"));
						question.put("type", e.getAttribute("xsi:type"));

						if ("qu:OrdinalScaleQuestionPageType".equals(qtype)
								|| "qu:DichotomousQuestionPageType".equals(qtype)) {
							// question.put("defval",Integer.parseInt(e.getAttribute("defval")));
							question.put("minlabel", e.getAttribute("minlabel"));
							question.put("maxlabel", e.getAttribute("maxlabel"));

							if ("qu:OrdinalScaleQuestionPageType".equals(qtype)) {
								question.put("minval", Integer.parseInt(e.getAttribute("minval")));
								question.put("maxval", Integer.parseInt(e.getAttribute("maxval")));
							}
						}
						Node instructionNode = e.getElementsByTagName("qu:Instructions").item(0);
						question.put("instructions",instructionNode.getTextContent());

						questions.put(e.getAttribute("qid"), question);
					}
				}
			}
			return questions;
		}

		/**
		 * TODO: write documentation
		 * 
		 * @param onAction
		 * @return
		 */
		private Response internalError(String onAction) {
			return Response.status(Status.INTERNAL_SERVER_ERROR).entity("Internal error while " + onAction + "!")
					.type(MediaType.TEXT_PLAIN).build();
		}

		/**
		 * TODO: write documentation
		 * 
		 * @param ds
		 */
		@SuppressWarnings("unused")
		private static void printDataSourceStats(DataSource ds) {
			System.out.println("Data Source Stats: ");
			BasicDataSource bds = (BasicDataSource) ds;
			System.out.println("  Num Active: " + bds.getNumActive());
			System.out.println("  Num Idle: " + bds.getNumIdle());
			System.out.println("  Max Idle: " + bds.getMaxIdle());
			System.out.println("  Max Total: " + bds.getMaxTotal());
			System.out.println("  Max Conn Lifetime Millis: " + bds.getMaxConnLifetimeMillis());
			System.out.println("  Min Idle: " + bds.getMinIdle());
			System.out.println("  Min Evictable Idletime Millis: " + bds.getMinEvictableIdleTimeMillis());
			System.out.println("  Validation Query: " + bds.getValidationQuery());
		}

		/**
		 * Given a survey identifier, checks if the respective survey already defines a database view for survey
		 * responses.
		 * 
		 * @param sid
		 * @return
		 * @throws SQLException
		 */
		private boolean existsResponseView(int sid) throws SQLException {
			try {
				Connection c = null;
				PreparedStatement s = null;
				ResultSet rs = null;

				// +++ dsi
				try {
					c = dataSource.getConnection();
					s = c.prepareStatement("show tables in " + service.jdbcSchema + " like ?");
					s.setString(1, "responses_survey_" + sid);
					rs = s.executeQuery();

					// view does not exist
					if (!rs.isBeforeFirst()) {
						return false;
					} else {
						return true;
					}

				} catch (Exception e) {
					throw e;
				} finally {
					try {
						if (rs != null) {
							rs.close();
						}
					} catch (Exception e) {
						throw e;
					}
					try {
						if (s != null) {
							s.close();
						}
					} catch (Exception e) {
						throw e;
					}
					try {
						if (c != null) {
							c.close();
						}
					} catch (Exception e) {
						throw e;
					}
				}
				// --- dsi

			} catch (Exception e) {
				throw e;
			}
		}

		/**
		 * Given a survey and its corresponding questionnaire form, creates a new database view for convenient access to
		 * survey responses.
		 * 
		 * @param sid
		 * @param form
		 * @throws SQLException
		 */
		@SuppressWarnings("unchecked")
		private void createResponseView(int sid, Document form) throws SQLException {
			try {
				JSONObject questions = extractQuestionInformation(form);

				// generate create view statement for response view

				// example:
				// create view mobsos.responses_survey_1 as
				// select uid, sid, s.resource as cid,
				// MAX(IF(qkey = 'A.2.1', cast(qval as unsigned), NULL)) AS "A.2.1",
				// MAX(IF(qkey = 'A.2.2', cast(qval as unsigned), NULL)) AS "A.2.2",
				// MAX(IF(qkey = 'A.2.3', qval, NULL)) AS "A.2.3"
				// from mobsos.response r join mobsos.survey s on (s.id = r.sid) where sid = 1 group by uid, cid;

				String sql = "create view " + service.jdbcSchema + ".responses_survey_" + sid + " as ";
				sql += "select uid, sid, s.resource as cid, \n";

				Iterator<String> it = questions.keySet().iterator();

				while (it.hasNext()) {
					String key = it.next();

					JSONObject def = (JSONObject) questions.get(key);
					if ("qu:FreeTextQuestionPageType".equals(def.get("type"))) {
						sql += "  MAX(IF(qkey = '" + key + "', qval, NULL)) AS \"" + key + "\"";
					} else if ("qu:DichotomousQuestionPageType".equals(def.get("type"))
							|| "qu:OrdinalScaleQuestionPageType".equals(def.get("type"))) {
						sql += "  MAX(IF(qkey = '" + key + "', cast(qval as unsigned), NULL)) AS \"" + key + "\"";
					}
					if (it.hasNext()) {
						sql += ",\n";
					} else {
						sql += "\n";
					}
				}

				sql += " from " + service.jdbcSchema + ".response r join " + service.jdbcSchema
						+ ".survey s on (s.id = r.sid) where sid = " + sid + " group by uid, cid;";

				System.out.println("SQL for creating survey response view for survey " + sid + ": \n" + sql);

				Connection c = null;
				PreparedStatement s = null;
				ResultSet rs = null;

				// +++ dsi
				try {
					c = dataSource.getConnection();
					s = c.prepareStatement(sql);
					s.executeUpdate();

				} catch (Exception e) {
					throw e;
				} finally {
					try {
						if (rs != null) {
							rs.close();
						}
					} catch (Exception e) {
						throw e;
					}
					try {
						if (s != null) {
							s.close();
						}
					} catch (Exception e) {
						throw e;
					}
					try {
						if (c != null) {
							c.close();
						}
					} catch (Exception e) {
						throw e;
					}
				}
				// --- dsi

			} catch (Exception e) {
				throw e;
			}
		}

		/**
		 * localizes the given String t according to locale l. If no resource bundle exists for locale l, fall back to
		 * English. Input string t is expected to contain placeholders ${k}, where k is a key defined in the
		 * ResourceBundle.
		 * 
		 * @param t a String to be localized
		 * @param lang a Locale
		 * @return
		 */
		private String i18n(String t, String lang) {

			// now parse locales from accept-language header
			Pattern p = Pattern.compile("[a-z]+-[A-Z]+");
			Matcher m = p.matcher(lang);

			// do not iterate over all locales found, but only use first option with highest preference.

			Locale l = null;

			if (m.find()) {
				String[] tokens = m.group().split("-");
				l = new Locale(tokens[0], tokens[1]);
				// System.out.println("Locale: " + l.getDisplayCountry() + " " + l.getDisplayLanguage());
			}

			ResourceBundle messages = ResourceBundle.getBundle("MessageBundle", l);
			Enumeration<String> e = messages.getKeys();

			while (e.hasMoreElements()) {

				String key = e.nextElement();
				String translation = messages.getString(key);
				t = t.replaceAll("\\$\\{" + key + "\\}", escapeHtml4(translation));
			}

			return t;
		}

		@SuppressWarnings("unchecked")
		private JSONObject getActiveUserInfo() throws ParseException {
			if (Context.getCurrent().getMainAgent() instanceof UserAgent) {
				UserAgent me = (UserAgent) Context.getCurrent().getMainAgent();
				JSONObject o = new JSONObject();
				if (Context.get().getMainAgent() instanceof AnonymousAgent) {
					o.put("sub", "anonymous");
				} else {

					String md5ide = new String("" + me.getIdentifier());
					o.put("sub", md5ide);
				}
				return o;
			} else {
				return new JSONObject();
			}
		}
	}
}
