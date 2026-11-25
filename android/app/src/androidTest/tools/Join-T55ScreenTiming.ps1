[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$AttemptsCsv,
    [Parameter(Mandatory = $true)][string]$ClientCsv,
    [Parameter(Mandatory = $true)][string]$ServerCsv
)
$ErrorActionPreference = 'Stop'
$attempts = @{}
$requests = @{}
$server = @{}
$routes = @{
    HOME = @('/api/v1/dashboard')
    RECORDS = @('/api/v1/transactions', '/api/v1/accounts', '/api/v1/categories')
    PLAN = @('/api/v1/budgets', '/api/v1/categories')
    INSIGHTS = @('/api/v1/insights', '/api/v1/score', '/api/v1/score-inputs/{month}')
}

foreach ($row in Import-Csv -LiteralPath $AttemptsCsv) {
    if ($row.AttemptId -cnotmatch '^t55-[a-f0-9]{32}$' -or $attempts.ContainsKey($row.AttemptId) -or
        $row.Route -cnotin $routes.Keys -or
        $row.Presentation -cnotin @('qualified','unqualified','failed','timed-out')) {
        throw 'Invalid or duplicate attempt record.'
    }
    if ($row.Presentation -ceq 'qualified') {
        if ($row.DisplayNs -notmatch '^[1-9][0-9]{0,17}$') { throw 'Missing qualified duration.' }
    } elseif ($row.DisplayNs) {
        throw 'Unqualified attempts cannot contain a displayed duration.'
    }
    $attempts[$row.AttemptId] = $row
}

foreach ($row in Import-Csv -LiteralPath $ClientCsv) {
    $attempt = $attempts[$row.AttemptId]
    if (!$attempt -or $row.RequestId -cnotmatch '^t55-[a-f0-9]{32}$' -or
        $requests.ContainsKey($row.RequestId) -or $row.Method -cne 'GET' -or
        $row.Endpoint -cnotin $routes[$attempt.Route] -or
        $row.DurationNs -notmatch '^[0-9]{1,18}$' -or
        $row.Outcome -cnotmatch '^([1-5][0-9]{2}|io-failure|timed-out|cancelled)$') {
        throw 'Invalid or duplicate client timing record.'
    }
    $requests[$row.RequestId] = $row
}

foreach ($row in Import-Csv -LiteralPath $ServerCsv) {
    $client = $requests[$row.RequestId]
    if (!$client -or $row.RequestId -cne $client.RequestId -or $server.ContainsKey($row.RequestId) -or
        $row.Method -cne $client.Method -or $row.Endpoint -cne $client.Endpoint -or
        $row.DurationNs -notmatch '^[0-9]{1,18}$' -or $row.Outcome -cnotmatch '^[1-5][0-9]{2}$') {
        throw 'Unmatched, inconsistent or duplicate server timing record.'
    }
    $server[$row.RequestId] = $row
}

foreach ($attemptId in ($attempts.Keys | Sort-Object)) {
    $attempt = $attempts[$attemptId]
    $related = @($requests.Values | Where-Object AttemptId -CEQ $attemptId | Sort-Object RequestId)
    if (!$related.Count) { $related = @($null) }
    foreach ($client in $related) {
        $match = if ($client) { $server[$client.RequestId] }
        $join = if (!$client) { 'no-client-observation' }
                elseif (!$match) { 'missing-server-observation' }
                elseif ($client.Outcome -ceq $match.Outcome) { 'matched' }
                else { 'outcome-mismatch' }
        [pscustomobject]@{
            AttemptId = $attemptId
            Route = $attempt.Route
            Presentation = $attempt.Presentation
            DisplayNs = $attempt.DisplayNs
            RequestCount = if ($client) { $related.Count } else { 0 }
            RequestId = $client.RequestId
            Method = $client.Method
            Endpoint = $client.Endpoint
            ClientNs = $client.DurationNs
            ClientOutcome = $client.Outcome
            ServerNs = $match.DurationNs
            ServerOutcome = $match.Outcome
            Join = $join
        }
    }
}
