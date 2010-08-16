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

package com.google.ytd.db;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.util.Log;

public class DbHelper {
  private static final String LOG_TAG = DbHelper.class.getSimpleName();

  private static final String DATABASE_NAME = "ytd";
  private static final String DATABASE_TABLE = "assignment";
  private static final int DATABASE_VERSION = 1;

  public static final String KEY_ID = "_id";
  public static final String YTD_DOMAIN = "ytd_domain";
  public static final String ASSIGNMENT_ID = "assignment_id";
  public static final String DESCRIPTION = "description";
  public static final String CREATED = "created";
  public static final String UPDATED = "updated";
  public static final String STATUS = "status";

  private static final String DATABASE_CREATE = "create table " + DATABASE_TABLE + " (" + KEY_ID
      + " integer primary key autoincrement, " + YTD_DOMAIN + " text not null, " + STATUS
      + " text not null, " + ASSIGNMENT_ID + " text not null, " + DESCRIPTION + " text not null, "
      + UPDATED + " text not null," + CREATED + " text not null)";

  public static final String YT_ACCOUNT = "yt_account";

  private Context context = null;
  private MySqliteHelper sqliteHelper = null;
  private SQLiteDatabase db = null;

  public DbHelper(Context context) {
    this.context = context;
    this.sqliteHelper = new MySqliteHelper(context, DATABASE_NAME, null, DATABASE_VERSION);
  }

  public DbHelper open() throws SQLException {
    this.db = sqliteHelper.getWritableDatabase();
    return this;
  }

  public void close() {
    if (this.db.isOpen()) {
      this.db.close();
    }
  }

  public SQLiteDatabase getDb() {
    return this.db;
  }

  public void renew() {
    db.execSQL("DROP TABLE IF EXISTS " + DATABASE_TABLE);
    db.execSQL(DATABASE_CREATE);
    Log.d("LOG_TAG", "Assignment DB is renewed");
  }

  public boolean hasAssignment(String ytdDomain, String assignmentId) {
    Cursor cursor = db.query(true, DATABASE_TABLE, new String[] { KEY_ID }, ASSIGNMENT_ID + "="
        + assignmentId + " AND " + YTD_DOMAIN + "=\"" + ytdDomain + "\"", null, null, null, null,
        null);

    if ((cursor.getCount() == 0 || !cursor.moveToFirst())) {
      cursor.close();
      return false;
    } else {
      cursor.close();
      return true;
    }
  }

  public Assignment getAssignment(String id) {
    Cursor cursor = db.query(true, DATABASE_TABLE, new String[] { KEY_ID, YTD_DOMAIN,
        ASSIGNMENT_ID, STATUS, CREATED, UPDATED, DESCRIPTION }, KEY_ID + "=" + id, null, null,
        null, null, null);

    if ((cursor.getCount() == 0 || !cursor.moveToFirst())) {
      cursor.close();
      return null;
    }

    Assignment assignment = new Assignment(cursor.getString(cursor.getColumnIndex(YTD_DOMAIN)),
        cursor.getString(cursor.getColumnIndex(ASSIGNMENT_ID)));
    assignment.setStatus(cursor.getString(cursor.getColumnIndex(STATUS)));
    assignment.setCreated(new Date(cursor.getLong(cursor.getColumnIndex(CREATED))));
    assignment.setUpdated(new Date(cursor.getLong(cursor.getColumnIndex(UPDATED))));
    assignment.setDescription(cursor.getString(cursor.getColumnIndex(DESCRIPTION)));

    cursor.close();
    return assignment;
  }

  public Assignment getAssignment(String ytdDomain, String assignmentId) {
    Cursor cursor = db.query(true, DATABASE_TABLE, new String[] { STATUS, CREATED, UPDATED,
        DESCRIPTION }, ASSIGNMENT_ID + "=" + assignmentId + " AND " + YTD_DOMAIN + "=\""
        + ytdDomain + "\"", null, null, null, null, null);

    if ((cursor.getCount() == 0 || !cursor.moveToFirst())) {
      cursor.close();
      return null;
    }

    Assignment assignment = new Assignment(ytdDomain, assignmentId);
    assignment.setStatus(cursor.getString(cursor.getColumnIndex(STATUS)));
    assignment.setCreated(new Date(cursor.getLong(cursor.getColumnIndex(CREATED))));
    assignment.setUpdated(new Date(cursor.getLong(cursor.getColumnIndex(UPDATED))));
    assignment.setDescription(cursor.getString(cursor.getColumnIndex(DESCRIPTION)));

    cursor.close();

    return assignment;
  }

  public List<Assignment> getAllAssignments(String ytdDomain) {
    List<Assignment> entries = new ArrayList<Assignment>();

    Cursor cursor = db.query(true, DATABASE_TABLE, new String[] { ASSIGNMENT_ID, STATUS, CREATED,
        UPDATED, DESCRIPTION },
        YTD_DOMAIN + "=\"" + ytdDomain + "\" AND " + STATUS + "=\"ACTIVE\"", null, null, null,
        UPDATED + " DESC", null);

    if (cursor.getCount() > 0) {
      if (cursor.moveToFirst()) {
        do {
          String assignmentId = cursor.getString(cursor.getColumnIndex(ASSIGNMENT_ID));
          Assignment assignment = new Assignment(ytdDomain, assignmentId);
          assignment.setStatus(cursor.getString(cursor.getColumnIndex(STATUS)));
          assignment.setCreated(new Date(cursor.getLong(cursor.getColumnIndex(CREATED))));
          assignment.setUpdated(new Date(cursor.getLong(cursor.getColumnIndex(UPDATED))));
          assignment.setDescription(cursor.getString(cursor.getColumnIndex(DESCRIPTION)));
          entries.add(assignment);
        } while (cursor.moveToNext());
      }
    }
    cursor.close();

    return entries;
  }

  public List<Assignment> getAllAssignmentsWithHeadings(String ytdDomain) {
    List<Assignment> entries = new ArrayList<Assignment>();

    Cursor cursor = db.query(true, DATABASE_TABLE, new String[] { ASSIGNMENT_ID, STATUS, CREATED,
        UPDATED, DESCRIPTION },
        YTD_DOMAIN + "=\"" + ytdDomain + "\" AND " + STATUS + "=\"ACTIVE\"", null, null, null,
        UPDATED + " DESC", null);

    int currentDay = -1;
    int currentMonth = -1;
    int currentYear = -1;

    Calendar cal = Calendar.getInstance();
    
    if (cursor.getCount() > 0) {
      if (cursor.moveToFirst()) {
        do {
            String assignmentId = cursor.getString(cursor.getColumnIndex(ASSIGNMENT_ID));
            Assignment assignment = new Assignment(ytdDomain, assignmentId);
            assignment.setStatus(cursor.getString(cursor.getColumnIndex(STATUS)));
            assignment.setCreated(new Date(cursor.getLong(cursor.getColumnIndex(CREATED))));
            assignment.setUpdated(new Date(cursor.getLong(cursor.getColumnIndex(UPDATED))));
            assignment.setDescription(cursor.getString(cursor.getColumnIndex(DESCRIPTION)));
      
            
            cal.setTime(assignment.getUpdated());
            int day = cal.get(Calendar.DATE);
            int month = cal.get(Calendar.MONTH) + 1;
            int year = cal.get(Calendar.YEAR);

            if (currentDay != day) {
              // This is a new heading
              currentDay = day;
              currentMonth = month;
              currentYear = year;

              Assignment heading = new Assignment(null, null);
              heading.setHeading(true);
              heading.setUpdated(assignment.getUpdated());

              entries.add(heading);
            }            
            entries.add(assignment);                
        } while (cursor.moveToNext());
      }
    }
    cursor.close();

    return entries;
  }  
  
  public Cursor getAllAssignmentsCursor(String ytdDomain) {
    return db.query(true, DATABASE_TABLE, new String[] { KEY_ID, ASSIGNMENT_ID, STATUS, CREATED,
        DESCRIPTION }, YTD_DOMAIN + "=\"" + ytdDomain + "\" AND " + STATUS + "=\"ACTIVE\"", null,
        null, null, CREATED + " DESC", null);
  }

  public boolean updateAssignment(String ytdDomain, String assignmentId, Assignment newAssignment) {
    ContentValues values = new ContentValues();
    values.put(DESCRIPTION, newAssignment.getDescription());
    values.put(STATUS, newAssignment.getStatus());
    values.put(UPDATED, newAssignment.getUpdated().getTime());

    db.update(DATABASE_TABLE, values, YTD_DOMAIN + "=? AND " + ASSIGNMENT_ID + "=?", new String[] {
        ytdDomain, assignmentId });

    return true;
  }

  public long insertAssignment(Assignment assignment) {
    if (assignment.getDescription().equals("default mobile assignment")) {
      return -1;
    }

    ContentValues values = new ContentValues();

    values.put(ASSIGNMENT_ID, assignment.getAssignmentId());
    values.put(YTD_DOMAIN, assignment.getYtdDomain());
    values.put(DESCRIPTION, assignment.getDescription());
    values.put(CREATED, assignment.getCreated().getTime());
    values.put(UPDATED, assignment.getUpdated().getTime());
    values.put(STATUS, assignment.getStatus());

    return db.insert(DATABASE_TABLE, null, values);
  }

  private static class MySqliteHelper extends SQLiteOpenHelper {
    public MySqliteHelper(Context context, String name, CursorFactory factory, int version) {
      super(context, name, factory, version);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
      Log.d(LOG_TAG, "creating table");
      db.execSQL(DATABASE_CREATE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    }
  }
}