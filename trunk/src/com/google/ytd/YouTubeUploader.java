/* Copyright (c) 2010 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.ytd;

import android.location.Location;
import android.os.Handler;
import android.util.Log;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

public class YouTubeUploader {
  private static final String TAG = "YouTubeUploader";
  private static final int CHUNK_SIZE = 512 * 1024; //512kb
  //TODO: Change this from staging server once resumable uploads goes live.
  private static final String INITIAL_UPLOAD_URL = "http://uploads.stage.gdata.youtube.com/resumable/feeds/api/users/default/uploads";
  private static final String ATOM_FORMAT = "<?xml version=\"1.0\"?> <entry xmlns=\"http://www.w3.org/2005/Atom\" xmlns:media=\"http://search.yahoo.com/mrss/\" xmlns:yt=\"http://gdata.youtube.com/schemas/2007\"> <media:group> <media:title type=\"plain\">%s</media:title> <media:description type=\"plain\">%s</media:description> <media:category scheme=\"http://gdata.youtube.com/schemas/2007/categories.cat\">%s</media:category> <media:keywords>%s</media:keywords> </media:group> </entry>";
  private static final String ATOM_GEO_FORMAT = "<?xml version=\"1.0\"?> <entry xmlns=\"http://www.w3.org/2005/Atom\" xmlns:media=\"http://search.yahoo.com/mrss/\" xmlns:yt=\"http://gdata.youtube.com/schemas/2007\"> <media:group> <media:title type=\"plain\">%s</media:title> <media:description type=\"plain\">%s</media:description> <media:category scheme=\"http://gdata.youtube.com/schemas/2007/categories.cat\">%s</media:category> <media:keywords>%s</media:keywords> </media:group> <georss:where xmlns:georss=\"http://www.georss.org/georss\" xmlns:gml=\"http://www.opengis.net/gml\"><gml:Point xmlns:gml=\"http://www.opengis.net/gml\"><gml:pos>%f %f</gml:pos></gml:Point></georss:where> </entry>";
  private static final int UNKNOWN_SIZE = -1;
  public static final int START_MESSAGE = 0;
  public static final int PROGRESS_MESSAGE = 1;
  public static final int SUCCESS_RESPONSE = 2;
  public static final int ERROR_RESPONSE = 3;
  
  private String authSubToken;
  private String filePath;
  private String youTubeApiKey;
  private String title;
  private String description;
  private String category;
  private String keywords;
  private String fileUploadUrl;
  private String ytdSubmitUrl;
  private int fileSize;
  private Location location;
  private Handler callbackHandler;
  
  
  public YouTubeUploader(String authSubToken, String youTubeApiKey) {
    this.authSubToken = authSubToken;
    this.youTubeApiKey = youTubeApiKey;
    fileSize = UNKNOWN_SIZE;
  }
  
  public void upload() {
    try {
      String videoId;
      if (fileUploadUrl == null) {
        fileUploadUrl = uploadMetadata();
        
        if (callbackHandler != null) {
          HashMap<String, String> params = new HashMap<String, String>();
          params.put("filePath", filePath);
          params.put("fileUploadUrl", fileUploadUrl);
          callbackHandler.sendMessage(callbackHandler.obtainMessage(START_MESSAGE, params));
        }
        
        videoId = startFileUpload();
      } else {
        videoId = resumeFileUpload();
      }
      
      Log.d(TAG, String.format("New video created with id '%s'", videoId));
      
      submitVideoToYtd(videoId);

      if (callbackHandler != null) {
        callbackHandler.sendMessage(callbackHandler.obtainMessage(SUCCESS_RESPONSE, videoId));
      }
    } catch (Exception e) {
      Log.e(TAG, "", e);
      if (callbackHandler != null) {
        callbackHandler.sendMessage(callbackHandler.obtainMessage(ERROR_RESPONSE, e));
      }
    }
  }
  
  private String uploadMetadata() throws IOException {
    String uploadUrl = INITIAL_UPLOAD_URL;
    
    HttpURLConnection urlConnection = getUrlConnection(uploadUrl);
    urlConnection.setRequestMethod("POST");
    urlConnection.setDoOutput(true);
    urlConnection.setRequestProperty("Content-Type", "application/atom+xml");
    urlConnection.setRequestProperty("Slug", filePath);
    String atomData;
    if (location == null) {
      atomData = String.format(ATOM_FORMAT, title, description, category, keywords);
    } else {
      atomData = String.format(ATOM_GEO_FORMAT, title, description, category, keywords,
              location.getLatitude(), location.getLongitude());
    }
    Log.d(TAG, String.format("Uploading metadata %s to %s", atomData, uploadUrl));
    
    OutputStreamWriter outStreamWriter = new OutputStreamWriter(urlConnection.getOutputStream());
    outStreamWriter.write(atomData);
    outStreamWriter.close();
    
    int responseCode = urlConnection.getResponseCode();
    if (responseCode < 200 || responseCode >= 300) {
      throw new IOException(String.format("Receieved response message '%s' (code %d)" +
          " when POSTing to %s", urlConnection.getResponseMessage(), responseCode,
          urlConnection.getURL()));
    }
    
    return urlConnection.getHeaderField("Location");
  }
  
  private String startFileUpload() throws IOException, ParserConfigurationException, SAXException {
    return uploadFile(0);
  }
  
  private String resumeFileUpload() throws IOException, ParserConfigurationException, SAXException {
    HttpURLConnection urlConnection = getUrlConnection(fileUploadUrl);
    urlConnection.setRequestProperty("Content-Range", "bytes */*");
    urlConnection.setRequestMethod("POST");
    urlConnection.setFixedLengthStreamingMode(0);
    
    HttpURLConnection.setFollowRedirects(false);
    
    urlConnection.connect();
    int responseCode = urlConnection.getResponseCode();
    
    if (responseCode >= 300 && responseCode < 400) {
      int nextByteToUpload;
      String range = urlConnection.getHeaderField("Range");
      if (range == null) {
        Log.d(TAG, String.format("POST to %s did not return 'Range' header.", fileUploadUrl));
        nextByteToUpload = 0;
      } else {
        Log.d(TAG, String.format("Range header is '%s'.", range));
        String[] parts = range.split("-");
        if (parts.length > 1) {
          nextByteToUpload = Integer.parseInt(parts[1]);
        } else {
          nextByteToUpload = 0;
        }
      }
      
      return uploadFile(nextByteToUpload);
    } else if (responseCode >= 200 && responseCode < 300) {
      return parseVideoId(urlConnection.getInputStream());
    } else {
      throw new IOException(String.format("Unexpected response for POST to %s: %s " +
      		"(code %d)", fileUploadUrl, urlConnection.getResponseMessage(), responseCode));
    }
  }
  
  private String uploadFile(int nextByteToUpload) throws IOException, ParserConfigurationException, SAXException {
    Log.d(TAG, String.format("Uploading file %s to %s starting at byte %d.", filePath,
            fileUploadUrl, nextByteToUpload));
    
    File file = new File(filePath);
    
    if (fileSize == UNKNOWN_SIZE) {
      long fileSizeLong = file.length();
      if (fileSizeLong > Integer.MAX_VALUE) {
        throw new IllegalStateException("File is too big.");
      }
      fileSize = (int)fileSizeLong;
      Log.d(TAG, String.format("File size of %s is %d", filePath, fileSize));
    }
    
    int effectiveFileSize = fileSize - nextByteToUpload;
    
    HttpURLConnection urlConnection = getUrlConnection(fileUploadUrl);
    urlConnection.setRequestMethod("POST");
    urlConnection.setDoOutput(true);
    urlConnection.setFixedLengthStreamingMode(effectiveFileSize);
    urlConnection.setRequestProperty("Content-Type", "video/3gpp");
    urlConnection.setRequestProperty("Content-Range",
            String.format("bytes %d-%d/%d", nextByteToUpload, fileSize - 1, fileSize));

    OutputStream outStreamWriter = urlConnection.getOutputStream();

    byte[] bytes = new byte[CHUNK_SIZE];
    FileInputStream fileStream = new FileInputStream(file);

    int bytesRead;
    int bytesSoFar = 0;
    fileStream.skip(nextByteToUpload);
    while ((bytesRead = fileStream.read(bytes, 0, CHUNK_SIZE)) != -1) {
      outStreamWriter.write(bytes, 0, bytesRead);
      bytesSoFar += bytesRead;
      Log.d(TAG, String.format("Uploaded %d of %d bytes. [%d]", bytesSoFar, effectiveFileSize, Thread.currentThread().getId()));
      if (callbackHandler != null) {
        callbackHandler.sendMessage(callbackHandler.obtainMessage(PROGRESS_MESSAGE, bytesSoFar,
                effectiveFileSize));
      }
    }
    outStreamWriter.close();
    
    int responseCode = urlConnection.getResponseCode();
    if (responseCode < 200 || responseCode >= 300) {
      throw new IOException(String.format("Receieved response message '%s' (code %d)" +
          " when POSTing to %s", urlConnection.getResponseMessage(), responseCode,
          urlConnection.getURL()));
    }

    return parseVideoId(urlConnection.getInputStream());
  }
  
  private String parseVideoId(InputStream atomDataStream) throws ParserConfigurationException, SAXException, IOException {
    DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
    DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder();
    Document doc = docBuilder.parse(atomDataStream);
    
    NodeList nodes = doc.getElementsByTagNameNS("*", "*");
    for (int i = 0; i < nodes.getLength(); i++) {
      Node node = nodes.item(i);
      String nodeName = node.getNodeName();
      if (nodeName != null && nodeName.equals("yt:videoid")) {
        return node.getFirstChild().getNodeValue();
      }
    }
    
    throw new IllegalStateException("No <videoid> element found in response XML.");
  }

  private HttpURLConnection getUrlConnection(String urlString) throws IOException {
    URL url = new URL(urlString);
    return getUrlConnection(url);
  }

  private HttpURLConnection getUrlConnection(URL url) throws IOException {
    HttpURLConnection connection = (HttpURLConnection)url.openConnection();
    connection.setRequestProperty("Authorization", String.format("AuthSub token=\"%s\"",
            authSubToken));
    connection.setRequestProperty("GData-Version", "2");
    connection.setRequestProperty("X-GData-Client", TAG);
    connection.setRequestProperty("X-GData-Key", String.format("key=%s", youTubeApiKey));

    return connection;
  }
  
  private void submitVideoToYtd(String videoId) throws IOException {
    URL url = new URL(ytdSubmitUrl);
    HttpURLConnection urlConnection = (HttpURLConnection)url.openConnection();
    urlConnection.setDoOutput(true);
    
    OutputStreamWriter outStreamWriter = new OutputStreamWriter(urlConnection.getOutputStream());
    outStreamWriter.write(String.format("videoId=%s&authSubToken=%s", videoId, authSubToken));
    outStreamWriter.close();
    
    int responseCode = urlConnection.getResponseCode();
    if (responseCode < 200 || responseCode >= 300) {
      throw new IOException(String.format("Receieved response message '%s' (code %d)" +
          " when POSTing to %s", urlConnection.getResponseMessage(), responseCode,
          urlConnection.getURL()));
    }
    
    Log.d(TAG, String.format("Successully submitted video id '%s' to %s.", videoId, ytdSubmitUrl));
  }

  public String getAuthSubToken() {
    return authSubToken;
  }

  public void setAuthSubToken(String authSubToken) {
    this.authSubToken = authSubToken;
  }

  public String getFilePath() {
    return filePath;
  }

  public void setFilePath(String filePath) {
    this.filePath = filePath;
  }

  public String getYouTubeApiKey() {
    return youTubeApiKey;
  }

  public void setYouTubeApiKey(String youTubeApiKey) {
    this.youTubeApiKey = youTubeApiKey;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getCategory() {
    return category;
  }

  public void setCategory(String category) {
    this.category = category;
  }

  public String getKeywords() {
    return keywords;
  }

  public void setKeywords(String keywords) {
    this.keywords = keywords;
  }

  public Location getLocation() {
    return location;
  }

  public void setLocation(Location location) {
    this.location = location;
  }

  public Handler getCallbackHandler() {
    return callbackHandler;
  }

  public void setCallbackHandler(Handler callbackHandler) {
    this.callbackHandler = callbackHandler;
  }

  public String getFileUploadUrl() {
    return fileUploadUrl;
  }

  public void setFileUploadUrl(String fileUploadUrl) {
    this.fileUploadUrl = fileUploadUrl;
  }

  public String getYtdSubmitUrl() {
    return ytdSubmitUrl;
  }

  public void setYtdSubmitUrl(String ytdSubmitUrl) {
    this.ytdSubmitUrl = ytdSubmitUrl;
  }
}
