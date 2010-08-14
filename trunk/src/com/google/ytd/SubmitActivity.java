package com.google.ytd;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.provider.Settings;
import android.provider.MediaStore.Video;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import com.google.ytd.Authorizer.AuthorizationListener;
import com.google.ytd.db.DbHelper;

public class SubmitActivity extends Activity {
  private static final String LOG_TAG = SubmitActivity.class.getSimpleName();

  private static final String INITIAL_UPLOAD_URL =
      "http://uploads.gdata.youtube.com/resumable/feeds/api/users/default/uploads";
  private static final String DEFAULT_VIDEO_CATEGORY = "News";
  private static final String DEFAULT_VIDEO_TAGS = "mobile";

  private static final int DIALOG_LEGAL = 0;

  private ProgressDialog dialog = null;
  private DbHelper dbHelper = null;
  private String ytdDomain = null;
  private String assignmentId = null;
  private Uri videoUri = null;
  private String clientLoginToken = null;
  private String youTubeName = null;
  private Date dateTaken = null;
  private Authorizer authorizer = null;
  private Location videoLocation = null;
  private String tags = null;
  private LocationListener locationListener = null;
  private LocationManager locationManager = null;
  private SharedPreferences preferences = null;
  private TextView domainHeader = null;

  private double currentFileSize = 0;
  private double totalBytesUploaded = 0;

  static class YouTubeAccountException extends Exception {
    public YouTubeAccountException(String msg) {
      super(msg);
    }
  }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    this.setContentView(R.layout.submit);

    this.authorizer = new GlsAuthorizer.GlsAuthorizerFactory().getAuthorizer(this,
        GlsAuthorizer.YOUTUBE_AUTH_TOKEN_TYPE);

    dbHelper = new DbHelper(this);
    dbHelper = dbHelper.open();

    Intent intent = this.getIntent();
    this.videoUri = intent.getData();
    this.ytdDomain = intent.getExtras().getString(DbHelper.YTD_DOMAIN);
    this.assignmentId = intent.getExtras().getString(DbHelper.ASSIGNMENT_ID);

    this.domainHeader   = (TextView) this.findViewById(R.id.domainHeader);
    domainHeader.setText(SettingActivity.getYtdDomains(this).get(this.ytdDomain));

    this.preferences = this.getSharedPreferences(MainActivity.SHARED_PREF_NAME,
        Activity.MODE_PRIVATE);
    this.youTubeName = preferences.getString(DbHelper.YT_ACCOUNT, null);

    final Button submitButton = (Button) findViewById(R.id.submitButton);
    submitButton.setEnabled(false);

    submitButton.setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View v) {
        showDialog(DIALOG_LEGAL);
      }
    });

    Button cancelButton = (Button) findViewById(R.id.cancelButton);
    cancelButton.setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View v) {
        setResult(RESULT_CANCELED);
        finish();
      }
    });

    EditText titleEdit = (EditText) findViewById(R.id.submitTitle);
    titleEdit.addTextChangedListener(new TextWatcher() {
      @Override
      public void afterTextChanged(Editable arg0) {
        enableSubmitIfReady();
      }

      @Override
      public void beforeTextChanged(CharSequence s, int start, int count, int after) {
      }

      @Override
      public void onTextChanged(CharSequence s, int start, int before, int count) {
      }
    });
    EditText descriptionEdit = (EditText) findViewById(R.id.submitDescription);
    descriptionEdit.addTextChangedListener(new TextWatcher() {
      @Override
      public void afterTextChanged(Editable arg0) {
        enableSubmitIfReady();
      }

      @Override
      public void beforeTextChanged(CharSequence s, int start, int count, int after) {
      }

      @Override
      public void onTextChanged(CharSequence s, int start, int before, int count) {
      }
    });

    Cursor cursor = this.managedQuery(this.videoUri, null, null, null, null);

    if (cursor.getCount() == 0) {
      Log.d(LOG_TAG, "not a valid video uri");
      Toast.makeText(SubmitActivity.this, "not a valid video uri", Toast.LENGTH_LONG).show();
    } else {
      getVideoLocation();

      if (cursor.moveToFirst()) {

        long id = cursor.getLong(cursor.getColumnIndex(Video.VideoColumns._ID));
        this.dateTaken = new Date(cursor.getLong(cursor
            .getColumnIndex(Video.VideoColumns.DATE_TAKEN)));

        SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, MMM d, yyyy hh:mm aaa");
        Configuration userConfig = new Configuration();
        Settings.System.getConfiguration(getContentResolver(), userConfig);
        Calendar cal = Calendar.getInstance(userConfig.locale);
        TimeZone tz = cal.getTimeZone();

        dateFormat.setTimeZone(tz);

        TextView dateTakenView = (TextView) findViewById(R.id.dateCaptured);
        dateTakenView.setText("Date captured: " + dateFormat.format(dateTaken));

        ImageView thumbnail = (ImageView) findViewById(R.id.thumbnail);
        ContentResolver crThumb = getContentResolver();
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 1;
        Bitmap curThumb = MediaStore.Video.Thumbnails.getThumbnail(crThumb, id,
            MediaStore.Video.Thumbnails.MICRO_KIND, options);
        thumbnail.setImageBitmap(curThumb);
      }
    }
  }

  @Override
  protected Dialog onCreateDialog(int id) {
    final Dialog dialog = new Dialog(SubmitActivity.this);
    dialog.setTitle("Terms of Service");
    switch(id) {
    case DIALOG_LEGAL:
      dialog.setContentView(R.layout.legal);

      TextView legalText = (TextView) dialog.findViewById(R.id.legal);

      legalText.setText(Util.readFile(this, R.raw.legal).toString());

      dialog.findViewById(R.id.agree).setOnClickListener(new OnClickListener() {
        @Override
        public void onClick(View v) {
          dialog.cancel();
          getAuthTokenWithPermission(youTubeName);
        }
      });
      dialog.findViewById(R.id.notagree).setOnClickListener(new OnClickListener() {
        @Override
        public void onClick(View v) {
          dialog.cancel();
        }
      });

      break;
    }

    return dialog;
  }

  @Override
  public void onRestart() {
    super.onRestart();
    hideKeyboard(this.getCurrentFocus());
  }

  private void requestDummyFocus() {
    this.findViewById(R.id.dummy).requestFocus();
  }

  private void hideKeyboard(View currentFocusView) {
    if (currentFocusView instanceof EditText) {
      InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
      imm.hideSoftInputFromWindow(currentFocusView.getWindowToken(), 0);
      requestDummyFocus();
    }
  }

  public String getTitleText() {
    EditText titleEdit = (EditText) findViewById(R.id.submitTitle);
    return sanitize(titleEdit.getText().toString());
  }

  public String getDescriptionText() {
    EditText descriptionEdit = (EditText) findViewById(R.id.submitDescription);
    return sanitize(descriptionEdit.getText().toString());
  }

  private String sanitize(String text) {
    return text.replaceAll("&", "&amp;");
  }

  public String getTagsText() {
    EditText tagsEdit = (EditText) findViewById(R.id.submitTags);
    return sanitize(tagsEdit.getText().toString());
  }

  public void enableSubmitIfReady() {
    Button submit = (Button) findViewById(R.id.submitButton);
    boolean isReady = getTitleText().length() > 0 && getDescriptionText().length() > 0;

    if (isReady) {
      submit.setEnabled(true);
    } else {
      submit.setEnabled(false);
    }
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    dbHelper.close();
    if (this.locationListener != null) {
      this.locationManager.removeUpdates(locationListener);
    }
  }

  public void upload(Uri videoUri) {
    this.dialog = new ProgressDialog(this);
    dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
    dialog.setMessage("uploading ...");
    dialog.setCancelable(false);
    dialog.show();

    Handler handler = new Handler() {
      @Override
      public void handleMessage(Message msg) {
        dialog.dismiss();
        String videoId = msg.getData().getString("videoId");

        if (!Util.isNullOrEmpty(videoId)) {
          currentFileSize = 0;
          totalBytesUploaded = 0;
          Intent result = new Intent();
          result.putExtra("videoId", videoId);
          setResult(RESULT_OK, result);
          finish();
        } else {
          String error = msg.getData().getString("error");
          if (!Util.isNullOrEmpty(error)) {
            Toast.makeText(SubmitActivity.this, error, Toast.LENGTH_LONG).show();
          }
        }
      }
    };

    asyncUpload(videoUri, handler);
  }

  public void asyncUpload(final Uri uri, final Handler handler) {
    new Thread(new Runnable() {
      @Override
      public void run() {
        Message msg = new Message();
        Bundle bundle = new Bundle();
        msg.setData(bundle);

        String videoId = null;
        try {
          videoId = startUpload(uri);
        } catch (IOException e) {
          e.printStackTrace();
          bundle.putString("error", e.getMessage());
          handler.sendMessage(msg);
          return;
        } catch (YouTubeAccountException e) {
          e.printStackTrace();
          bundle.putString("error", e.getMessage());
          handler.sendMessage(msg);
          return;
        }

        bundle.putString("videoId", videoId);
        handler.sendMessage(msg);
      }
    }).start();
  }

  private File getFileFromUri(Uri uri) throws IOException {
    Cursor cursor = managedQuery(uri, null, null, null, null);
    if (cursor.getCount() == 0) {
      throw new IOException(String.format("cannot find data from %s", uri.toString()));
    } else {
      cursor.moveToFirst();
    }

    String filePath = cursor.getString(cursor.getColumnIndex(Video.VideoColumns.DATA));

    File file = new File(filePath);
    cursor.close();
    return file;
  }

  private String startUpload(Uri uri) throws IOException, YouTubeAccountException {
    File file = getFileFromUri(uri);

    if (this.clientLoginToken == null) {
      // The stored gmail account is not linked to YouTube
      throw new YouTubeAccountException(this.youTubeName + " is not linked to a YouTube account.");
    }

    String uploadUrl = uploadMetaData(file.getAbsolutePath(), true);

    Log.d(LOG_TAG, "uploadUrl=" + uploadUrl);

    this.currentFileSize = file.length();

    int uploadChunk = 1024 * 1024 * 3; // 3MB

    int start = 0;
    int end = -1;

    String videoId = null;
    double fileSize = this.currentFileSize;
    while (fileSize > 0) {
      if (fileSize - uploadChunk > 0) {
        end = start + uploadChunk - 1;
      } else {
        end = start + (int) fileSize - 1;
      }
      fileSize -= uploadChunk;
      // Log.d(LOG_TAG, String.format("start=%s end=%s total=%s", start, end,
      // file.length()));

      videoId = gdataUpload(file, uploadUrl, start, end);
      start = end + 1;
    }

    if (videoId != null) {
      return videoId;
    }

    return null;
  }

  private String uploadMetaData(String filePath, boolean retry) throws IOException {
    String uploadUrl = INITIAL_UPLOAD_URL;

    HttpURLConnection urlConnection = getGDataUrlConnection(uploadUrl);
    urlConnection.setRequestMethod("POST");
    urlConnection.setDoOutput(true);
    urlConnection.setRequestProperty("Content-Type", "application/atom+xml");
    urlConnection.setRequestProperty("Slug", filePath);
    String atomData;

    String title = getTitleText();
    String description = getDescriptionText();
    String category = DEFAULT_VIDEO_CATEGORY;
    this.tags = DEFAULT_VIDEO_TAGS;

    if (!Util.isNullOrEmpty(this.getTagsText())) {
      this.tags = this.getTagsText();
    }

    if (this.videoLocation == null) {
      String template = Util.readFile(this, R.raw.gdata).toString();
      atomData = String.format(template, title, description, category, this.tags);
    } else {
      String template = Util.readFile(this, R.raw.gdata_geo).toString();
      atomData = String.format(template, title, description, category, this.tags,
          videoLocation.getLatitude(), videoLocation.getLongitude());
    }

    OutputStreamWriter outStreamWriter = new OutputStreamWriter(urlConnection.getOutputStream());
    outStreamWriter.write(atomData);
    outStreamWriter.close();

    int responseCode = urlConnection.getResponseCode();
    if (responseCode < 200 || responseCode >= 300) {
      // The response code is 40X
      if ((responseCode + "").startsWith("4") && retry) {
        Log.d(LOG_TAG, "retrying to fetch auth token for " + youTubeName);
        this.clientLoginToken = authorizer.getFreshAuthToken(youTubeName, clientLoginToken);
        // Try again with fresh token
        return uploadMetaData(filePath, false);
      } else {
        throw new IOException(String.format("response code='%s' (code %d)" + " for %s",
            urlConnection.getResponseMessage(), responseCode, urlConnection.getURL()));
      }
    }

    return urlConnection.getHeaderField("Location");
  }

  private String gdataUpload(File file, String uploadUrl, int start, int end) throws IOException {
    int chunk = end - start + 1;
    int bufferSize = 1024;
    byte[] buffer = new byte[bufferSize];
    FileInputStream fileStream = new FileInputStream(file);

    HttpURLConnection urlConnection = getGDataUrlConnection(uploadUrl);

    urlConnection.setRequestMethod("POST");
    urlConnection.setDoOutput(true);
    urlConnection.setFixedLengthStreamingMode(chunk);
    urlConnection.setRequestProperty("Content-Type", "video/3gpp");
    urlConnection.setRequestProperty("Content-Range", String.format("bytes %d-%d/%d", start, end,
        file.length()));

    OutputStream outStreamWriter = urlConnection.getOutputStream();

    fileStream.skip(start);

    int bytesRead;
    int totalRead = 0;
    while ((bytesRead = fileStream.read(buffer, 0, bufferSize)) != -1) {
      outStreamWriter.write(buffer, 0, bytesRead);
      totalRead += bytesRead;
      this.totalBytesUploaded += bytesRead;

      double percent = (totalBytesUploaded / currentFileSize) * 99;

      // Log.d(LOG_TAG, String.format(
      // "fileSize=%f totalBytesUploaded=%f percent=%f", currentFileSize,
      // totalBytesUploaded, percent));

      dialog.setProgress((int) percent);

      if (totalRead == (end - start + 1)) {
        break;
      }
    }

    outStreamWriter.close();

    int responseCode = urlConnection.getResponseCode();

    Log.d(LOG_TAG, "responseCode=" + responseCode);
    Log.d(LOG_TAG, "responseMessage=" + urlConnection.getResponseMessage());

    try {
      if (responseCode == 201) {
        String videoId = parseVideoId(urlConnection.getInputStream());

        String latLng = null;
        if (this.videoLocation != null) {
          latLng = String.format("lat=%f lng=%f", this.videoLocation.getLatitude(),
              this.videoLocation.getLongitude());
        }

        submitToYtdDomain(this.ytdDomain, this.assignmentId, videoId,
            this.youTubeName, SubmitActivity.this.clientLoginToken, getTitleText(),
            getDescriptionText(), this.dateTaken, latLng, this.tags);
        dialog.setProgress(100);
        return videoId;
      } else {
        if ((responseCode + "").startsWith("5")) {
          String error = String.format("responseCode=%d responseMessage=%s", responseCode,
              urlConnection.getResponseMessage());
          Log.d(LOG_TAG, error);
          throw new IOException(error);
        }
      }
    } catch (ParserConfigurationException e) {
      e.printStackTrace();
    } catch (SAXException e) {
      e.printStackTrace();
    }

    return null;
  }

  private String parseVideoId(InputStream atomDataStream) throws ParserConfigurationException,
      SAXException, IOException {
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
    return null;
  }

  private HttpURLConnection getGDataUrlConnection(String urlString) throws IOException {
    URL url = new URL(urlString);
    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
    Log.d(LOG_TAG, clientLoginToken);
    connection.setRequestProperty("Authorization", String.format("GoogleLogin auth=\"%s\"",
        clientLoginToken));
    connection.setRequestProperty("GData-Version", "2");
    connection.setRequestProperty("X-GData-Client", this.getString(R.string.client_id));
    connection.setRequestProperty("X-GData-Key", String.format("key=%s", this.getString(R.string.dev_key)));
    return connection;
  }

  private void getAuthTokenWithPermission(String accountName) {
    this.authorizer.fetchAuthToken(accountName, this, new AuthorizationListener<String>() {
      @Override
      public void onCanceled() {
      }

      @Override
      public void onError(Exception e) {
      }

      @Override
      public void onSuccess(String result) {
        SubmitActivity.this.clientLoginToken = result;
        upload(SubmitActivity.this.videoUri);
      }});
  }


  private void getVideoLocation() {
    this.locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);

    Criteria criteria = new Criteria();
    criteria.setAccuracy(Criteria.ACCURACY_FINE);
    criteria.setPowerRequirement(Criteria.POWER_HIGH);
    criteria.setAltitudeRequired(false);
    criteria.setBearingRequired(false);
    criteria.setSpeedRequired(false);
    criteria.setCostAllowed(true);

    String provider = locationManager.getBestProvider(criteria, true);

    this.locationListener = new LocationListener() {
      @Override
      public void onLocationChanged(Location location) {
        if (location != null) {
          SubmitActivity.this.videoLocation = location;
          double lat = location.getLatitude();
          double lng = location.getLongitude();
          Log.d(LOG_TAG, "lat=" + lat);
          Log.d(LOG_TAG, "lng=" + lng);

          TextView locationText = (TextView) findViewById(R.id.locationLabel);
          locationText.setText("Geo Location: " + String.format("lat=%.2f lng=%.2f", lat, lng));
          locationManager.removeUpdates(this);
        } else {
          Log.d(LOG_TAG, "location is null");
        }
      }

      @Override
      public void onProviderDisabled(String provider) {
      }

      @Override
      public void onProviderEnabled(String provider) {
      }

      @Override
      public void onStatusChanged(String provider, int status, Bundle extras) {
      }

    };
    
    if (provider != null) {
      locationManager.requestLocationUpdates(provider, 2000, 10, locationListener);
    }
  }

  public void submitToYtdDomain(String ytdDomain, String assignmentId, String videoId,
      String youTubeName, String clientLoginToken, String title, String description,
      Date dateTaken, String videoLocation, String tags) {

    JSONObject payload = new JSONObject();
    try {
      payload.put("method", "NEW_MOBILE_VIDEO_SUBMISSION");
      JSONObject params = new JSONObject();

      params.put("videoId", videoId);
      params.put("youTubeName", youTubeName);
      params.put("clientLoginToken", clientLoginToken);
      params.put("title", title);
      params.put("description", description);
      params.put("videoDate", dateTaken.toString());
      params.put("tags", tags);

      if (videoLocation != null) {
        params.put("videoLocation", videoLocation);
      }

      if (assignmentId != null) {
        params.put("assignmentId", assignmentId);
      }

      payload.put("params", params);
    } catch (JSONException e) {
      e.printStackTrace();
    }

    String jsonRpcUrl = "http://" + ytdDomain + "/jsonrpc";
    String json = Util.makeJsonRpcCall(jsonRpcUrl, payload);

    if (json != null) {
      try {
        JSONObject jsonObj = new JSONObject(json);
      } catch (JSONException e) {
        e.printStackTrace();
      }
    }
  }

}