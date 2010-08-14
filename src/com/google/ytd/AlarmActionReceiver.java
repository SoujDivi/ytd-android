package com.google.ytd;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import com.google.ytd.db.DbHelper;

public class AlarmActionReceiver extends BroadcastReceiver {
  private static final String LOG_TAG = AlarmActionReceiver.class.getSimpleName();
  private static final String ALARM_ACTION = "com.google.ytd.ALARM_ACTION";
  
  @Override
  public void onReceive(Context context, Intent intent) {
    String ytdDomain = intent.getStringExtra(DbHelper.YTD_DOMAIN);
    
    Log.d(LOG_TAG, ytdDomain);
    
    if (intent.getAction().equals(ALARM_ACTION)) {
      Log.d(LOG_TAG, "ALARM_ACTION broadcast received!");
      startAssignmentSyncService(context, ytdDomain);
    }    
  }

  private void startAssignmentSyncService(Context context, String ytdDomain) {
    Intent intent = new Intent(context, AssignmentSyncService.class);
    intent.putExtra(DbHelper.YTD_DOMAIN, ytdDomain);
    context.startService(intent);
  }  
}