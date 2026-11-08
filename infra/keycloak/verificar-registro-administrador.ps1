#Requires -Version 7.0
[CmdletBinding()]
param([string]$GatewayUrl = 'http://localhost:8080')
$ErrorActionPreference = 'Stop'
$PSDefaultParameterValues = @{'Invoke-RestMethod:TimeoutSec'=20; 'Invoke-RestMethod:NoProxy'=$true; 'Invoke-WebRequest:TimeoutSec'=30; 'Invoke-WebRequest:NoProxy'=$true}
$config=@{}
Get-Content (Join-Path $PSScriptRoot '../../.env') | ForEach-Object {
    if ($_ -match '^([A-Z_]+)=(.*)$') {$config[$Matches[1]]=$Matches[2]}
}
$port=if($config.KEYCLOAK_PORT){$config.KEYCLOAK_PORT}else{'8180'}
$base="http://localhost:$port"
$login=Invoke-RestMethod -Method Post -Uri "$base/realms/master/protocol/openid-connect/token" -Body @{
    grant_type='password';client_id='admin-cli';username=$config.KEYCLOAK_ADMIN_USERNAME;password=$config.KEYCLOAK_ADMIN_PASSWORD
}
$headers=@{Authorization="Bearer $($login.access_token)"}
$admin="$base/admin/realms/complejos-deportivos"
$suffix=[guid]::NewGuid().ToString('N')
$callerName="prueba-admin-$suffix"
$email="prueba-admin-$suffix@example.com"
$clientId=$null
$userId=$null
$payload=@{email=$email;password="Prueba-$suffix-Aa1!";nombre='Prueba';apellido='Temporal';telefonoLaboral='3511234567'}
$endpoint="$($GatewayUrl.TrimEnd('/'))/api/users/administradores"
function Assert-Status($response, [int]$expected) {
    if ([int]$response.StatusCode -ne $expected) { throw "Se esperaba HTTP $expected y se recibió $([int]$response.StatusCode): $($response.Content)" }
    Write-Output "HTTP $expected verificado."
}
try {
    # Cliente de prueba efímero: no modifica permisos de las cuentas de servicio existentes.
    $client=@{clientId=$callerName;protocol='openid-connect';enabled=$true;publicClient=$false;serviceAccountsEnabled=$true;
        standardFlowEnabled=$false;directAccessGrantsEnabled=$false;fullScopeAllowed=$true;
        protocolMappers=@(@{name='backend-api-audience';protocol='openid-connect';protocolMapper='oidc-audience-mapper';
            config=@{'included.custom.audience'='backend-api';'access.token.claim'='true';'id.token.claim'='false'}})}
    Invoke-RestMethod -Method Post -Uri "$admin/clients" -Headers $headers -ContentType 'application/json' -Body ($client | ConvertTo-Json -Depth 15) | Out-Null
    $clientId=(Invoke-RestMethod -Uri "$admin/clients?clientId=$callerName" -Headers $headers)[0].id
    $service=Invoke-RestMethod -Uri "$admin/clients/$clientId/service-account-user" -Headers $headers
    $secret=(Invoke-RestMethod -Uri "$admin/clients/$clientId/client-secret" -Headers $headers).value
    $role=Invoke-RestMethod -Uri "$admin/roles/ADMIN" -Headers $headers
    $roleBody=ConvertTo-Json -InputObject @($role) -Depth 10
    $tokenUrl="$base/realms/complejos-deportivos/protocol/openid-connect/token"
    $form=@{grant_type='client_credentials';client_id=$callerName;client_secret=$secret}

    $anonymous=Invoke-WebRequest -SkipHttpErrorCheck -Method Post -Uri $endpoint -ContentType 'application/json' -Body ($payload | ConvertTo-Json)
    Assert-Status $anonymous 401
    # Cuenta de servicio sin ADMIN: el realm le otorga CLIENTE por defecto.
    $token=Invoke-RestMethod -Method Post -Uri $tokenUrl -Body $form
    $denied=Invoke-WebRequest -SkipHttpErrorCheck -Method Post -Uri $endpoint -Headers @{Authorization="Bearer $($token.access_token)"} -ContentType 'application/json' -Body ($payload | ConvertTo-Json)
    Assert-Status $denied 403

    Invoke-RestMethod -Method Post -Uri "$admin/users/$($service.id)/role-mappings/realm" -Headers $headers -ContentType 'application/json' -Body $roleBody | Out-Null
    $token=Invoke-RestMethod -Method Post -Uri $tokenUrl -Body $form
    $callerHeaders=@{Authorization="Bearer $($token.access_token)"}
    $response=Invoke-WebRequest -SkipHttpErrorCheck -Method Post -Uri $endpoint -Headers $callerHeaders -ContentType 'application/json' -Body ($payload | ConvertTo-Json)
    Assert-Status $response 201
    $profile=$response.Content | ConvertFrom-Json
    $localId=[guid]::Parse($profile.id).ToString()
    if ($profile.PSObject.Properties.Name -contains 'password') {throw 'La respuesta expone contraseña'}
    $users=Invoke-RestMethod -Uri "$admin/users?username=$([uri]::EscapeDataString($email))&exact=true" -Headers $headers
    if($users.Count -ne 1 -or -not $users[0].enabled){throw 'Cuenta administrativa inexistente o deshabilitada'}
    $userId=[guid]::Parse($users[0].id).ToString()
    $roles=Invoke-RestMethod -Uri "$admin/users/$userId/role-mappings/realm/composite" -Headers $headers
    if('ADMIN' -notin $roles.name -or 'CLIENTE' -in $roles.name){throw 'Roles administrativos incorrectos'}
    Write-Output 'Keycloak: ADMIN sin CLIENTE verificado.'
    $sql="SELECT count(*) FROM perfil_usuario p JOIN administrador a ON a.usuario_id=p.id WHERE p.id='$localId' AND p.keycloak_id='$userId' AND p.tipo_perfil='ADMINISTRADOR'"
    $count=& docker compose exec -T app-db psql -U app_admin -d users_db -tAc $sql
    if($LASTEXITCODE -ne 0 -or "$count".Trim() -ne '1'){throw 'El administrador no quedó vinculado al perfil PostgreSQL'}
    Write-Output 'PostgreSQL: perfil administrativo vinculado verificado.'
    $duplicate=Invoke-WebRequest -SkipHttpErrorCheck -Method Post -Uri $endpoint -Headers $callerHeaders -ContentType 'application/json' -Body ($payload | ConvertTo-Json)
    Assert-Status $duplicate 409
    $payload.password='corta'
    $invalid=Invoke-WebRequest -SkipHttpErrorCheck -Method Post -Uri $endpoint -Headers $callerHeaders -ContentType 'application/json' -Body ($payload | ConvertTo-Json)
    Assert-Status $invalid 400
} finally {
    if(-not $userId){
        $users=Invoke-RestMethod -Uri "$admin/users?username=$([uri]::EscapeDataString($email))&exact=true" -Headers $headers
        if($users.Count -eq 1){$userId=[guid]::Parse($users[0].id).ToString()}
    }
    try {
        if($userId){
            $sql="BEGIN; DELETE FROM administrador WHERE usuario_id IN (SELECT id FROM perfil_usuario WHERE keycloak_id='$userId'); DELETE FROM perfil_usuario WHERE keycloak_id='$userId'; COMMIT;"
            & docker compose exec -T app-db psql -U app_admin -d users_db -v ON_ERROR_STOP=1 -c $sql | Out-Null
            if($LASTEXITCODE -ne 0){throw 'No se pudo limpiar el perfil temporal'}
            Invoke-RestMethod -Method Delete -Uri "$admin/users/$userId" -Headers $headers | Out-Null
        }
    } finally {
        if($clientId){Invoke-RestMethod -Method Delete -Uri "$admin/clients/$clientId" -Headers $headers | Out-Null}
    }
    Write-Output 'Cuenta, perfil y cliente de prueba eliminados.'
}
