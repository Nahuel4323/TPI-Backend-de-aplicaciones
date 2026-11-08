#Requires -Version 7.0
[CmdletBinding()]
param([string]$EnvFile = (Join-Path $PSScriptRoot '../../.env'))
$ErrorActionPreference='Stop'
$config=@{}
Get-Content -LiteralPath $EnvFile | ForEach-Object {
    if($_ -match '^([A-Z_]+)=(.*)$'){$config[$Matches[1]]=$Matches[2]}
}
$port=if($config.KEYCLOAK_PORT){$config.KEYCLOAK_PORT}else{'8180'}
$user=if($config.KEYCLOAK_ADMIN_USERNAME){$config.KEYCLOAK_ADMIN_USERNAME}else{'admin'}
$base="http://localhost:$port"
$token=Invoke-RestMethod -NoProxy -TimeoutSec 20 -Method Post -Uri "$base/realms/master/protocol/openid-connect/token" -Body @{
    grant_type='password';client_id='admin-cli';username=$user;password=$config.KEYCLOAK_ADMIN_PASSWORD
}
$headers=@{Authorization="Bearer $($token.access_token)"}
$admin="$base/admin/realms/complejos-deportivos"
$desired=Get-Content (Join-Path $PSScriptRoot 'realms/complejos-deportivos-realm.json') -Raw | ConvertFrom-Json
$client=$desired.clients | Where-Object clientId -eq 'postman-auto'
if(-not $client){throw 'Falta la definición de postman-auto en el realm versionado'}
$existing=Invoke-RestMethod -NoProxy -TimeoutSec 20 -Uri "$admin/clients?clientId=postman-auto" -Headers $headers
if($existing){
    Invoke-RestMethod -NoProxy -TimeoutSec 20 -Method Put -Uri "$admin/clients/$($existing[0].id)" -Headers $headers -ContentType 'application/json' -Body ($client|ConvertTo-Json -Depth 20)|Out-Null
}else{
    Invoke-RestMethod -NoProxy -TimeoutSec 20 -Method Post -Uri "$admin/clients" -Headers $headers -ContentType 'application/json' -Body ($client|ConvertTo-Json -Depth 20)|Out-Null
}
Write-Output 'postman-auto configurado para login automático de pruebas locales. No se modificaron usuarios ni postman-local (PKCE).'
