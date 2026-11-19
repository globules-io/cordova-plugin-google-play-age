var exec = require('cordova/exec');

/**
 * Request Play age signals.
 *
 * The native side calls requestAgeSignalsAccess, then checkAgeSignals when
 * the status is SHARED. A declined or unverified user is a successful result
 * with empty bounds, not an error.
 *
 * Result:
 * {
 *   ageSignalsStatus: "SHARED" | "NOT_SHARED" | "VERIFICATION_REQUIRED" | "UNSPECIFIED",
 *   ageLower: number | null,
 *   ageUpper: number | null,
 *   ageRangeSource: "TIER_A" | "TIER_B" | "TIER_C" | "TIER_D" | null,
 *   significantChangeStatus: "APPROVED" | "PENDING" | "DECLINED" | null,
 *   significantChangeApprovalDate: number | null,
 *   installId: string | null
 * }
 *
 * significantChangeApprovalDate is milliseconds since epoch.
 */
var AgeSignals = {
    checkAgeSignals: function(successCallback, errorCallback) {
        exec(successCallback, errorCallback, 'AgeSignalsPlugin', 'checkAgeSignals', []);
    }
};

module.exports = AgeSignals;
