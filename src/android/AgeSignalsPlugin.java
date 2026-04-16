package io.globules.cordova.googleplayage;

import android.app.Activity;
import android.content.Context;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;

import com.google.android.play.agesignals.AgeSignalsException;
import com.google.android.play.agesignals.AgeSignalsManager;
import com.google.android.play.agesignals.AgeSignalsManagerFactory;
import com.google.android.play.agesignals.AgeSignalsRequest;
import com.google.android.play.agesignals.AgeSignalsResult;
import com.google.android.play.agesignals.model.AgeSignalsVerificationStatus;

import com.google.android.gms.tasks.Task;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Date;

public class AgeSignalsPlugin extends CordovaPlugin {

    private static final String TAG = "AgeSignalsPlugin";
    private static final String ACTION_CHECK_AGE_SIGNALS = "checkAgeSignals";

    @Override
    public boolean execute(String action, JSONArray args, final CallbackContext callbackContext) {
        if (ACTION_CHECK_AGE_SIGNALS.equals(action)) {
            cordova.getThreadPool().execute(() -> checkAgeSignals(callbackContext));
            return true;
        }

        sendError(callbackContext, "UNKNOWN_ACTION", "Unknown action: " + action);
        return false;
    }

    private void checkAgeSignals(final CallbackContext callbackContext) {
        final Activity activity = cordova.getActivity();
        if (activity == null) {
            sendError(callbackContext, "NO_ACTIVITY", "Cordova activity is null");
            return;
        }

        final Context context = activity.getApplicationContext();

        // Soft warning only
        int playServicesStatus = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context);
        if (playServicesStatus != ConnectionResult.SUCCESS) {
            Log.w(TAG, "Google Play Services not fully available (status: " + playServicesStatus + ")");
        }

        AgeSignalsManager manager;
        try {
            manager = AgeSignalsManagerFactory.create(context);
        } catch (Throwable t) {
            sendError(callbackContext, "MANAGER_CREATION_FAILED",
                    "Failed to create AgeSignalsManager: " + t.getMessage());
            return;
        }

        // Official way to create the request
        AgeSignalsRequest request = AgeSignalsRequest.builder().build();

        Task<AgeSignalsResult> task = manager.checkAgeSignals(request);

        task.addOnSuccessListener(activity, result -> {
            if (result == null) {
                sendError(callbackContext, "NULL_RESULT", "AgeSignalsResult was null");
                return;
            }

            JSONObject response = new JSONObject();
            try {
                putNullable(response, "ageLower", result.ageLower());
                putNullable(response, "ageUpper", result.ageUpper());
                putNullable(response, "installId", result.installId());

                Date approvalDate = result.mostRecentApprovalDate();
                response.put("mostRecentApprovalDate", approvalDate != null ? approvalDate.getTime() : JSONObject.NULL);

                // Most important field
                putUserStatus(response, result.userStatus());

            } catch (JSONException e) {
                sendError(callbackContext, "JSON_BUILD_ERROR", "Failed to build JSON response: " + e.getMessage());
                return;
            }

            sendSuccess(callbackContext, response);
        });

        task.addOnFailureListener(activity, e -> {
            if (e instanceof AgeSignalsException) {
                AgeSignalsException ase = (AgeSignalsException) e;
                JSONObject errorObj = new JSONObject();
                try {
                    errorObj.put("code", ase.getErrorCode());
                    errorObj.put("type", "AgeSignalsException");
                    errorObj.put("message", ase.getMessage());
                } catch (JSONException ignored) {}
                sendError(callbackContext, errorObj);
            } else {
                sendError(callbackContext, "UNKNOWN_ERROR",
                        e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        });
    }

    private void putUserStatus(JSONObject obj, Integer statusCode) throws JSONException {
        String statusStr = null;
        if (statusCode != null) {
            switch (statusCode) {
                case AgeSignalsVerificationStatus.VERIFIED:
                    statusStr = "VERIFIED";
                    break;
                case AgeSignalsVerificationStatus.DECLARED:
                    statusStr = "DECLARED";
                    break;
                case AgeSignalsVerificationStatus.SUPERVISED:
                    statusStr = "SUPERVISED";
                    break;
                case AgeSignalsVerificationStatus.SUPERVISED_APPROVAL_PENDING:
                    statusStr = "SUPERVISED_APPROVAL_PENDING";
                    break;
                case AgeSignalsVerificationStatus.SUPERVISED_APPROVAL_DENIED:
                    statusStr = "SUPERVISED_APPROVAL_DENIED";
                    break;
                case AgeSignalsVerificationStatus.UNKNOWN:
                    statusStr = "UNKNOWN";
                    break;
                default:
                    statusStr = "UNKNOWN";
            }
        }
        putNullable(obj, "userStatus", statusStr);
    }

    private void putNullable(JSONObject obj, String key, Object value) throws JSONException {
        obj.put(key, value == null ? JSONObject.NULL : value);
    }

    private void sendSuccess(final CallbackContext callbackContext, final JSONObject response) {
        runOnUiThreadIfAvailable(() -> callbackContext.success(response));
    }

    private void sendError(final CallbackContext callbackContext, final JSONObject error) {
        runOnUiThreadIfAvailable(() -> callbackContext.error(error));
    }

    private void sendError(final CallbackContext callbackContext, final String code, final String message) {
        JSONObject error = new JSONObject();
        try {
            error.put("code", code);
            error.put("message", message);
            sendError(callbackContext, error);
        } catch (JSONException ignored) {
            runOnUiThreadIfAvailable(() -> callbackContext.error(code + ": " + message));
        }
    }

    private void runOnUiThreadIfAvailable(Runnable runnable) {
        Activity activity = cordova.getActivity();
        if (activity != null) {
            activity.runOnUiThread(runnable);
        }
    }
}