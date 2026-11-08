#Requires -Version 7.0
[CmdletBinding()]
param([string]$GatewayUrl = 'http://localhost:8080')
# Prueba local real. Crea y elimina exclusivamente una cuenta y perfil de prueba aleatorios.
$ErrorActionPreference = 'Stop'
$config = @{}
Get-Content (Join-Path $PSScriptRoot '../../.env') | ForEach-Object {
    if ($_ -match '^([A-Z_]+)=(.*)$') { $config[$Matches[1]] = $Matches[2] }
}
$port = if ($config.KEYCLOAK_PORT) { $config.KEYCLOAK_PORT } else { '8180' }
$base = "http://localhost:$port"
$login = Invoke-RestMethod -NoProxy -TimeoutSec 20 -Method Post -Uri "$base/realms/master/protocol/openid-connect/token" -Body @{
    grant_type='password'; client_id='admin-cli'; username=$config.KEYCLOAK_ADMIN_USERNAME
    password=$config.KEYCLOAK_ADMIN_PASSWORD
}
$headers = @{Authorization="Bearer $($login.access_token)"}
$admin = "$base/admin/realms/complejos-deportivos"
$email = "registro-prueba-$([guid]::NewGuid().ToString('N'))@example.com"
$payload = @{email=$email; password="Prueba-$([guid]::NewGuid().ToString('N'))-Aa1!"; nombre='Prueba'; apellido='Temporal'; preferenciaContacto='EMAIL'}
$endpoint = "$($GatewayUrl.TrimEnd('/'))/api/users/clientes"
$userId = $null
try {
    $response = Invoke-WebRequest -NoProxy -TimeoutSec 30 -SkipHttpErrorCheck -Method Post -Uri $endpoint -ContentType 'application/json' -Body ($payload | ConvertTo-Json)
    Write-Output "Registro anónimo HTTP=$([int]$response.StatusCode)"
    if ($response.StatusCode -ne 201) { throw "Registro falló: $($response.Content)" }
    $profile = $response.Content | ConvertFrom-Json
    if (-not $profile.id -or $profile.PSObject.Properties.Name -contains 'password') { throw 'Respuesta inválida' }
    $users = Invoke-RestMethod -NoProxy -TimeoutSec 20 -Uri "$admin/users?username=$([uri]::EscapeDataString($email))&exact=true" -Headers $headers
    if ($users.Count -ne 1) { throw 'No se encontró una cuenta única en Keycloak' }
    $userId = [guid]::Parse($users[0].id).ToString()
    if (-not $users[0].enabled) { throw 'La cuenta no quedó habilitada' }
    $roles = Invoke-RestMethod -NoProxy -TimeoutSec 20 -Uri "$admin/users/$userId/role-mappings/realm/composite" -Headers $headers
    if ('CLIENTE' -notin $roles.name -or 'ADMIN' -in $roles.name -or 'ADMINISTRADOR' -in $roles.name) { throw 'Roles incorrectos' }
    $credentials = Invoke-RestMethod -NoProxy -TimeoutSec 20 -Uri "$admin/users/$userId/credentials" -Headers $headers
    if ('password' -notin $credentials.type) { throw 'Keycloak no guardó la credencial de contraseña' }
    Write-Output 'Keycloak: cuenta habilitada, contraseña configurada y rol CLIENTE sin ADMIN.'
    $localId = [guid]::Parse($profile.id).ToString()
    $sql = "SELECT count(*) FROM perfil_usuario p JOIN cliente c ON c.usuario_id=p.id WHERE p.id='$localId' AND p.keycloak_id='$userId'"
    $count = & docker compose exec -T app-db psql -U app_admin -d users_db -tAc $sql
    if ($LASTEXITCODE -ne 0 -or "$count".Trim() -ne '1') { throw 'No coincide el perfil PostgreSQL con la identidad Keycloak' }
    Write-Output 'PostgreSQL: perfil y cliente vinculados a la misma identidad.'
    $duplicate = Invoke-WebRequest -NoProxy -TimeoutSec 30 -SkipHttpErrorCheck -Method Post -Uri $endpoint -ContentType 'application/json' -Body ($payload | ConvertTo-Json)
    if ($duplicate.StatusCode -ne 409) { throw 'El correo repetido no devolvió 409' }
    Write-Output 'Duplicado HTTP=409.'
    $payload.password = 'corta'
    $invalid = Invoke-WebRequest -NoProxy -TimeoutSec 30 -SkipHttpErrorCheck -Method Post -Uri $endpoint -ContentType 'application/json' -Body ($payload | ConvertTo-Json)
    if ($invalid.StatusCode -ne 400) { throw 'La contraseña inválida no devolvió 400' }
    Write-Output 'Datos inválidos HTTP=400.'
} finally {
    # La cuenta solo puede ser la del correo único generado por esta ejecución.
    if (-not $userId) {
        $users = Invoke-RestMethod -NoProxy -TimeoutSec 20 -Uri "$admin/users?username=$([uri]::EscapeDataString($email))&exact=true" -Headers $headers
        if ($users.Count -eq 1) { $userId = [guid]::Parse($users[0].id).ToString() }
    }
    if ($userId) {
        $sql = "BEGIN; DELETE FROM cliente WHERE usuario_id IN (SELECT id FROM perfil_usuario WHERE keycloak_id='$userId'); DELETE FROM perfil_usuario WHERE keycloak_id='$userId'; COMMIT;"
        & docker compose exec -T app-db psql -U app_admin -d users_db -v ON_ERROR_STOP=1 -c $sql | Out-Null
        if ($LASTEXITCODE -ne 0) { throw 'No se pudo limpiar el perfil temporal' }
        Invoke-RestMethod -NoProxy -TimeoutSec 20 -Method Delete -Uri "$admin/users/$userId" -Headers $headers | Out-Null
        Write-Output 'Cuenta y perfil temporales eliminados.'
    }
}
