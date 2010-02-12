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

import java.io.File;
import java.util.HashMap;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;

public class VideoUploadActivity extends Activity {
  private static final String TAG = "VideoUploadActivity";
  // Replace with a developer key from http://code.google.com/apis/youtube/dashboard/gwt/index.html#settings
  private static final String YOUTUBE_API_KEY = "";
  // Replace with your YTD App Engine hostname, e.g. ytd-test.appspot.com
  private static final String YTD_SERVER = "";
  private static final String YTD_MOBILE_SUBMISSION_URL = 
    "http://%s/mobile/PersistMobileSubmission";
  private static final String YTD_MOBILE_LOGIN_URL = 
    "http://%s/mobile/MobileAuthSub?protocol=androidytd";
  private static final int VIDEO_CAPTURE_WIDTH = 240;
  private static final int VIDEO_CAPTURE_HEIGHT = 180;
  //If running in the emulator, you'll need to explicitly create a file on your AVD SD card image.
  private static final String DUMMY_MOVIE_FILE = "/sdcard/test.mov";
  
  private String username = null;
  private String authSubToken = null;
  private String filePath = null;
  private String fileUploadUrl = null;
  private CamcorderPreview camcorderPreview = null;
  
  final Handler callbackHandler = new Handler() {
    @SuppressWarnings("unchecked")
    @Override
    public void handleMessage(Message message) {
      Editor editor;
      
      switch (message.what) {
        case YouTubeUploader.START_MESSAGE:
          editor = getSharedPreferences("default", 0).edit();
          HashMap<String, String> messageParams = (HashMap<String, String>)message.obj;
          editor.putString("filePath", messageParams.get("filePath"));
          editor.putString("fileUploadUrl", messageParams.get("fileUploadUrl"));
          editor.commit();
          
          displayMessage("Starting upload.");
          break;
          
        case YouTubeUploader.PROGRESS_MESSAGE:
          if (message.arg1 == message.arg2) {
            displayMessage("YouTube upload complete. Now submitting to YouTube Direct.");
          } else {
            displayMessage(String.format("Uploaded %d of %d bytes.", message.arg1, message.arg2));
          }
          break;
        
        case YouTubeUploader.SUCCESS_RESPONSE:
          displayMessage(String.format("Successfully submitted video with id '%s'.", message.obj));
          findViewById(R.id.UploadProgressBar).setVisibility(View.INVISIBLE);
          ((Button)findViewById(R.id.UploadButton)).setEnabled(true);
          
          editor = getSharedPreferences("default", 0).edit();
          editor.remove("filePath");
          editor.remove("fileUploadUrl");
          editor.commit();
          
          removeFile(filePath);
          break;
        
        case YouTubeUploader.ERROR_RESPONSE:
          Exception e = (Exception)message.obj;
          displayMessage(String.format("Upload failed: %s", e.toString()));
          findViewById(R.id.UploadProgressBar).setVisibility(View.INVISIBLE);
          
          ((Button)findViewById(R.id.ResumeButton)).setEnabled(true);

          break;
      }
    }
  };

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    
    setContentView(R.layout.main);

    Uri uri = this.getIntent().getData();
    if(uri != null) {
      String path = uri.getPath();
      String[] parts = path.split("/", 3);
      if (parts.length == 3) {
        username = parts[1];
        authSubToken = parts[2];

        Editor editor = getSharedPreferences("default", 0).edit();
        editor.putString("username", username);
        editor.putString("authSubToken", authSubToken);
        editor.remove("filePath");
        editor.remove("fileUploadUrl");
        editor.commit();
      }
    } else {
      SharedPreferences preferences = getSharedPreferences("default", 0);
      username = preferences.getString("username", null);
      authSubToken = preferences.getString("authSubToken", null);
      filePath = preferences.getString("filePath", null);
      fileUploadUrl = preferences.getString("fileUploadUrl", null);
    }

    if (username != null && authSubToken != null) {
      displayMessage(String.format("Logged in with YouTube account '%s'.", username));
      ((Button)findViewById(R.id.LogoutButton)).setEnabled(true);
      ((Button)findViewById(R.id.UploadButton)).setEnabled(true);
      
      if (filePath != null && fileUploadUrl != null) {
        ((Button)findViewById(R.id.ResumeButton)).setEnabled(true);
      } else {
        ((Button)findViewById(R.id.ResumeButton)).setEnabled(false);
      }
    } else {
      ((Button)findViewById(R.id.LogoutButton)).setEnabled(false);
      ((Button)findViewById(R.id.UploadButton)).setEnabled(false);
      ((Button)findViewById(R.id.ResumeButton)).setEnabled(false);
      displayMessage("Please log in to YouTube.");
    }
  }
  
  public void onResumeClick(View v) {
    final YouTubeUploader ytUploader = new YouTubeUploader(authSubToken, YOUTUBE_API_KEY);
    ytUploader.setFilePath(filePath);
    ytUploader.setYtdSubmitUrl(String.format(YTD_MOBILE_SUBMISSION_URL, YTD_SERVER));
    ytUploader.setFileUploadUrl(fileUploadUrl);
    ytUploader.setCallbackHandler(callbackHandler);
    
    displayMessage("Resuming upload.");

    findViewById(R.id.UploadProgressBar).setVisibility(View.VISIBLE);
    new Thread(new Runnable() {
      public void run() {
        ytUploader.upload();
      }
    }).start();
  }

  public void onUploadClick(View v) {
    if (camcorderPreview == null) {
      try {
        camcorderPreview = new CamcorderPreview(this);
      } catch (Exception e) {
        Log.e(TAG, "", e);
      }

      DisplayMetrics dm = new DisplayMetrics();
      getWindowManager().getDefaultDisplay().getMetrics(dm);
      int width = (int)(VIDEO_CAPTURE_WIDTH * dm.density);
      int height = (int)(VIDEO_CAPTURE_HEIGHT * dm.density);
      
      RelativeLayout relativeLayout = (RelativeLayout)findViewById(R.id.RelativeLayout01);
      RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(width, height);
      layoutParams.addRule(RelativeLayout.CENTER_HORIZONTAL);
      layoutParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
      relativeLayout.addView(camcorderPreview, layoutParams);
      
      ((Button)findViewById(R.id.UploadButton)).setText("Upload");
      
      try {
        camcorderPreview.startRecording();
      } catch (Exception e) {
        Log.e(TAG, "", e);
      }
    } else {
      ((Button)findViewById(R.id.UploadButton)).setEnabled(false);
      
      try {
        camcorderPreview.stopRecording();
      } catch (Exception e) {
        Log.e(TAG, "", e);
      }
      
      Location currentLocation = getCurrentLocation();
      if (currentLocation == null) {
        currentLocation = new Location("");
      }

      filePath = camcorderPreview.getOutputFilePath();
      if (filePath == null) {
        filePath = DUMMY_MOVIE_FILE;
      }

      final YouTubeUploader ytUploader = new YouTubeUploader(authSubToken, YOUTUBE_API_KEY);
      ytUploader.setTitle("Android Test");
      ytUploader.setCategory("People");
      ytUploader.setDescription("Just a test.");
      ytUploader.setKeywords("test");
      ytUploader.setFilePath(filePath);
      ytUploader.setLocation(currentLocation);
      ytUploader.setYtdSubmitUrl(String.format(YTD_MOBILE_SUBMISSION_URL, YTD_SERVER));
      ytUploader.setCallbackHandler(callbackHandler);

      findViewById(R.id.UploadProgressBar).setVisibility(View.VISIBLE);
      new Thread(new Runnable() {
        public void run() {
          ytUploader.upload();
        }
      }).start();
      
      RelativeLayout relativeLayout = (RelativeLayout)findViewById(R.id.RelativeLayout01);
      relativeLayout.removeView(camcorderPreview);
      camcorderPreview = null;
    }
  }
  
  private void removeFile(String filePath) {
    if (!filePath.equals(DUMMY_MOVIE_FILE)) {
      File file = new File(filePath);
      file.delete();
    }
  }
  
  private Location getCurrentLocation() {
    LocationManager locationManager = (LocationManager)getSystemService(Context.LOCATION_SERVICE);
    
    Criteria locationCriteria = new Criteria();
    locationCriteria.setAccuracy(Criteria.ACCURACY_FINE);
    locationCriteria.setAltitudeRequired(false);
    locationCriteria.setBearingRequired(false);
    locationCriteria.setCostAllowed(false);
    locationCriteria.setPowerRequirement(Criteria.POWER_HIGH);
    
    String locationProvider = locationManager.getBestProvider(locationCriteria, true);
    if (locationProvider != null) {
      Location currentLocation = locationManager.getLastKnownLocation(locationProvider);
      return currentLocation;
    }
    
    return null;
  }

  public void onLoginClick(View v) {
    try {
      String url = String.format(YTD_MOBILE_LOGIN_URL, YTD_SERVER);
      Intent i = new Intent(Intent.ACTION_VIEW);
      i.setData(Uri.parse(url));
      startActivity(i);
    } catch (Exception e) {
      Log.e(TAG, "", e);
    }
  }

  public void onLogoutClick(View v) {
    try {
      Editor editor = getSharedPreferences("default", 0).edit();
      editor.remove("username");
      editor.remove("authSubToken");
      editor.remove("filePath");
      editor.remove("fileUploadUrl");
      editor.commit();

      displayMessage("Cleared saved username and AuthSub token.");
      ((Button)findViewById(R.id.LogoutButton)).setEnabled(false);
      ((Button)findViewById(R.id.UploadButton)).setEnabled(false);
      ((Button)findViewById(R.id.ResumeButton)).setEnabled(false);
    } catch (Exception e) {
      Log.e(TAG, "", e);
    }
  }

  public void displayMessage(String message) {
    ((TextView)findViewById(R.id.MessageTextView)).setText(message);
  }
}