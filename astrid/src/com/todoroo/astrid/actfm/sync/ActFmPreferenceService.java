/**
 * Copyright (c) 2012 Todoroo Inc
 *
 * See the file "LICENSE" for the full license governing this code.
 */
package com.todoroo.astrid.actfm.sync;

import android.text.TextUtils;

import com.todoroo.andlib.utility.Preferences;
import com.todoroo.astrid.dao.RemoteModelDao;
import com.todoroo.astrid.data.RemoteModel;
import com.todoroo.astrid.sync.SyncProviderUtilities;
import com.todoroo.astrid.utility.AstridPreferences;

import org.astrid.R;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Methods for working with GTasks preferences
 *
 * @author timsu
 */
public class ActFmPreferenceService extends SyncProviderUtilities {

    /**
     * add-on identifier
     */
    public static final String IDENTIFIER = "actfm"; //$NON-NLS-1$

    @Override
    public String getIdentifier() {
        return IDENTIFIER;
    }

    @Override
    public int getSyncIntervalKey() {
        return R.string.actfm_APr_interval_key;
    }

    @Override
    public void clearLastSyncDate() {
        super.clearLastSyncDate();
        Preferences.setInt(ActFmPreferenceService.PREF_SERVER_TIME, 0);
    }

    @Override
    public boolean shouldShowToast() {
        if (Preferences.getBoolean(AstridPreferences.P_FIRST_TASK, true)) {
            return false;
        }
        return super.shouldShowToast();
    }

    // --- user management

    @Override
    public void setToken(String setting) {
        super.setToken(setting);
        if (TextUtils.isEmpty(setting)) {
            RemoteModelDao.setOutstandingEntryFlags(RemoteModelDao.OUTSTANDING_FLAG_UNINITIALIZED);
        } else {
            RemoteModelDao.setOutstandingEntryFlags(RemoteModelDao.OUTSTANDING_ENTRY_FLAG_ENQUEUE_MESSAGES | RemoteModelDao.OUTSTANDING_ENTRY_FLAG_RECORD_OUTSTANDING);
        }
    }

    /**
     * @return true if the user is now or has ever been logged in
     */
    public boolean wasLoggedIn() {
        return RemoteModel.isValidUuid(userId());
    }

    /**
     * @return get user id
     */
    public static String userId() {
        try {
            String value = Preferences.getStringValue(PREF_USER_ID);
            if (value == null) {
                return Long.toString(Preferences.getLong(PREF_USER_ID, -2L));
            }
            return value;
        } catch (Exception e) {
            return Long.toString(Preferences.getLong(PREF_USER_ID, -2L));
        }
    }

    /**
     * Act.fm current user id
     */
    public static final String PREF_USER_ID = IDENTIFIER + "_user"; //$NON-NLS-1$

    /**
     * Act.fm current user name
     */
    public static final String PREF_NAME = IDENTIFIER + "_name"; //$NON-NLS-1$

    /**
     * Act.fm current user first name
     */
    public static final String PREF_FIRST_NAME = IDENTIFIER + "_first_name"; //$NON-NLS-1$

    /**
     * Act.fm current user last name
     */
    public static final String PREF_LAST_NAME = IDENTIFIER + "_last_name"; //$NON-NLS-1$

    /**
     * Act.fm current user premium status
     */
    public static final String PREF_PREMIUM = IDENTIFIER + "_premium"; //$NON-NLS-1$

    /**
     * Local knowledge of current premium status
     */
    public static final String PREF_LOCAL_PREMIUM = IDENTIFIER + "_local_premium"; //$NON-NLS-1$

    /**
     * Act.fm current user picture
     */
    public static final String PREF_PICTURE = IDENTIFIER + "_picture"; //$NON-NLS-1$

    /**
     * Act.fm current user email
     */
    public static final String PREF_EMAIL = IDENTIFIER + "_email"; //$NON-NLS-1$

    /**
     * Act.fm last sync server time
     */
    public static final String PREF_SERVER_TIME = IDENTIFIER + "_time"; //$NON-NLS-1$

    private static JSONObject user = null;

    public synchronized static JSONObject thisUser() {
        if (user == null) {
            user = new JSONObject();
            populateUser();
        }
        return user;
    }

    public synchronized static void reloadThisUser() {
        if (user == null) {
            return;
        }
        populateUser();
    }


    private static void populateUser() {
        try {
            user.put("name", Preferences.getStringValue(PREF_NAME));
            user.put("first_name", Preferences.getStringValue(PREF_FIRST_NAME));
            user.put("last_name", Preferences.getStringValue(PREF_LAST_NAME));
            user.put("premium", isPremiumUser());
            user.put("email", Preferences.getStringValue(PREF_EMAIL));
            user.put("picture", Preferences.getStringValue(PREF_PICTURE));
            user.put("id", ActFmPreferenceService.userId());
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    public static boolean isPremiumUser() {
        return true;
    }

    public static void premiumLogout() {
        Preferences.setBoolean(PREF_LOCAL_PREMIUM, false);
        Preferences.setBoolean(PREF_PREMIUM, false);
    }

    @Override
    public String getLoggedInUserName() {
        String name = Preferences.getStringValue(PREF_NAME);
        if (TextUtils.isEmpty(name)) {
            String firstName = Preferences.getStringValue(PREF_FIRST_NAME);
            if (!TextUtils.isEmpty(firstName)) {
                name = firstName;
            }

            String lastName = Preferences.getStringValue(PREF_FIRST_NAME);
            if (!TextUtils.isEmpty(lastName)) {
                if (!TextUtils.isEmpty(name)) {
                    name += " "; //$NON-NLS-1$
                }
                name += lastName;
            }

            if (name == null) {
                name = ""; //$NON-NLS-1$
            }
        }
        return name;
    }
}
