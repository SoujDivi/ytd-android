package com.google.ytd;

import java.util.List;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.OnItemClickListener;
import com.google.ytd.db.Assignment;
import com.google.ytd.db.DbHelper;

public class MainActivity extends Activity implements OnSharedPreferenceChangeListener {
  private static final String LOG_TAG = MainActivity.class.getSimpleName();
  private static final int ALARM_FREQUENCY = 1000 * 60 * 5; // 60 sec sync

  public static final String NEW_ASSIGNMENT_UPDATE = "com.google.ytd.NEW_ASSIGNMENT_UPDATE";
  public static final String ALARM_ACTION = "com.google.ytd.ALARM_ACTION";
  public static final String SHARED_PREF_NAME = "com.google.ytd.PREF";
  private static final int RETURN_FROM_SETTING = 123;

  private ListView assignmentListView = null;
  private DbHelper dbHelper = null;
  private AssignmentArrayAdapter assignmentArrayAdapter = null;
  private List<Assignment> assignments = null;
  private BroadcastReceiver broadcastReceiver = null;
  private AlarmManager alarmManager = null;
  private PendingIntent alarmIntent = null;
  private SharedPreferences preferences = null;
  private String ytdDomain = null;
  private TextView domainHeader = null;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    this.setContentView(R.layout.main);

    this.assignmentListView = (ListView) this.findViewById(R.id.assignmentList);
    this.domainHeader = (TextView) this.findViewById(R.id.domainHeader);

    this.dbHelper = new DbHelper(this);
    this.dbHelper = this.dbHelper.open();

    this.preferences = this.getSharedPreferences(SHARED_PREF_NAME, Activity.MODE_PRIVATE);
    this.preferences.registerOnSharedPreferenceChangeListener(this);

    this.ytdDomain = this.getSharedPreference(DbHelper.YTD_DOMAIN);

    this.findViewById(R.id.specialReportButton).setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View v) {
        Intent intent = new Intent(MainActivity.this, DetailsActivity.class);
        intent.putExtra(DbHelper.YTD_DOMAIN, ytdDomain);
        startActivity(intent);
      }
    });

    this.registerBroadcastReceivers();

    if (this.ytdDomain == null) {
      // first time access, go to intro/setting
      startSettingactivity();
    } else {
      domainHeader.setText(SettingActivity.getYtdDomains(this).get(this.ytdDomain));
      initAssignmentList();
    }

    Log.d(LOG_TAG, "onCreate()!");
  }

  private void addFooter() {
    View footer = View.inflate(this, R.layout.mystory, null);

    this.assignmentListView.addFooterView(footer);

    footer.findViewById(R.id.specialReportButton).setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View v) {
        Intent intent = new Intent(MainActivity.this, DetailsActivity.class);
        intent.putExtra(DbHelper.YTD_DOMAIN, ytdDomain);
        startActivity(intent);
      }
    });
  }

  private void switchToDomain(String domain) {
    if (domain != null && !Util.isNullOrEmpty(domain)) {
      if (!domain.equals(this.ytdDomain)) {
        this.stopAlarm();
        this.ytdDomain = domain;
        this.domainHeader.setText(SettingActivity.getYtdDomains(this).get(this.ytdDomain));
        this.downloadThenDisplay();
      }
    }
  }

  private String getSharedPreference(String key) {
    return preferences.getString(key, null);
  }

  @Override
  public void onStart() {
    super.onStart();
  }

  public boolean onCreateOptionsMenu(Menu menu) {
    super.onCreateOptionsMenu(menu);
    getMenuInflater().inflate(R.layout.menu, menu);
    return true;
  }

  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
    case R.id.refresh:
      downloadThenDisplay();
      return true;

    case R.id.setting:
      startSettingactivity();
      return true;
    }
    return false;
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent intent) {
    switch (requestCode) {
    case RETURN_FROM_SETTING:
      Log.d(LOG_TAG, "pref changed");
      String domain = this.getSharedPreference(DbHelper.YTD_DOMAIN);
      switchToDomain(domain);
      break;
    }
  }

  private void startSettingactivity() {
    Intent intent = new Intent(this, SettingActivity.class);
    startActivityForResult(intent, RETURN_FROM_SETTING);
  }

  private void startAlarm() {
    this.alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
    Intent intent = new Intent();
    intent.setAction(ALARM_ACTION);
    intent.putExtra(DbHelper.YTD_DOMAIN, this.ytdDomain);
    this.alarmIntent = PendingIntent.getBroadcast(this, 0, intent,
        PendingIntent.FLAG_UPDATE_CURRENT);

    int interval = ALARM_FREQUENCY;
    long timeToRefresh = SystemClock.elapsedRealtime() + interval;
    this.alarmManager.setRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, timeToRefresh, interval,
        this.alarmIntent);
  }

  private void stopAlarm() {
    if (this.alarmIntent != null) {
      this.alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
      this.alarmManager.cancel(this.alarmIntent);
    }
  }

  private void registerBroadcastReceivers() {
    IntentFilter filter = new IntentFilter();
    filter.addAction(NEW_ASSIGNMENT_UPDATE);

    this.broadcastReceiver = new BroadcastReceiver() {
      @Override
      public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals(NEW_ASSIGNMENT_UPDATE)) {
          assignments = dbHelper.getAllAssignmentsWithHeadings(ytdDomain);
          assignmentArrayAdapter = new AssignmentArrayAdapter(MainActivity.this,
              R.layout.assignment_list_item, assignments);
          assignmentListView.setAdapter(assignmentArrayAdapter);
          MainActivity.this.assignmentArrayAdapter.notifyDataSetChanged();
        }
      }
    };
    registerReceiver(this.broadcastReceiver, filter);
  }

  private void downloadThenDisplay() {
    final ProgressDialog dialog = new ProgressDialog(this);
    dialog.setMessage("loading ...");
    dialog.setCancelable(false);
    dialog.show();

    final Handler handler = new Handler() {
      @Override
      public void handleMessage(Message msg) {
        dialog.dismiss();
        assignments = dbHelper.getAllAssignmentsWithHeadings(ytdDomain);
        displayAssignmentList();
      }
    };

    if (this.assignmentArrayAdapter != null) {
      this.assignmentArrayAdapter.clear();
    }

    new Thread() {
      @Override
      public void run() {
        boolean success = Util.initAssignmentDb(dbHelper, ytdDomain);
        Looper.prepare();
        if (!success) {
          Toast.makeText(
              MainActivity.this,
              "Request made to " + ytdDomain
                  + " failed.  Please make sure you have the correct domain.", Toast.LENGTH_LONG)
              .show();
        }

        handler.sendEmptyMessage(RESULT_OK);
        Looper.loop();
      }
    }.start();
  }

  private void initAssignmentList() {
    assignments = dbHelper.getAllAssignmentsWithHeadings(ytdDomain);

    if (assignments.isEmpty()) {
      downloadThenDisplay();
    } else {
      displayAssignmentList();
    }
  }

  @Override
  public void onDestroy() {
    Log.d(LOG_TAG, "onDestroy()");
    super.onDestroy();
    dbHelper.close();
    unregisterReceiver(this.broadcastReceiver);
  }

  private void displayAssignmentList() {
    assignmentArrayAdapter = new AssignmentArrayAdapter(this, R.layout.assignment_list_item,
        assignments);
    assignmentListView.setAdapter(assignmentArrayAdapter);
    assignmentListView.setTextFilterEnabled(true);
    assignmentListView.setOnItemClickListener(new OnItemClickListener() {
      public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        Assignment selectedAssignment = MainActivity.this.assignments.get(position);
        Intent intent = new Intent(MainActivity.this, DetailsActivity.class);
        intent.putExtra(DbHelper.YTD_DOMAIN, selectedAssignment.getYtdDomain());
        intent.putExtra(DbHelper.ASSIGNMENT_ID, selectedAssignment.getAssignmentId());
        startActivity(intent);
      }
    });

    startAlarm();
  }

  @Override
  public void onSharedPreferenceChanged(SharedPreferences arg0, String arg1) {
    // Log.d(LOG_TAG, "pref changed");
    // String domain = this.getSharedPreference(DbHelper.YTD_DOMAIN);
    // switchToDomain(domain);
  }
}