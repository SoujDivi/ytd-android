package com.google.ytd;

import android.app.Activity;
import android.content.Context;

public interface Authorizer {

  public void fetchAccounts(AuthorizationListener<String[]> listener);

  public void addAccount(Activity activity, AuthorizationListener<String> listener);

  public void fetchAuthToken(String account, Activity activity,
      AuthorizationListener<String> listener);

  public String getAuthToken(String accountName);

  public String getFreshAuthToken(String accountName, String authToken);

  public static interface AuthorizationListener<T> {
    public void onSuccess(T result);

    public void onCanceled();

    public void onError(Exception e);
  }

  public static interface AuthorizerFactory {
    public Authorizer getAuthorizer(Context context, String type);
  }

}