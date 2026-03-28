package io.globules.cordova.googleplayage;

import android.app.Activity;
import android.content.Context;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.agesignals.AgeSignalsException;
import com.google.android.gms.agesignals.AgeSignalsManager;
import com.google.android.gms.agesignals.AgeSignalsManagerFactory;
import com.google.android.gms.agesignals.AgeSignalsRequest;
import com.google.android.gms.agesignals.AgeSignalsResult;
import com.google.android.gms.agesignals.AgeSignalsResultCallback;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Date;

public class AgeSignalsPlugin extends CordovaPlugin {

    private static final String ACTION_CHECK_AGE_SIGNALS = "checkAgeSignals";

    @Override
    public boolean execute(String action, JSONArray args, final CallbackContext callbackContext) {
        if (ACTION_CHECK_AGE_SIGNALS.equals(action)) {
            cordova.getThreadPool().execute(() -> checkAgeSignals(callbackContext));
            return true;
        }

        sendUnknownActionError(action, callbackContext);
        return false;
    }

    private void checkAgeSignals(final CallbackContext callbackContext) {
        final Activity activity = cordova.getActivity();
        if (activity == null) {
            sendError(callbackContext, "NO_ACTIVITY", "Cordova activity is null");
            return;
        }

        final Context context = activity.getApplicationContext();
        int playServicesStatus = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context);
        if (playServicesStatus != ConnectionResult.SUCCESS) {
            sendError(callbackContext,
                    "PLAY_SERVICES_UNAVAILABLE",
                    "Google Play Services unavailable or outdated. Status: " + playServicesStatus);
            return;
        }

        AgeSignalsManager manager;
        try {
            manager = AgeSignalsManagerFactory.create(context);
        } catch (Throwable t) {
            sendError(callbackContext,
                    "MANAGER_CREATION_FAILED",
                    "Failed to create AgeSignalsManager: " + t.getMessage());
            return;
        }

        AgeSignalsRequest request = new AgeSignalsRequest.Builder().build();

        manager.checkAgeSignals(request, new AgeSignalsResultCallback() {
            @Override
            public void onSuccess(AgeSignalsResult result) {
                JSONObject response = new JSONObject();
                try {
                    putNullable(response, "ageLower", result.ageLower());
                    putNullable(response, "ageUpper", result.ageUpper());
                    putNullable(response, "installId", result.installId());

                    Date approvalDate = result.mostRecentApprovalDate();
                    if (approvalDate != null) {
                        response.put("mostRecentApprovalDate", approvalDate.getTime());
                    } else {
                        response.put("mostRecentApprovalDate", JSONObject.NULL);
                    }

                    response.put("isAgePersonalizedAdsEnabled", result.isAgePersonalizedAdsEnabled());
                    response.put("isAgePersonalizedAdsEligible", result.isAgePersonalizedAdsEligible());

                } catch (JSONException e) {
                    sendError(callbackContext,
                            "JSON_BUILD_ERROR",
                            "Failed to build JSON response: " + e.getMessage());
                    return;
                }

                sendSuccess(callbackContext, response);
            }

            @Override
            public void onFailure(AgeSignalsException e) {
                JSONObject error = new JSONObject();
                try {
                    error.put("code", e.getErrorCode());
                    error.put("type", "AgeSignalsException");
                    error.put("message", e.getMessage());
                } catch (JSONException jsonException) {
                    sendError(callbackContext,
                            "AGE_SIGNALS_EXCEPTION",
                            "AgeSignalsException (code " + e.getErrorCode() + "): " + e.getMessage());
                    return;
                }
                sendError(callbackContext, error);
            }

            @Override
            public void onFailure(Throwable t) {
                JSONObject error = new JSONObject();
                try {
                    error.put("code", "UNKNOWN_ERROR");
                    error.put("type", t.getClass().getSimpleName());
                    error.put("message", t.getMessage());
                } catch (JSONException jsonException) {
                    sendError(callbackContext,
                            "UNKNOWN_ERROR",
                            "Unexpected error: " + t.getMessage());
                    return;
                }
                sendError(callbackContext, error);
            }
        });
    }

    private void putNullable(JSONObject obj, String key, Object value) throws JSONException {
        if (value == null) {
            obj.put(key, JSONObject.NULL);
        } else {
            obj.put(key, value);
        }
    }

    private void sendSuccess(final CallbackContext callbackContext, final JSONObject response) {
        Activity activity = cordova.getActivity();
        if (activity == null) return;

        activity.runOnUiThread(() -> callbackContext.success(response));
    }

    private void sendError(final CallbackContext callbackContext, final JSONObject error) {
        Activity activity = cordova.getActivity();
        if (activity == null) return;

        activity.runOnUiThread(() -> callbackContext.error(error));
    }

    private void sendError(final CallbackContext callbackContext, final String code, final String message) {
        JSONObject error = new JSONObject();
        try {
            error.put("code", code);
            error.put("message", message);
        } catch (JSONException ignored) {
            Activity activity = cordova.getActivity();
            if (activity == null) return;

            activity.runOnUiThread(() -> callbackContext.error(code + ": " + message));
            return;
        }
        sendError(callbackContext, error);
    }

    private void sendUnknownActionError(String action, CallbackContext callbackContext) {
        sendError(callbackContext,
                "UNKNOWN_ACTION",
                "Unknown action: " + action);
    }
}
