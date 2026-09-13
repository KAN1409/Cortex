package com.kareem.cortex;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Context;
import android.os.Bundle;

/**
 * Device-account OAuth token provider for Gmail REST.
 * The selected Google account must already exist on the device and may require user consent.
 * No token is persisted by Cortex.
 */
public final class AccountManagerGmailTokenProvider implements GmailBridgeTransport.AccessTokenProvider {
    public static final String GOOGLE_ACCOUNT_TYPE = "com.google";
    public static final String GMAIL_SCOPE = "oauth2:https://www.googleapis.com/auth/gmail.modify";

    private final Context context;
    private final String accountName;

    public AccountManagerGmailTokenProvider(Context context, String accountName) {
        if (context == null) throw new IllegalArgumentException("context required");
        if (accountName == null || accountName.trim().isEmpty()) throw new IllegalArgumentException("accountName required");
        this.context = context.getApplicationContext();
        this.accountName = accountName.trim();
    }

    @Override
    public String getAccessToken() throws Exception {
        Account account = findAccount();
        if (account == null) throw new AuthRequiredException("Google account not available to Cortex: " + accountName);
        AccountManager am = AccountManager.get(context);
        Bundle result = am.getAuthToken(account, GMAIL_SCOPE, null, false, null, null).getResult();
        if (result == null) throw new AuthRequiredException("empty AccountManager auth result");
        String token = result.getString(AccountManager.KEY_AUTHTOKEN);
        if (token == null || token.trim().isEmpty()) {
            if (result.getParcelable(AccountManager.KEY_INTENT) != null) {
                throw new AuthRequiredException("Google consent is required before Gmail bridge can run unattended");
            }
            throw new AuthRequiredException("no Gmail OAuth token returned");
        }
        return token;
    }

    @Override
    public void invalidate(String token) {
        if (token == null || token.isEmpty()) return;
        AccountManager.get(context).invalidateAuthToken(GOOGLE_ACCOUNT_TYPE, token);
    }

    private Account findAccount() {
        Account[] accounts = AccountManager.get(context).getAccountsByType(GOOGLE_ACCOUNT_TYPE);
        if (accounts == null) return null;
        for (Account account : accounts) {
            if (accountName.equalsIgnoreCase(account.name)) return account;
        }
        return null;
    }

    public static final class AuthRequiredException extends Exception {
        public AuthRequiredException(String message) { super(message); }
    }
}
