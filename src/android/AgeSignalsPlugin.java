package io.globules.cordova.googleplayage;

import android.app.Activity;
import android.content.Context;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;

import com.google.android.play.agesignals.AgeSignalsAccessRequest;
import com.google.android.play.agesignals.AgeSignalsAccessResult;
import com.google.android.play.agesignals.AgeSignalsException;
import com.google.android.play.agesignals.AgeSignalsManager;
import com.google.android.play.agesignals.AgeSignalsManagerFactory;
import com.google.android.play.agesignals.AgeSignalsRequest;
import com.google.android.play.agesignals.AgeSignalsResult;
import com.google.android.play.agesignals.model.AgeRangeSource;
import com.google.android.play.agesignals.model.AgeSignalsStatus;
import com.google.android.play.agesignals.model.SignificantChangeStatus;

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

        // Play draws the sharing prompt on this activity, so start on the UI thread.
        activity.runOnUiThread(() -> requestAgeSignalsAccess(activity, callbackContext));
    }

    private void requestAgeSignalsAccess(final Activity activity, final CallbackContext callbackContext) {
        final Context context = activity.getApplicationContext();

        int playServicesStatus = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context);
        if (playServicesStatus != ConnectionResult.SUCCESS) {
            Log.w(TAG, "Google Play Services not fully available (status: " + playServicesStatus + ")");
        }

        final AgeSignalsManager manager;
        try {
            manager = AgeSignalsManagerFactory.create(context);
        } catch (Throwable t) {
            sendError(callbackContext, "MANAGER_CREATION_FAILED",
                    "Failed to create AgeSignalsManager: " + t.getMessage());
            return;
        }

        AgeSignalsAccessRequest accessRequest = AgeSignalsAccessRequest.builder()
                .setActivity(activity)
                .build();

        Task<AgeSignalsAccessResult> accessTask = manager.requestAgeSignalsAccess(accessRequest);

        accessTask.addOnSuccessListener(activity, accessResult -> {
            if (accessResult == null) {
                sendError(callbackContext, "NULL_RESULT", "AgeSignalsAccessResult was null");
                return;
            }

            Integer status = accessResult.ageSignalsStatus();
            if (status != null && status == AgeSignalsStatus.SHARED) {
                fetchAgeSignals(activity, manager, callbackContext, status);
                return;
            }

            try {
                sendSuccess(callbackContext, buildResponse(status, null));
            } catch (JSONException e) {
                sendError(callbackContext, "JSON_BUILD_ERROR", "Failed to build JSON response: " + e.getMessage());
            }
        });

        accessTask.addOnFailureListener(activity, e -> handleFailure(callbackContext, e));
    }

    private void fetchAgeSignals(final Activity activity, final AgeSignalsManager manager,
            final CallbackContext callbackContext, final Integer accessStatus) {
        AgeSignalsRequest request = AgeSignalsRequest.builder().build();
        Task<AgeSignalsResult> task = manager.checkAgeSignals(request);

        task.addOnSuccessListener(activity, result -> {
            if (result == null) {
                sendError(callbackContext, "NULL_RESULT", "AgeSignalsResult was null");
                return;
            }

            try {
                sendSuccess(callbackContext, buildResponse(accessStatus, result));
            } catch (JSONException e) {
                sendError(callbackContext, "JSON_BUILD_ERROR", "Failed to build JSON response: " + e.getMessage());
            }
        });

        task.addOnFailureListener(activity, e -> handleFailure(callbackContext, e));
    }

    private JSONObject buildResponse(Integer accessStatus, AgeSignalsResult result) throws JSONException {
        JSONObject response = new JSONObject();
        response.put("ageSignalsStatus", ageSignalsStatusName(accessStatus));

        if (result == null) {
            putNullable(response, "ageLower", null);
            putNullable(response, "ageUpper", null);
            putNullable(response, "ageRangeSource", null);
            putNullable(response, "significantChangeStatus", null);
            response.put("significantChangeApprovalDate", JSONObject.NULL);
            putNullable(response, "installId", null);
            return response;
        }

        putNullable(response, "ageLower", result.ageLower());
        putNullable(response, "ageUpper", result.ageUpper());
        putNullable(response, "ageRangeSource", ageRangeSourceName(result.ageRangeSource()));
        putNullable(response, "significantChangeStatus",
                significantChangeStatusName(result.significantChangeStatus()));

        Date approvalDate = result.significantChangeApprovalDate();
        response.put("significantChangeApprovalDate",
                approvalDate != null ? approvalDate.getTime() : JSONObject.NULL);
        putNullable(response, "installId", result.installId());
        return response;
    }

    private String ageSignalsStatusName(Integer status) {
        if (status == null) {
            return "UNSPECIFIED";
        }
        switch (status) {
            case AgeSignalsStatus.SHARED:
                return "SHARED";
            case AgeSignalsStatus.NOT_SHARED:
                return "NOT_SHARED";
            case AgeSignalsStatus.VERIFICATION_REQUIRED:
                return "VERIFICATION_REQUIRED";
            case AgeSignalsStatus.UNSPECIFIED:
            default:
                return "UNSPECIFIED";
        }
    }

    private String ageRangeSourceName(Integer source) {
        if (source == null) {
            return null;
        }
        switch (source) {
            case AgeRangeSource.TIER_A:
                return "TIER_A";
            case AgeRangeSource.TIER_B:
                return "TIER_B";
            case AgeRangeSource.TIER_C:
                return "TIER_C";
            case AgeRangeSource.TIER_D:
                return "TIER_D";
            case AgeRangeSource.UNSPECIFIED:
            default:
                return null;
        }
    }

    private String significantChangeStatusName(Integer status) {
        if (status == null) {
            return null;
        }
        switch (status) {
            case SignificantChangeStatus.APPROVED:
                return "APPROVED";
            case SignificantChangeStatus.PENDING:
                return "PENDING";
            case SignificantChangeStatus.DECLINED:
                return "DECLINED";
            case SignificantChangeStatus.UNSPECIFIED:
            default:
                return null;
        }
    }

    private void handleFailure(final CallbackContext callbackContext, Exception e) {
        if (e instanceof AgeSignalsException) {
            AgeSignalsException ase = (AgeSignalsException) e;
            JSONObject errorObj = new JSONObject();
            try {
                errorObj.put("code", ase.getErrorCode());
                errorObj.put("type", "AgeSignalsException");
                errorObj.put("message", ase.getMessage());
            } catch (JSONException ignored) {
            }
            sendError(callbackContext, errorObj);
            return;
        }

        sendError(callbackContext, "UNKNOWN_ERROR",
                e.getClass().getSimpleName() + ": " + e.getMessage());
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
        } else {
            runnable.run();
        }
    }
}
