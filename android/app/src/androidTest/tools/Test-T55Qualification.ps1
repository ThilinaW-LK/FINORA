$ErrorActionPreference = 'Stop'
$d = Join-Path ([IO.Path]::GetTempPath()) ('t55-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory $d | Out-Null
try {
    $a = @{
        approvalReference='fixture-approval'; sessionId=('a'*32); sessionOwner='Synthetic'
        serial='SyntheticDevice'; model='Synthetic Phone'; buildFingerprint='test/build'
        androidApi='35'; androidUser='10'; androidUserSerial='42'; isolatedProfile='FINORA Test'
        applicationId='lk.finora.app.acceptance'; instrumentationId='lk.finora.app.acceptance.test'
        signerSha256=('b'*64); instrumentationSignerSha256=('b'*64)
        appApkSha256=('c'*64); testApkSha256=('d'*64); apiEndpoint='http://127.0.0.1:8080/'
        reverseMapping='tcp:8080 tcp:8080'
        datasetSha256='14e563da86de67d4f295b6a9f8577d76ec781128b9c91900e6a1b54c96069944'
    }
    $o = $a.Clone()
    foreach ($key in 'ownerConsentVerified','midRangeClassificationVerified','isolatedProfileVerified',
        'runnerTargetVerified','sessionOwnsReverseMapping','providerAndFirebaseDisabled',
        'isolatedApiVerified','smsPermissionDenied','notificationPermissionDenied') { $o[$key] = $true }
    $approved = Join-Path $d approved.json
    $observed = Join-Path $d observed.json
    $a | ConvertTo-Json | Set-Content $approved
    $o | ConvertTo-Json | Set-Content $observed
    & "$PSScriptRoot/Verify-T55Handset.ps1" $approved $observed
    foreach ($key in @($o.Keys)) {
        $changed = $o.Clone()
        $changed[$key] = if ($o[$key] -is [bool]) { $false } else { 'wrong' }
        $changed | ConvertTo-Json | Set-Content $observed
        $refused = $false
        try { & "$PSScriptRoot/Verify-T55Handset.ps1" $approved $observed | Out-Null }
        catch {
            if ($_.Exception.Message -notmatch [regex]::Escape($key)) { throw }
            $refused = $true
        }
        if (!$refused) { throw "Expected refusal for $key." }
    }
    "PASS: one valid manifest and $($o.Count) mismatched field fixtures."

    $i='t55-'+('1'*32); $j='t55-'+('2'*32); $r='t55-'+('a'*32); $s='t55-'+('b'*32); $u='t55-'+('c'*32)
    $attempts=Join-Path $d attempts.csv; $client=Join-Path $d client.csv; $server=Join-Path $d server.csv
    Set-Content $attempts @('AttemptId,Route,Presentation,DisplayNs',"$i,INSIGHTS,qualified,500","$j,PLAN,timed-out,")
    Set-Content $client @('AttemptId,RequestId,Method,Endpoint,DurationNs,Outcome',
        "$i,$r,GET,/api/v1/score,100,200","$i,$s,GET,/api/v1/insights,125,io-failure")
    Set-Content $server @('RequestId,Method,Endpoint,DurationNs,Outcome',"$r,GET,/api/v1/score,75,200")
    $rows = @(& "$PSScriptRoot/Join-T55ScreenTiming.ps1" $attempts $client $server)
    if ($rows.Count -ne 3 -or $rows[0].RequestCount -ne 2 -or $rows[0].Join -ne 'matched' -or
        $rows[0].ServerNs -ne '75' -or $rows[1].Join -ne 'missing-server-observation' -or
        $rows[2].Join -ne 'no-client-observation' -or $rows[2].Presentation -ne 'timed-out') {
        throw 'Timing join fixture failed.'
    }
    Add-Content $server "$u,GET,/api/v1/score,75,200"
    $refused = $false
    try { & "$PSScriptRoot/Join-T55ScreenTiming.ps1" $attempts $client $server | Out-Null }
    catch { if ($_.Exception.Message -notmatch 'Unmatched') { throw }; $refused = $true }
    if (!$refused) { throw 'Expected refusal for unmatched server ID.' }
    'PASS: multi-request join, missing server observation, timeout and unmatched server ID.'
} finally {
    foreach ($name in 'approved.json','observed.json','attempts.csv','client.csv','server.csv') {
        $file = Join-Path $d $name
        if (Test-Path -LiteralPath $file) { Remove-Item -LiteralPath $file }
    }
    Remove-Item -LiteralPath $d
}
