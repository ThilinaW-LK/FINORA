[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$ApprovedManifest,
    [Parameter(Mandatory = $true)][string]$ObservedManifest
)
$ErrorActionPreference = 'Stop'

$approved = Get-Content -LiteralPath $ApprovedManifest -Raw | ConvertFrom-Json
$observed = Get-Content -LiteralPath $ObservedManifest -Raw | ConvertFrom-Json
if ($null -eq $approved -or $null -eq $observed -or
    $approved -is [array] -or $observed -is [array]) {
    throw 'Handset preflight refused: expected one approval and one observation.'
}

# This script validates records collected by the session owner. It never contacts a device.
$fields = [ordered]@{
    approvalReference = '^[A-Za-z0-9][A-Za-z0-9._/-]{2,127}$'
    sessionId = '^[a-f0-9]{32}$'
    sessionOwner = '^[A-Za-z0-9][A-Za-z0-9._-]{1,63}$'
    serial = '^(?!emulator-)[A-Za-z0-9][A-Za-z0-9._-]{3,63}$'
    model = '^[A-Za-z0-9][A-Za-z0-9 ._+-]{1,79}$'
    buildFingerprint = '^[^ ,\s]+$'
    androidApi = '^(3[3-9]|[4-9][0-9])$'
    androidUser = '^[1-9][0-9]*$'
    androidUserSerial = '^[1-9][0-9]*$'
    isolatedProfile = '^[A-Za-z0-9][A-Za-z0-9 ._-]{1,63}$'
    applicationId = '^lk\.finora\.app\.acceptance$'
    instrumentationId = '^lk\.finora\.app\.acceptance\.test$'
    signerSha256 = '^[a-f0-9]{64}$'
    instrumentationSignerSha256 = '^[a-f0-9]{64}$'
    appApkSha256 = '^[a-f0-9]{64}$'
    testApkSha256 = '^[a-f0-9]{64}$'
    apiEndpoint = '^http://127\.0\.0\.1:8080/$'
    reverseMapping = '^tcp:8080 tcp:8080$'
    datasetSha256 = '^14e563da86de67d4f295b6a9f8577d76ec781128b9c91900e6a1b54c96069944$'
}
foreach ($field in $fields.Keys) {
    $expected = $approved.PSObject.Properties[$field].Value
    $actual = $observed.PSObject.Properties[$field].Value
    if ($expected -isnot [string] -or $actual -isnot [string] -or
        $expected -cnotmatch $fields[$field] -or $actual -cne $expected) {
        throw "Handset preflight refused: $field mismatch or invalid value."
    }
}
if ($approved.signerSha256 -cne $approved.instrumentationSignerSha256) {
    throw 'Handset preflight refused: app and test signing identities differ.'
}

$required = @('ownerConsentVerified', 'midRangeClassificationVerified', 'isolatedProfileVerified',
    'runnerTargetVerified', 'sessionOwnsReverseMapping', 'providerAndFirebaseDisabled',
    'isolatedApiVerified', 'smsPermissionDenied', 'notificationPermissionDenied')
foreach ($field in $required) {
    $value = $observed.PSObject.Properties[$field].Value
    if ($value -isnot [bool] -or !$value) {
        throw "Handset preflight refused: $field lacks verification."
    }
}
'Handset manifest gate PASS. No instrumentation was launched.'
