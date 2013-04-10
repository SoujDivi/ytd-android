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

import java.util.Date;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import com.google.ytd.db.Assignment;
import com.google.ytd.db.DbHelper;

public class AssignmentSyncService extends Service {
  private static final String LOG_TAG = AssignmentSyncService.class.getSimpleName();

  private DbHelper dbHelper = null;

  private String ytdDomain = null;
  private String ytdJsonRpcUrl = null;

  private boolean isRunning = false;

  @Override
  public void onCreate() {
    super.onCreate();
    dbHelper = new DbHelper(AssignmentSyncService.this);
    Log.d(LOG_TAG, "sync service onCreate()!!!");
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    Log.d(LOG_TAG, "sync service onDestroy()!!!");
  }

  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    final int startId_ = startId;
    if (!isRunning) {
      isRunning = true;
    } else {
      Log.d(LOG_TAG, "sync service is already running, stop and return!!!");
      // For whatever reason, the previous onStartCommand is still running,
      // perhaps the service got
      // killed by runtime and this service is not STICKY, so it never reach end
      // to call stopSelf?
      // So just kill it and let another alarm to start it again.
      stopSelf(startId_);
      return Service.START_NOT_STICKY;
    }

    // Log.d(LOG_TAG, "incoming startId=" + startId_);
    // Log.d(LOG_TAG, "thread id=" + Thread.currentThread().getId());
    // Log.d(LOG_TAG, "thread count=" +
    // Thread.currentThread().getThreadGroup().activeCount());
    if ((flags & Service.START_FLAG_RETRY) == Service.START_FLAG_RETRY) {
      Log.d(LOG_TAG, "assignment service retry");
    }

    ytdDomain = intent.getStringExtra(DbHelper.YTD_DOMAIN);
    ytdJsonRpcUrl = "http://" + ytdDomain + "/jsonrpc";

    final Handler handler = new Handler() {
      @Override
      public void handleMessage(Message msg) {
        stopSelf(startId_);
        Log.d(LOG_TAG, "startId=" + startId_ + " is finished.");
      }
    };

    new Thread() {
      @Override
      public void run() {
        String newAssignmentId = updateAssignmentDb();
        if (newAssignmentId != null) {
          Intent intent = new Intent();
          intent.setAction(MainActivity.NEW_ASSIGNMENT_UPDATE);
          intent.putExtra(DbHelper.YTD_DOMAIN, ytdDomain);
          intent.putExtra(DbHelper.ASSIGNMENT_ID, newAssignmentId);
          sendBroadcast(intent);
          sendNotification(ytdDomain, newAssignmentId);
        }

        handler.sendEmptyMessage(0);
      }
    }.start();

    return Service.START_NOT_STICKY;
  }

  public void sendNotification(String ytdDomain, String assignmentId) {
    int notificationId = 1;

    Intent assignmentIntent = new Intent(this, DetailsActivity.class);
    assignmentIntent.putExtra(DbHelper.YTD_DOMAIN, ytdDomain);
    assignmentIntent.putExtra(DbHelper.ASSIGNMENT_ID, assignmentId);
    assignmentIntent.putExtra("notificationId", notificationId);

    PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, assignmentIntent,
        PendingIntent.FLAG_UPDATE_CURRENT);

    NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

    Notification n = new Notification(R.drawable.icon, "Update from YouTube Direct", System
        .currentTimeMillis());
    n.setLatestEventInfo(getApplicationContext(), "Update from YouTube Direct", "For " + 
        SettingActivity.getYtdDomains(this).get(this.ytdDomain), pendingIntent);
    n.defaults |= Notification.DEFAULT_LIGHTS;
    nm.notify(notificationId, n);
  }

  public String updateAssignmentDb() {
    String newAssignmentId = null;

    String sortBy = "created";
    String sortOrder = "desc";
    String filterType = "all";
    int pageIndex = 1;
    int pageSize = 30;

    JSONObject payload = new JSONObject();
    try {
      payload.put("method", "GET_ASSIGNMENTS");
      JSONObject params = new JSONObject();

      params.put("sortBy", sortBy);
      params.put("sortOrder", sortOrder);
      params.put("filterType", filterType);
      params.put("pageIndex", pageIndex);
      params.put("pageSize", pageSize);
      payload.put("params", params);
    } catch (JSONException e) {
      e.printStackTrace();
    }

    String jsonRpcUrl = ytdJsonRpcUrl;
    String json = Util.makeJsonRpcCall(jsonRpcUrl, payload);

    if (json == null) {
      Log.d(LOG_TAG, ytdDomain + " jsonrpc request failed");
      return null;
    }

    // Log.d(LOG_TAG, json);
    if (json != null) {
      try {
        JSONObject jsonObj = new JSONObject(json);
        JSONArray jsonArray = jsonObj.getJSONArray("result");
        for (int i = 0; i < jsonArray.length(); i++) {
          JSONObject assignmentJson = jsonArray.getJSONObject(i);
          String assignmentId = assignmentJson.getString("id");
          String created = assignmentJson.getString("created");
          String updated = assignmentJson.getString("updated");
          String description = assignmentJson.getString("description");
          String status = assignmentJson.getString("status");

          if (description.equals("default mobile assignment")) {
            continue;
          }

          dbHelper = dbHelper.open();
          Assignment assignment = dbHelper.getAssignment(ytdDomain, assignmentId);
          dbHelper.close();

          if (assignment == null) {
            // new entry
            assignment = new Assignment(ytdDomain, assignmentId);
            assignment.setStatus(status);
            assignment.setCreated(new Date(created));
            assignment.setUpdated(new Date(updated));
            assignment.setDescription(description);

            dbHelper = dbHelper.open();
            long rowId = dbHelper.insertAssignment(assignment);
            dbHelper.close();

            Log.d(LOG_TAG, "db insert id=" + rowId);
            Log.d(LOG_TAG, "db insert description=" + description);

            newAssignmentId = assignment.getAssignmentId();
          } else {
            Date newUpdated = new Date(updated);
            if (newUpdated.getTime() > assignment.getUpdated().getTime()) {
              assignment.setStatus(status);
              assignment.setCreated(new Date(created));
              assignment.setUpdated(new Date(updated));
              assignment.setDescription(description);

              dbHelper = dbHelper.open();
              dbHelper.updateAssignment(ytdDomain, assignmentId, assignment);
              dbHelper.close();
            }
          }
        }
      } catch (JSONException e) {
        e.printStackTrace();
      }
    }

    return newAssignmentId;
  }
}