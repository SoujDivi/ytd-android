package com.google.ytd;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.TextView;
import android.widget.Toast;
import com.google.ytd.db.Assignment;
import com.google.ytd.db.DbHelper;

public class DetailsActivity extends Activity {
  private static final String LOG_TAG = DetailsActivity.class.getSimpleName();
  private static final int CAPTURE_RETURN = 1;
  private static final int GALLERY_RETURN = 2;
  private static final int SUBMIT_RETURN = 3;
  private DbHelper dbHelper = null;
  private String ytdDomain = null;
  private String assignmentId = null;
  private TextView domainHeader = null;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    this.setContentView(R.layout.details);

    dbHelper = new DbHelper(this);
    dbHelper = dbHelper.open();

    Intent intent = getIntent();
    this.ytdDomain = intent.getStringExtra(DbHelper.YTD_DOMAIN);
    this.assignmentId = intent.getStringExtra(DbHelper.ASSIGNMENT_ID);

    this.domainHeader = (TextView) this.findViewById(R.id.domainHeader);
    domainHeader.setText(SettingActivity.getYtdDomains(this).get(this.ytdDomain));

    if (intent.hasExtra("notificationId")) {
      NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
      nm.cancel(intent.getIntExtra("notificationId", 0));
    }

    if (!Util.isNullOrEmpty(ytdDomain) && !Util.isNullOrEmpty(assignmentId)) {
      Log.d(LOG_TAG, "ytdDomain=" + ytdDomain + " id=" + assignmentId);
      displayAssignmentDetails();
    } else {
      displayAnonymousAssignment();
    }

    findViewById(R.id.captureButton).setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View v) {
        Intent i = new Intent();
        i.setAction("android.media.action.VIDEO_CAPTURE");
        startActivityForResult(i, CAPTURE_RETURN);
      }
    });

    findViewById(R.id.galleryButton).setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View v) {
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_PICK);
        intent.setType("video/*");

        List<ResolveInfo> list = getPackageManager().queryIntentActivities(intent,
            PackageManager.MATCH_DEFAULT_ONLY);
        if (list.size() <= 0) {
          Log.d(LOG_TAG, "no video picker intent on this hardware");
          return;
        }

        startActivityForResult(intent, GALLERY_RETURN);
      }
    });
  }

  public void displayAssignmentDetails() {
    Assignment assignment = dbHelper.getAssignment(this.ytdDomain, this.assignmentId);
    SimpleDateFormat dateFormat = new SimpleDateFormat("MMM d, yyyy hh:mm aaa");
    Configuration userConfig = new Configuration();
    Settings.System.getConfiguration(getContentResolver(), userConfig);
    Calendar cal = Calendar.getInstance(userConfig.locale);
    TimeZone tz = cal.getTimeZone();

    TextView assignmentCreated = (TextView) findViewById(R.id.assignmentCreated);
    assignmentCreated.setText("Created:  " + dateFormat.format(assignment.getCreated()));

    TextView assignmentUpdated = (TextView) findViewById(R.id.assignmentUpdated);
    assignmentUpdated.setText("Updated: " + dateFormat.format(assignment.getUpdated()));

    TextView assignmentDescription = (TextView) findViewById(R.id.assignmentDescription);
    assignmentDescription.setText(assignment.getDescription());
  }

  public void displayAnonymousAssignment() {
    TextView assignmentDescription = (TextView) findViewById(R.id.assignmentDescription);
    assignmentDescription.setText("Upload your " + SettingActivity.getYtdDomains(this).get(this.ytdDomain)
        + " news videos");
    assignmentDescription.setTypeface(null, Typeface.BOLD);
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);

    switch (requestCode) {
    case CAPTURE_RETURN:
    case GALLERY_RETURN:
      if (resultCode == RESULT_OK) {
        Intent intent = new Intent(this, SubmitActivity.class);
        intent.setData(data.getData());
        intent.putExtra(DbHelper.YTD_DOMAIN, this.ytdDomain);
        if (!Util.isNullOrEmpty(this.assignmentId)) {
          intent.putExtra(DbHelper.ASSIGNMENT_ID, this.assignmentId);
        }
        startActivityForResult(intent, SUBMIT_RETURN);
      }
      break;
    case SUBMIT_RETURN:
      if (resultCode == RESULT_OK) {
        Toast.makeText(DetailsActivity.this, "thank you!", Toast.LENGTH_LONG).show();
      } else {
        // Toast.makeText(DetailsActivity.this, "submit failed or cancelled",
        // Toast.LENGTH_LONG).show();
      }
      break;
    }
  }

  @Override
  public void onStart() {
    super.onStart();
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    dbHelper.close();
  }
}