/*
Copyright (c) 2014 Dominik Renzel, Peter de Lange, Alexander Ruppert, Advanced Community Information Systems (ACIS) Group,
Chair of Computer Science 5 (Databases & Information Systems), RWTH Aachen University, Germany
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

* Redistributions of source code must retain the above copyright notice, this
list of conditions and the following disclaimer.

* Redistributions in binary form must reproduce the above copyright notice,
this list of conditions and the following disclaimer in the documentation
and/or other materials provided with the distribution.

* Neither the name of the ACIS Group nor the names of its
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

function MobSOSSurveysClient(endpointUrl){

	// care for trailing slash in endpoint URL
	if(endpointUrl.endsWith("/")){
		this._serviceEndpoint = endpointUrl.substr(0,endpointUrl.length-1);
	} else {
		this._serviceEndpoint = endpointUrl;
	}
	
	// remember last page for redirection after OpenID Connect login
	window.localStorage["last_resource"] = window.location.href;
};

MobSOSSurveysClient.prototype.isAnonymous = function(){
	if (oidc_userinfo !== undefined){
		return false;
	} else {
		return true;
	}
};

MobSOSSurveysClient.prototype.navigateTo = function(path){
	var rurl = window.location.href + "/" + path;
	
	if(!this.isAnonymous()){
		if(rurl.indexOf("\?") > 0){
			console.log("Authenticated request... appending token as additional query param");
			rurl += "&access_token=" + window.localStorage["access_token"];
		} else {
			console.log("Authenticated request... appending token as query");
			rurl += "?access_token=" + window.localStorage["access_token"];
		}
	} else {
		console.log("Anonymous request... ");
	}
	
	window.location.href = rurl;
};

MobSOSSurveysClient.prototype.navigateAbsolute = function(rurl){
	
	if(!this.isAnonymous()){
		if(rurl.indexOf("\?") > 0){
			console.log("Authenticated request... appending token as additional query param");
			rurl += "&access_token=" + window.localStorage["access_token"];
		} else {
			console.log("Authenticated request... appending token as query");
			rurl += "?access_token=" + window.localStorage["access_token"];
		}
	} else {
		console.log("Anonymous request... ");
	}
	
	window.location.href = rurl;
};

MobSOSSurveysClient.prototype.createQuestionnaire = function(metadata, form, callback, errorCallback) {
	var that = this;
	
	this.sendRequest("POST",
		"questionnaires",
		metadata,
		"application/json",
		{},
		function(data, type){
			// if creating new questionnaire worked, 
			// send another request uploading questionnaire form
			var id = data.id;
			console.log(id);
			
			that.sendRequest("PUT","questionnaires/" + id + "/form",form,"text/xml",{},
				function(d,t){
					callback(data,type);
				},
				errorCallback
			);
		},
		errorCallback
	);
};

MobSOSSurveysClient.prototype.getQuestionnaires = function(query, full, callback, errorCallback){
	
	var q="";
	
	if(query !== null && query !== ""){
		q = "q="+query;
	}
	
	var f="";
	
	if(full !== null && (full == 0 || full == 1)){
		f = "full="+full;
	}
	
	var qpart;
	
	if(q !== "" && f !== ""){
		qpart = q + "&" + f;
	} else {
		qpart = q + f;
	}
	
	console.log(qpart);
	
	this.sendRequest("GET",
		"questionnaires?" + qpart,
		"",
		"application/json",
		{},
		callback,
		errorCallback);
	
};

MobSOSSurveysClient.prototype.getQuestionnaire = function(id, callback, errorCallback){
	
	this.sendRequest("GET",
		"questionnaires/" + id,
		"",
		"application/json",
		{},
		callback,
		errorCallback);
	
};

MobSOSSurveysClient.prototype.deleteQuestionnaire = function(id, callback, errorCallback){
	this.sendRequest("DELETE",
		"questionnaires/" + id,
		"",
		"",
		{},
		callback,
		errorCallback);
}

MobSOSSurveysClient.prototype.getQuestionnaireForm = function(id, callback, errorCallback){
	
	this.sendRequest("GET",
		"questionnaires/" +id + "/form",
		"",
		"text/xml",
		{},
		callback,
		errorCallback);
	
};

MobSOSSurveysClient.prototype.createSurvey = function(metadata, callback, errorCallback){
	this.sendRequest("POST",
		"surveys",
		metadata,
		"application/json",
		{},
		callback,
		errorCallback
	);
}

MobSOSSurveysClient.prototype.getSurveys = function(query, full, callback, errorCallback){
	
	var q="";
	
	if(query !== null && query !== ""){
		q = "q="+query;
	}
	
	var f="";
	
	if(full !== null && (full == 0 || full == 1)){
		f = "full="+full;
	}
	
	var qpart;
	
	if(q !== "" && f !== ""){
		qpart = q + "&" + f;
	} else {
		qpart = q + f;
	}
	
	console.log(qpart);
	
	this.sendRequest("GET",
		"surveys?" + qpart,
		"",
		"application/json",
		{},
		callback,
		errorCallback);
	
};

MobSOSSurveysClient.prototype.getSurvey = function(id, callback, errorCallback){
	
	this.sendRequest("GET",
		"surveys/" + id,
		"",
		"",
		{"Accept":"application/json"},
		callback,
		errorCallback);
	
};

MobSOSSurveysClient.prototype.getSurveyResponses = function(id, callback, errorCallback){
	
	this.sendRequest("GET",
		"surveys/" + id + "/responses",
		"",
		"text/csv",
		{"Accept":"text/csv"},
		callback,
		errorCallback);
	
};

MobSOSSurveysClient.prototype.setSurveyQuestionnaire = function(id, data, callback, errorCallback){
	this.sendRequest("POST",
		"surveys/" +id + "/questionnaire",
		data,
		"application/json",
		{},
		callback,
		errorCallback);
	
};

MobSOSSurveysClient.prototype.submitSurveyResponse = function(id, data, callback, errorCallback){
	this.sendRequest("POST",
		"surveys/" +id + "/responses",
		data,
		"application/json",
		{},
		callback,
		errorCallback);
	
};

MobSOSSurveysClient.prototype.getResourceMeta = function(uri, callback, errorCallback){
	
	this.sendRequest("POST",
		"resource-meta",
		uri,
		"text/plain",
		{},
		callback,
		errorCallback);
	
};

MobSOSSurveysClient.prototype.getUserInfo = function(callback, errorCallback){
	
	this.sendRequest("GET",
		"userinfo",
		"",
		"",
		{},
		callback,
		errorCallback);
	
};

MobSOSSurveysClient.prototype.sendRequest = function(method, relativePath, content, mime, customHeaders, callback, errorCallback) {
	
	var mtype = "text/plain; charset=UTF-8"
	if(mime !== 'undefined'){
		mtype = mime;
	}
	
	var rurl = this._serviceEndpoint + "/" + relativePath;
	
	if(!this.isAnonymous()){
		if(rurl.indexOf("\?") > 0){
			//console.log("Authenticated request... appending token as additional query param");
			rurl += "&access_token=" + window.localStorage["access_token"];
		} else {
			//console.log("Authenticated request... appending token as query");
			rurl += "?access_token=" + window.localStorage["access_token"];
		}
	} else {
		//console.log("Anonymous request... ");
	}
	
	var ajaxObj = {
		url: rurl, 
		type: method.toUpperCase(),
		data: content,
		contentType: mtype,
		crossDomain: true,
		headers: {},

		error: function (xhr, errorType, error) {
			console.log(error);
			var errorText = error;
			if (xhr.responseText != null && xhr.responseText.trim().length > 0){
				errorText = xhr.responseText;
			}
			errorCallback(errorText);
		},
		success: function (data, status, xhr) {
			var type = xhr.getResponseHeader("Content-Type");
			callback(data, type);
		},
	};
	
	
   
	if (customHeaders !== undefined && customHeaders !== null) {
		$.extend(ajaxObj.headers, customHeaders);
	}
	
	$.ajax(ajaxObj);
};

String.prototype.endsWith = function(suffix) {
    return this.indexOf(suffix, this.length - suffix.length) !== -1;
};