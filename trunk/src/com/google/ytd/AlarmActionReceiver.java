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