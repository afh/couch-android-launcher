package org.couchdb.android;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.util.Log;

public class CouchCtrlListener {
	
	private String couchUrl;
	
	private String ctrl;
	
	private String adminUser;
	private String adminPass;
	
	private Boolean cancelled = false;
	private Boolean running = false;
	
	public CouchCtrlListener(String couchUrl, String db, String user, String pass) {
		this.couchUrl = couchUrl;
		this.adminUser = user;
		this.adminPass = pass;
		this.ctrl = db + "-ctrl";
	}
	
	public void start() {
		
		Log.v(CouchDB.TAG, "Starting Listener for " + ctrl);

		if (!running) {
			try {
				running = true;
				JSONObject dbInfo = HTTPRequest.get(couchUrl + ctrl, headers());
				int updateSeq = dbInfo.getInt("update_seq");
				changes(updateSeq);
			} catch (JSONException e) {
				e.printStackTrace();
			}
		} else if(running && cancelled) {
			cancelled = false;
		}
	}
	
	private void doReplication(JSONObject json) throws JSONException { 
		HTTPRequest req = HTTPRequest.httpRequest("POST", couchUrl + "_replicate", json.toString(), headers());
		
		// Java will just close the connection when it gets a 404
		// without reading the result
		JSONObject result;
		try { 
			result = new JSONObject(req.result);
		} catch (Exception o) {
			result = new JSONObject();
		}

		result.put("http_status", req.status);
		json.put("result", result);
		json.put("status", "complete");
		String id = json.getString("_id");
		HTTPRequest.httpRequest("PUT", 
				couchUrl + ctrl + "/" + id, json.toString(), headers());			
	}
	
	private void handleChange(JSONObject doc) throws JSONException { 

		if (doc.has("status") && doc.get("status").equals("complete")) { 			
			return;	
		}

		if (doc.has("source") && doc.has("target")) {
			doReplication(doc);
		}		
	}

	private void changes(int seq) throws JSONException {
		while (!cancelled) {			
			String url = couchUrl + ctrl + "/_changes?include_docs=true&feed=longpoll&since=" 
				+ Integer.toString(seq);
			JSONObject json = HTTPRequest.get(url, headers());
			
			if (json != null) { 
				Log.v(CouchDB.TAG, "Received Changes for " + ctrl);
			
				seq = json.getInt("last_seq");
				JSONArray results = json.getJSONArray("results");
				
				for(int i = 0 ; i < results.length(); i++) {
					handleChange(results.getJSONObject(i).getJSONObject("doc"));
				}
			}
		}
		Log.v(CouchDB.TAG, "Changes listener on " + ctrl + " has stopped");
		running = false;
		cancelled = false;
	}
	
	public void cancel() {
		Log.v(CouchDB.TAG, "Cancelling changes listener for " + ctrl);
		cancelled = true;
	}

	private String[][] headers() { 
		String auth = Base64Coder.encodeString(adminUser + ":" + adminPass);
		String[][] headers = {{"Authorization", "Basic " + auth}};
		return headers;
	}
}