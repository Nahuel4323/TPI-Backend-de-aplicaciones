param(
    [string]$BaseUrl,
    [string]$EnvFile = (Join-Path $PSScriptRoot '../../.env')
)

$ErrorActionPreference = 'Stop'
$PSDefaultParameterValues = @{
    'Invoke-RestMethod:TimeoutSec' = 20
    'Invoke-RestMethod:NoProxy' = $true
}
$localConfig = @{}
Get-Content -LiteralPath $EnvFile | ForEach-Object {
    if ($_ -match '^([A-Z_]+)=(.*)$') {
        $localConfig[$Matches[1]] = $Matches[2].Trim().Trim('"').Trim("'")
    }
}
if (-not $BaseUrl) {
    $port = if ($localConfig.KEYCLOAK_PORT) { $localConfig.KEYCLOAK_PORT } else { '8180' }
    $BaseUrl = "http://localhost:$port"
}
$BaseUrl = $BaseUrl.TrimEnd('/')
$adminUser = if ($localConfig.KEYCLOAK_ADMIN_USERNAME) { $localConfig.KEYCLOAK_ADMIN_USERNAME } else { 'admin' }
if (-not $localConfig.KEYCLOAK_ADMIN_PASSWORD) { throw 'Completar KEYCLOAK_ADMIN_PASSWORD en el .env raiz' }

# Los secretos y tokens nunca se imprimen ni se guardan en archivos de salida.
$login = Invoke-RestMethod -Method Post -Uri "$BaseUrl/realms/master/protocol/openid-connect/token" -Body @{
    grant_type='password'; client_id='admin-cli'; username=$adminUser
    password=$localConfig.KEYCLOAK_ADMIN_PASSWORD
}
$headers = @{ Authorization = "Bearer $($login.access_token)" }
$realmName = 'complejos-deportivos'
$adminUrl = "$BaseUrl/admin/realms/$realmName"
$desired = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'realms/complejos-deportivos-realm.json') -Raw | ConvertFrom-Json

# Actualizar solo la configuracion del registro. No reemplazar ni reimportar el realm.
$patch = @{}
foreach ($field in @('registrationAllowed','registrationEmailAsUsername','editUsernameAllowed',
        'loginWithEmailAllowed','duplicateEmailsAllowed','verifyEmail','passwordPolicy')) {
    $patch[$field] = $desired.$field
}
Invoke-RestMethod -Method Put -Uri $adminUrl -Headers $headers -ContentType 'application/json' -Body ($patch | ConvertTo-Json) | Out-Null

$roles = Invoke-RestMethod -Uri "$adminUrl/roles" -Headers $headers
foreach ($roleName in @('CLIENTE','ADMIN')) {
    if (-not ($roles | Where-Object name -eq $roleName)) {
        Invoke-RestMethod -Method Post -Uri "$adminUrl/roles" -Headers $headers -ContentType 'application/json' -Body (@{name=$roleName} | ConvertTo-Json) | Out-Null
    }
}
$realm = Invoke-RestMethod -Uri $adminUrl -Headers $headers
$defaultRole = $realm.defaultRole.name
if (-not $defaultRole) { throw 'El realm no informa su rol predeterminado' }
$defaultUrl = "$adminUrl/roles/$([Uri]::EscapeDataString($defaultRole))/composites"
$composites = @(Invoke-RestMethod -Uri $defaultUrl -Headers $headers)
# Nunca otorgar ADMIN por autorregistro; conservar otros roles por defecto.
$adminDefaults = @($composites | Where-Object { $_.name -in @('ADMIN','ADMINISTRADOR') -and -not $_.clientRole })
if ($adminDefaults.Count -gt 0) {
    Invoke-RestMethod -Method Delete -Uri $defaultUrl -Headers $headers -ContentType 'application/json' -Body (ConvertTo-Json -InputObject $adminDefaults -Depth 10) | Out-Null
}
$clienteRole = Invoke-RestMethod -Uri "$adminUrl/roles/CLIENTE" -Headers $headers
if (-not ($composites | Where-Object { $_.name -eq 'CLIENTE' -and -not $_.clientRole })) {
    Invoke-RestMethod -Method Post -Uri $defaultUrl -Headers $headers -ContentType 'application/json' -Body (ConvertTo-Json -InputObject @($clienteRole) -Depth 10) | Out-Null
}

# Crear/actualizar solamente el cliente de pruebas aprobado.
$client = $desired.clients | Where-Object clientId -eq 'postman-local'
$existing = @(Invoke-RestMethod -Uri "$adminUrl/clients?clientId=postman-local" -Headers $headers | ForEach-Object { $_ })
if ($existing.Count -gt 0) {
    $client | Add-Member -NotePropertyName id -NotePropertyValue $existing[0].id -Force
    Invoke-RestMethod -Method Put -Uri "$adminUrl/clients/$($existing[0].id)" -Headers $headers -ContentType 'application/json' -Body ($client | ConvertTo-Json -Depth 20) | Out-Null
} else {
    Invoke-RestMethod -Method Post -Uri "$adminUrl/clients" -Headers $headers -ContentType 'application/json' -Body ($client | ConvertTo-Json -Depth 20) | Out-Null
}
$savedClient = @(Invoke-RestMethod -Uri "$adminUrl/clients?clientId=postman-local" -Headers $headers | ForEach-Object { $_ })[0]
$scopes = @(Invoke-RestMethod -Uri "$adminUrl/client-scopes" -Headers $headers | ForEach-Object { $_ })
foreach ($scopeName in $client.defaultClientScopes) {
    $scope = $scopes | Where-Object name -eq $scopeName | Select-Object -First 1
    if ($scope) {
        Invoke-RestMethod -Method Put -Uri "$adminUrl/clients/$($savedClient.id)/default-client-scopes/$($scope.id)" -Headers $headers | Out-Null
    }
}
$updated = Invoke-RestMethod -Uri $adminUrl -Headers $headers
$effectiveDefaults = @(Invoke-RestMethod -Uri "$defaultUrl/realm" -Headers $headers)
if (-not $updated.registrationAllowed -or -not $updated.registrationEmailAsUsername -or $updated.duplicateEmailsAllowed) {
    throw 'No se pudo verificar la configuracion del autorregistro'
}
if ($effectiveDefaults.name -contains 'ADMIN' -or $effectiveDefaults.name -contains 'ADMINISTRADOR') {
    throw 'Revisar roles compuestos: el rol por defecto incluye permisos administrativos'
}
Write-Output 'Registro habilitado: correo unico, rol CLIENTE y cliente postman-local con PKCE S256. Usuarios y datos conservados.'
