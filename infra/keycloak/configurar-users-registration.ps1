#Requires -Version 7.0
[CmdletBinding()]
param(
    [string]$EnvFile = (Join-Path $PSScriptRoot '../../.env'),
    [string]$UsersEnvFile = (Join-Path $PSScriptRoot '../../msvc-users/.env')
)

$ErrorActionPreference = 'Stop'
$PSDefaultParameterValues = @{'Invoke-RestMethod:TimeoutSec'=20; 'Invoke-RestMethod:NoProxy'=$true}
$config = @{}
Get-Content -LiteralPath $EnvFile | ForEach-Object {
    if ($_ -match '^([A-Z_]+)=(.*)$') { $config[$Matches[1]] = $Matches[2] }
}
if (-not $config.KEYCLOAK_ADMIN_PASSWORD) { throw 'Falta KEYCLOAK_ADMIN_PASSWORD en el .env raíz' }
if (-not (Test-Path -LiteralPath $UsersEnvFile)) { throw 'Crear primero msvc-users/.env desde su plantilla' }
$port = if ($config.KEYCLOAK_PORT) { $config.KEYCLOAK_PORT } else { '8180' }
$baseUrl = "http://localhost:$port"
$username = if ($config.KEYCLOAK_ADMIN_USERNAME) { $config.KEYCLOAK_ADMIN_USERNAME } else { 'admin' }

# Solo el aprovisionamiento usa al administrador. La aplicación usa client_credentials.
$login = Invoke-RestMethod -Method Post -Uri "$baseUrl/realms/master/protocol/openid-connect/token" -Body @{
    grant_type='password'; client_id='admin-cli'; username=$username; password=$config.KEYCLOAK_ADMIN_PASSWORD
}
$headers = @{Authorization="Bearer $($login.access_token)"}
$adminUrl = "$baseUrl/admin/realms/complejos-deportivos"
$clientId = 'users-registration'
$clients = Invoke-RestMethod -Uri "$adminUrl/clients?clientId=$clientId" -Headers $headers
if (-not $clients) {
    $client = @{
        clientId=$clientId; protocol='openid-connect'; enabled=$true; publicClient=$false
        serviceAccountsEnabled=$true; standardFlowEnabled=$false; directAccessGrantsEnabled=$false
        implicitFlowEnabled=$false; fullScopeAllowed=$false
    }
    Invoke-RestMethod -Method Post -Uri "$adminUrl/clients" -Headers $headers -ContentType 'application/json' -Body ($client | ConvertTo-Json) | Out-Null
    $clients = Invoke-RestMethod -Uri "$adminUrl/clients?clientId=$clientId" -Headers $headers
}
$id = $clients[0].id
$service = Invoke-RestMethod -Uri "$adminUrl/clients/$id/service-account-user" -Headers $headers
$management = (Invoke-RestMethod -Uri "$adminUrl/clients?clientId=realm-management" -Headers $headers)[0]
$roles = @()
foreach ($name in @('manage-users','view-users','view-realm')) {
    $roles += Invoke-RestMethod -Uri "$adminUrl/clients/$($management.id)/roles/$name" -Headers $headers
}
$body = ConvertTo-Json -InputObject $roles -Depth 10
Invoke-RestMethod -Method Post -Uri "$adminUrl/users/$($service.id)/role-mappings/clients/$($management.id)" -Headers $headers -ContentType 'application/json' -Body $body | Out-Null
Invoke-RestMethod -Method Post -Uri "$adminUrl/clients/$id/scope-mappings/clients/$($management.id)" -Headers $headers -ContentType 'application/json' -Body $body | Out-Null

# No rotar secretos en ejecuciones posteriores ni imprimirlos.
$secret = (Invoke-RestMethod -Uri "$adminUrl/clients/$id/client-secret" -Headers $headers).value
if (-not $secret) { throw 'Keycloak no devolvió el secreto del cliente de servicio' }
$updates = @{
    KEYCLOAK_BASE_URL=$baseUrl; KEYCLOAK_REALM='complejos-deportivos'
    KEYCLOAK_REGISTRATION_CLIENT_ID=$clientId; KEYCLOAK_REGISTRATION_CLIENT_SECRET=$secret
}
$text = [IO.File]::ReadAllText((Resolve-Path -LiteralPath $UsersEnvFile))
foreach ($key in $updates.Keys) {
    $line = "$key=$($updates[$key])"
    $pattern = '(?m)^' + [regex]::Escape($key) + '=[^\r\n]*'
    if ([regex]::IsMatch($text, $pattern)) {
        $text = [regex]::Replace($text, $pattern, [System.Text.RegularExpressions.MatchEvaluator]{param($match) $line})
    } else { $text = $text.TrimEnd() + "`n$line`n" }
}
[IO.File]::WriteAllText((Resolve-Path -LiteralPath $UsersEnvFile), $text, [Text.UTF8Encoding]::new($false))
Write-Output 'Cuenta de servicio configurada. Secreto guardado solo en msvc-users/.env. Reiniciar users.'
