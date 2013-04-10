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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.OnAccountsUpdateListener;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import com.google.ytd.db.DbHelper;

public class SettingActivity extends Activity implements OnAccountsUpdateListener {
  private static final String LOG_TAG = SettingActivity.class.getSimpleName();

  private static final Map<String, String> YTD_DOMAINS = new HashMap<String, String>();

  private SharedPreferences preferences = null;

  private String[] domains = null;

  private String[] domainLabels = null;

  private AccountManager accountManager = null;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    Log.d(LOG_TAG, "setting onCreate");
    super.onCreate(savedInstanceState);
    this.setContentView(R.layout.setting);
    this.preferences = this.getSharedPreferences(MainActivity.SHARED_PREF_NAME,
        Activity.MODE_PRIVATE);

    TextView welcomeMsg = (TextView) this.findViewById(R.id.introMsg);
    welcomeMsg.setText(this.getString(R.string.intro_msg));

    this.accountManager = AccountManager.get(this);
    this.accountManager.addOnAccountsUpdatedListener(this, null, false);

    initDomainSourceSpinner();
    initYtAccountSpinner();

    Button doneButton = (Button) this.findViewById(R.id.settingDoneButton);

    doneButton.setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View v) {
        setResult(RESULT_OK, null);
        finish();
      }
    });
  }

  @Override
  public void onAccountsUpdated(Account[] accounts) {
    initYtAccountSpinner();
  }

  private void initDomainSourceSpinner() {
    Spinner domainSource = (Spinner) this.findViewById(R.id.domainSource);

    Map<String, String> ytdDomains = getYtdDomains(this);
    
    this.domains = new String[ytdDomains.size()];
    this.domainLabels = new String[ytdDomains.size()];
    int index = 0;
    for (String domain : ytdDomains.keySet()) {
      this.domains[index] = domain;
      this.domainLabels[index] = ytdDomains.get(domain);
      index++;
    }
    String currentDomain = this.getSharedPreference(DbHelper.YTD_DOMAIN);
    if (currentDomain != null) {
      // Already been previously set, move this domain to index 0 to indicate
      // previously selected
      for (int i = 0; i < domains.length; i++) {
        if (domains[i].equals(currentDomain)) {
          String temp = new String(domainLabels[0]);
          domainLabels[0] = domainLabels[i];
          domainLabels[i] = temp;

          temp = new String(domains[0]);
          domains[0] = domains[i];
          domains[i] = temp;
          temp = null;
          break;
        }
      }
    }

    ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
        R.layout.domain_source_spinner_item, domainLabels);

    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    domainSource.setAdapter(adapter);
    domainSource.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
      @Override
      public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        String domain = domains[position];
        if (!domain.equals(getSharedPreference(DbHelper.YTD_DOMAIN))) {
          setSharedPreference(DbHelper.YTD_DOMAIN, domain);
          Log.d(LOG_TAG, "domain spinner item selected: " + domain);
        }
      }

      @Override
      public void onNothingSelected(AdapterView<?> parent) {
        // setSharedPreference(DbHelper.YTD_DOMAIN, domains[0]);
        Log.d(LOG_TAG, "nothing ...");
      }
    });
  }

  private void initYtAccountSpinner() {
    Spinner ytAccount = (Spinner) this.findViewById(R.id.ytAccount);

    Account[] accts = accountManager.getAccountsByType("com.google");

    if (accts.length == 0) {
      // This phone has no stored accounts
      Toast.makeText(this, "You do not have any Google accounts on this phone.", Toast.LENGTH_LONG)
          .show();
    }

    final List<String> emails = new ArrayList<String>();

    for (Account acct : accts) {
      emails.add(acct.name);
    }

    // first gmail account
    if (emails.isEmpty()) {
      // This phone has no stored gmail account
      Toast.makeText(this, "You do not have a linked Gmail to YouTube account.", Toast.LENGTH_LONG)
          .show();
    }

    String currentAccount = this.getSharedPreference(DbHelper.YT_ACCOUNT);
    if (currentAccount != null) {
      // Already been previously set, move this domain to index 0 to indicate
      // previously selected
      for (int i = 0; i < emails.size(); i++) {
        if (emails.get(i).equals(currentAccount)) {
          String temp = new String(emails.get(0));
          emails.set(0, emails.get(i));
          emails.set(i, temp);
          temp = null;
          break;
        }
      }
    }

    ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
        R.layout.domain_source_spinner_item, emails);

    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    ytAccount.setAdapter(adapter);
    ytAccount.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
      @Override
      public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        Log.d(LOG_TAG, "account spinner item selected: " + emails.get(position));
        setSharedPreference(DbHelper.YT_ACCOUNT, emails.get(position));
      }

      @Override
      public void onNothingSelected(AdapterView<?> parent) {
      }
    });
  }

  private String getSharedPreference(String key) {
    return this.preferences.getString(key, null);
  }

  private void setSharedPreference(String key, String value) {
    SharedPreferences.Editor editor = this.preferences.edit();
    editor.putString(key, value);
    editor.commit();
  }

  public static Map<String, String> getYtdDomains(Context ctx) {
    if (YTD_DOMAINS.isEmpty()) {
        initYtdDomains(ctx);
    }
    return YTD_DOMAINS;
  }

  private static void initYtdDomains(Context ctx) {
    synchronized(YTD_DOMAINS) {
      if (YTD_DOMAINS.isEmpty()) {
        Resources res = ctx.getResources();
        YTD_DOMAINS.put(res.getString(R.string.default_ytd_domain),
        res.getString(R.string.default_ytd_domain_name));
      }
    }
  }
}