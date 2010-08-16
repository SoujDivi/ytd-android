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

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import android.content.Context;
import android.content.res.Configuration;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.google.ytd.db.Assignment;

public class AssignmentArrayAdapter extends ArrayAdapter<Assignment> {
  private static final String LOG_TAG = MainActivity.class.getSimpleName();
  private static final int TYPE_HEADING = 0;
  private static final int TYPE_ASSIGNMENT = 1;

  private int resource;
  private Context context;
  List<Assignment> objects;

  public AssignmentArrayAdapter(Context context, int resource, List<Assignment> objects) {
    super(context, resource, objects);
    this.resource = resource;
    this.context = context;
    this.objects = objects;
  }

  @Override
  public void notifyDataSetChanged() {
    Log.d(LOG_TAG, "notifyDataSetChanged");
    super.notifyDataSetChanged();
  }

  @Override
  public int getViewTypeCount() {
    return 2;
  }

  @Override
  public int getItemViewType(int position) {
    Assignment assignment = objects.get(position);

    if (assignment.isHeading()) {
      return TYPE_HEADING;
    } else {
      return TYPE_ASSIGNMENT;
    }
  }
  
  @Override
  public boolean areAllItemsEnabled() {
    return false;
  }

  @Override
  public boolean isEnabled(int position) {
    switch(getItemViewType(position)) {
    case TYPE_HEADING:
      return false;
    case TYPE_ASSIGNMENT:
      return true;
    }
    
    return true;
  }

  @Override
  public View getView(int position, View convertView, ViewGroup parent) {
    LinearLayout newView = null;
    Assignment assignment = getItem(position);

    String description = assignment.getDescription();
    Date updated = assignment.getUpdated();

    SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, MMM d, yyyy");
    SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm aaa");
    Configuration userConfig = new Configuration();
    Settings.System.getConfiguration(context.getContentResolver(), userConfig);
    Calendar cal = Calendar.getInstance(userConfig.locale);
    TimeZone tz = cal.getTimeZone();

    timeFormat.setTimeZone(tz);
    dateFormat.setTimeZone(tz);

    if (convertView == null) {
      newView = new LinearLayout(getContext());
      LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(
              Context.LAYOUT_INFLATER_SERVICE);

      if (assignment.isHeading()) {
        inflater.inflate(R.layout.assignment_list_heading, newView, true);
      } else {
        inflater.inflate(this.resource, newView, true);
      }
    } else {
      newView = (LinearLayout) convertView;
    }

    if (assignment.isHeading()) {
      TextView headingTitle = (TextView) newView.findViewById(R.id.heading_title);
      headingTitle.setText(dateFormat.format(updated));
      newView.setEnabled(false);
      newView.setFocusable(false);
    } else {
      TextView descriptionView = (TextView) newView.findViewById(R.id.description);
      TextView timeView = (TextView) newView.findViewById(R.id.time);
      descriptionView.setText(Util.truncate(description, 52));
      timeView.setText(timeFormat.format(updated));
    }

    return newView;
  }

}
