#Requires -Version 7.0
# Comprueba el script guardado con Postman CLI y un administrador Keycloak temporal.
$ErrorActionPreference='Stop'
$config=@{}
$repo=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
Get-Content (Join-Path $repo '.env')|ForEach-Object{if($_ -match '^([A-Z_]+)=(.*)$'){$config[$Matches[1]]=$Matches[2]}}
$port=if($config.KEYCLOAK_PORT){$config.KEYCLOAK_PORT}else{'8180'}
$base="http://localhost:$port"
$login=Invoke-RestMethod -NoProxy -TimeoutSec 20 -Method Post -Uri "$base/realms/master/protocol/openid-connect/token" -Body @{
    grant_type='password';client_id='admin-cli';username=$config.KEYCLOAK_ADMIN_USERNAME;password=$config.KEYCLOAK_ADMIN_PASSWORD
}
$headers=@{Authorization="Bearer $($login.access_token)"}
$admin="$base/admin/realms/complejos-deportivos"
$suffix=[guid]::NewGuid().ToString('N')
$email="postman-auth-$suffix@example.com"
$password="Prueba-$suffix-Aa1!"
$userId=$null
$target=Join-Path $repo 'msvc-users/target'
[IO.Directory]::CreateDirectory($target)|Out-Null
$collectionFile=Join-Path $target "postman-auth-$suffix.collection.json"
$environmentFile=Join-Path $target "postman-auth-$suffix.environment.json"
try{
    $body=@{username=$email;email=$email;firstName='Prueba';lastName='Temporal';enabled=$true;emailVerified=$true;
        credentials=@(@{type='password';value=$password;temporary=$false})}
    Invoke-RestMethod -NoProxy -TimeoutSec 20 -Method Post -Uri "$admin/users" -Headers $headers -ContentType 'application/json' -Body ($body|ConvertTo-Json -Depth 10)|Out-Null
    $users=Invoke-RestMethod -NoProxy -TimeoutSec 20 -Uri "$admin/users?username=$([uri]::EscapeDataString($email))&exact=true" -Headers $headers
    $userId=[guid]::Parse($users[0].id).ToString()
    $direct=Invoke-RestMethod -NoProxy -TimeoutSec 20 -Uri "$admin/users/$userId/role-mappings/realm" -Headers $headers
    if($direct){Invoke-RestMethod -NoProxy -TimeoutSec 20 -Method Delete -Uri "$admin/users/$userId/role-mappings/realm" -Headers $headers -ContentType 'application/json' -Body (ConvertTo-Json -InputObject @($direct) -Depth 10)|Out-Null}
    $role=Invoke-RestMethod -NoProxy -TimeoutSec 20 -Uri "$admin/roles/ADMIN" -Headers $headers
    Invoke-RestMethod -NoProxy -TimeoutSec 20 -Method Post -Uri "$admin/users/$userId/role-mappings/realm" -Headers $headers -ContentType 'application/json' -Body (ConvertTo-Json -InputObject @($role) -Depth 10)|Out-Null
    $source=@(Get-Content (Join-Path $PSScriptRoot 'autenticar-admin.js'))
    $collection=@{info=@{name='Prueba local del login automático';schema='https://schema.getpostman.com/json/collection/v2.1.0/collection.json'};
        item=@(@{name='Autenticar y consultar identidad';request=@{method='GET';url="$base/realms/complejos-deportivos/protocol/openid-connect/userinfo";
            auth=@{type='bearer';bearer=@(@{key='token';value='{{admin_access_token}}';type='string'})}};
            event=@(@{listen='prerequest';script=@{type='text/javascript';exec=$source}},@{listen='test';script=@{type='text/javascript';exec=@(
                "pm.test('HTTP 200 autenticado',()=>pm.response.to.have.status(200));",
                "pm.test('Identidad correcta',()=>pm.expect(pm.response.json().email).to.eql(pm.environment.get('admin_login_email')));",
                "pm.test('Token temporal disponible',()=>pm.expect(pm.variables.get('admin_access_token')).to.be.a('string').and.not.empty);",
                "pm.test('Token no persistido en entorno',()=>pm.expect(pm.environment.get('admin_access_token')).to.eql(''));"
            )}})})}
    $environment=@{name='Entorno temporal de autenticación';values=@(
        @{key='keycloak_url';value=$base;enabled=$true},@{key='keycloak_realm';value='complejos-deportivos';enabled=$true},
        @{key='keycloak_auto_client_id';value='postman-auto';enabled=$true},@{key='admin_login_email';value=$email;enabled=$true;type='secret'},
        @{key='admin_login_password';value=$password;enabled=$true;type='secret'},@{key='admin_access_token';value='';enabled=$true;type='secret'})}
    $collection|ConvertTo-Json -Depth 30|Set-Content -LiteralPath $collectionFile -Encoding utf8
    $environment|ConvertTo-Json -Depth 30|Set-Content -LiteralPath $environmentFile -Encoding utf8
    & postman collection run $collectionFile -e $environmentFile --no-report-events
    if($LASTEXITCODE -ne 0){throw 'La prueba de autenticación automática en Postman CLI falló'}
}finally{
    if(-not $userId){
        $users=Invoke-RestMethod -NoProxy -TimeoutSec 20 -Uri "$admin/users?username=$([uri]::EscapeDataString($email))&exact=true" -Headers $headers
        if($users.Count -eq 1){$userId=[guid]::Parse($users[0].id).ToString()}
    }
    if($userId){Invoke-RestMethod -NoProxy -TimeoutSec 20 -Method Delete -Uri "$admin/users/$userId" -Headers $headers|Out-Null}
    # Solo archivos propios, de nombres aleatorios, dentro del target del repositorio.
    foreach($file in @($collectionFile,$environmentFile)){
        $absolute=[IO.Path]::GetFullPath($file)
        if(-not $absolute.StartsWith([IO.Path]::GetFullPath($target)+[IO.Path]::DirectorySeparatorChar)){throw 'Ruta temporal fuera de target'}
        if([IO.File]::Exists($absolute)){[IO.File]::Delete($absolute)}
    }
    Write-Output 'Cuenta y credenciales temporales de prueba eliminadas.'
}
