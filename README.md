# cordova-plugin-google-play-age

Cordova plugin for the Google Play Age Signals API on Android.

https://developer.android.com/google/play/age-signals/request-age-signals

Uses `com.google.android.play:age-signals:0.0.4`. `checkAgeSignals()` requests access first, which can show Play's in-app age-sharing prompt, then reads the signals when the user has shared them.

### Add the plugin

> cordova plugin add @globules-io/cordova-plugin-google-play-age

### Remove the plugin

> cordova plugin rm @globules-io/cordova-plugin-google-play-age

### Usage

```javascript
AgeSignalsPlugin.checkAgeSignals(
    function(result) {
        if (result.ageSignalsStatus === 'VERIFICATION_REQUIRED') {
            // Ask the user to resolve their age in the Play Store.
            return;
        }
        if (result.ageSignalsStatus !== 'SHARED') {
            // NOT_SHARED or UNSPECIFIED: Play did not return a band.
            return;
        }

        // ageLower is inclusive. ageUpper is inclusive, or null for the open top band (for example 18+).
        console.log(result.ageLower, result.ageUpper, result.ageRangeSource);
    },
    function(error) {
        // error.code is a Play error number, or a string for plugin errors.
        console.error(error.code, error.message);
    }
);
```

## Response

`checkAgeSignals` resolves on every completed Play response, including when the user does not share. It rejects only when the API call fails.

| Field | Values |
| --- | --- |
| `ageSignalsStatus` | `SHARED`, `NOT_SHARED`, `VERIFICATION_REQUIRED`, `UNSPECIFIED` |
| `ageLower` | Inclusive lower bound, `0` to `18`, or `null` |
| `ageUpper` | Inclusive upper bound, `2` to `18`, or `null` for the open top band |
| `ageRangeSource` | `TIER_A`, `TIER_B`, `TIER_C`, `TIER_D`, or `null` |
| `significantChangeStatus` | `APPROVED`, `PENDING`, `DECLINED`, or `null` |
| `significantChangeApprovalDate` | Milliseconds since epoch, or `null` |
| `installId` | Play install id for a supervised install, or `null` |

`ageLower`, `ageUpper`, and `ageRangeSource` are `null` unless `ageSignalsStatus` is `SHARED`.

### ageSignalsStatus

#### SHARED

The user or parent shared an age range, or the user is in a region where Play shares it. Read `ageLower` and `ageUpper`.

#### NOT_SHARED

The user or parent did not share. Play returns no band.

#### VERIFICATION_REQUIRED

The user is in a region where age verification is mandatory and has not finished it. Ask them to resolve their status in the Play Store. Play returns no band.

#### UNSPECIFIED

Play did not return a status. Treat this the same as `NOT_SHARED`.

### ageRangeSource

Returned with a `SHARED` band.

#### TIER_A

The user provided their age to Google. Google may not have confirmed it.

#### TIER_B

A parent or guardian manages the user's age.

#### TIER_C

Age was assessed with a credit card, email address, selfie, government ID, or tax ID. Play returns this in regions where verification is mandatory.

#### TIER_D

Age was checked with a government ID plus a selfie, or with a digital ID. Play returns this in regions where verification is mandatory.

### significantChangeStatus

Set only where significant-change approval applies, and only for a supervised account that has a recorded change.

#### APPROVED

The latest significant change, and every earlier one, is approved. `significantChangeApprovalDate` is the effective date of the latest approved change.

#### PENDING

A parent has not approved one or more pending significant changes.

#### DECLINED

A parent denied one or more significant changes.

## Error object

Play failures use a numeric `code`. Plugin failures use a string `code` such as `NO_ACTIVITY` or `MANAGER_CREATION_FAILED`.

```javascript
{
    "code": -9,
    "type": "AgeSignalsException",
    "message": "..."
}
```

| Code | Name | Meaning |
| --- | --- | --- |
| -1 | `API_NOT_AVAILABLE` | Play Age Signals is unavailable. The installed Play Store may be too old. |
| -2 | `PLAY_STORE_NOT_FOUND` | No Play Store app is on the device. |
| -3 | `NETWORK_ERROR` | No network. |
| -4 | `PLAY_SERVICES_NOT_FOUND` | Play services is missing or too old. |
| -5 | `CANNOT_BIND_TO_SERVICE` | Binding to the Play Store service failed. |
| -6 | `PLAY_STORE_VERSION_OUTDATED` | The Play Store app needs an update. |
| -7 | `PLAY_SERVICES_VERSION_OUTDATED` | Play services needs an update. |
| -8 | `CLIENT_TRANSIENT_ERROR` | Transient client error. Retry with a limit. |
| -9 | `APP_NOT_OWNED` | The app was not installed by Google Play. |
| -10 | `SDK_VERSION_OUTDATED` | This Play Age Signals SDK is no longer accepted. |
| -100 | `INTERNAL_ERROR` | Unknown Play error. Retry with a limit. |

## Library

`0.0.4` removes `userStatus` and `mostRecentApprovalDate`. Sharing status is `ageSignalsStatus`. The band source is `ageRangeSource`. Significant-change approval is `significantChangeStatus` and `significantChangeApprovalDate`.
