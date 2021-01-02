package com.eveningoutpost.dexdrip.wearintegration;

import android.app.IntentService;
import android.content.Intent;
import androidx.annotation.NonNull;
import androidx.legacy.content.WakefulBroadcastReceiver;

import com.eveningoutpost.dexdrip.models.APStatus;
import com.eveningoutpost.dexdrip.models.JoH;
import com.eveningoutpost.dexdrip.models.UserError;
import com.eveningoutpost.dexdrip.NewDataObserver;
import com.eveningoutpost.dexdrip.utilitymodels.Constants;
import com.eveningoutpost.dexdrip.utilitymodels.PersistentStore;

/**
 * Created by adrian on 14/02/16.
 */
public class ExternalStatusService extends IntentService {
    //constants
    private static final String EXTERNAL_STATUS_STORE = "external-status-store";
    private static final String EXTERNAL_STATUS_STORE_TIME = "external-status-store-time";
    private static final String EXTRA_STATUSLINE = "com.eveningoutpost.dexdrip.Extras.Statusline";
    public static final String ACTION_NEW_EXTERNAL_STATUSLINE = "com.eveningoutpost.dexdrip.ExternalStatusline";
    //public static final String RECEIVER_PERMISSION = "com.eveningoutpost.dexdrip.permissions.RECEIVE_EXTERNAL_STATUSLINE";
    private static final int MAX_LEN = 40;
    private final static String TAG = ExternalStatusService.class.getSimpleName();

    public ExternalStatusService() {
        super("ExternalStatusService");
        setIntentRedelivery(true);
    }

    private static boolean isCurrent(long timestamp) {
        return JoH.msSince(timestamp) < Constants.HOUR_IN_MS * 5;
    }

    private static boolean isCurrent() {
        return isCurrent(getLastStatusLineTime());
    }

    @NonNull
    public static String getLastStatusLine() {
        if (isCurrent()) {
            return PersistentStore.getString(EXTERNAL_STATUS_STORE);
        } else {
            return ""; // ignore if more than 8 hours old
        }
    }

    public static long getLastStatusLineTime() {
        return PersistentStore.getLong(EXTERNAL_STATUS_STORE_TIME);
    }

    @Override
    protected void onHandleIntent(Intent intent) {

        if (intent == null)
            return;

        try {
            final String action = intent.getAction();
            if (action == null) return;

            if (ACTION_NEW_EXTERNAL_STATUSLINE.equals(action)) {
                final String statusLine = intent.getStringExtra(EXTRA_STATUSLINE);
                update(JoH.tsl(), statusLine, true);
            }
        } finally {
            WakefulBroadcastReceiver.completeWakefulIntent(intent);
        }
    }


    public static void update(long timestamp, String statusline, boolean receivedLocally) {
        if (statusline != null) {

            if (statusline.length() > MAX_LEN) {
                statusline = statusline.substring(0, MAX_LEN);
            }

            // store the data
            if (isCurrent(timestamp)) {
                PersistentStore.setString(EXTERNAL_STATUS_STORE, statusline);
                PersistentStore.setLong(EXTERNAL_STATUS_STORE_TIME, timestamp);
            }

            if (statusline.length() > 0) {
                final Integer percent = getTBRInt();
                if (percent != null) {
                    APStatus.createEfficientRecord(timestamp, percent);
                } else {
                    UserError.Log.wtf(TAG, "Could not parse TBR from: " + statusline);
                }
            }

            // notify observers
            NewDataObserver.newExternalStatus(receivedLocally);

        }
    }

    // adapted from PR submission by JoernL
    public static String getIOB() {
        final String statusLine = getLastStatusLine();
        if (statusLine.length() == 0) return "";
        final String check = statusLine.replaceAll("[^%]", "");
        if (check.length() > 0) {
            UserError.Log.v(TAG, statusLine);
            return statusLine.substring((statusLine.lastIndexOf('%') + 2), (statusLine.lastIndexOf('%') + 6));
        } else if (check.length() == 0) {
            UserError.Log.v(TAG, statusLine);
            return statusLine.substring(0, 4);
        } else return "???";
    }

    // adapted from PR submission by JoernL
    public static String getTBR() {
        final String statusLine = getLastStatusLine();
        if (statusLine.length() == 0) return "";
        final String check = statusLine.replaceAll("[^%]", "");
        if (check.length() > 0) {
            int index1 = 0, index2 = 4;
            UserError.Log.v(TAG, statusLine);
            if (statusLine.lastIndexOf('%') == 3) index2 = 4;
            else if (statusLine.lastIndexOf('%') == 2) index2 = 3;
            else if (statusLine.lastIndexOf('%') == 1) index2 = 2;
            return statusLine.substring(index1, index2);
        } else if (check.length() == 0)
            return "100%";
        else return "???";
    }

    public static Integer getTBRInt() {
        try {
            return Integer.parseInt(getTBR().replace("%", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }


}