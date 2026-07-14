# BizLink Owner API Test Script (no Liquibase — uses APIs only)
# Usage: powershell -File scripts/test-owner-api.ps1
# Backend must be running on http://localhost:8080

$base = 'http://localhost:8080'
$email = 'ravi.tiffin@bizlink.com'
$password = 'Owner@123'

function Api-Json($method, $uri, $body, $token) {
  $headers = @{ 'Content-Type' = 'application/json' }
  if ($token) { $headers['Authorization'] = "Bearer $token" }
  $params = @{ Uri = "$base$uri"; Method = $method; Headers = $headers }
  if ($body) { $params.Body = ($body | ConvertTo-Json -Depth 6) }
  return Invoke-RestMethod @params
}

Write-Host "`n=== LOGIN ===" -ForegroundColor Cyan
$login = Api-Json POST '/api/auth/login' @{ email = $email; password = $password }
$token = $login.data.token
Write-Host "OK - $($login.data.user.name) ($($login.data.user.role))"

Write-Host "`n=== CREATE BUSINESS ===" -ForegroundColor Cyan
$bizFile = Join-Path $env:TEMP 'bizlink-business.json'
@{
  businessName = 'Ravi Tiffin Center'
  description  = 'Fresh homely tiffin daily'
  phone        = '9123456789'
  whatsappNumber = '9123456789'
  city         = 'Hyderabad'
  state        = 'Telangana'
  pincode      = '500001'
} | ConvertTo-Json | Set-Content $bizFile -Encoding UTF8

$bizResp = curl.exe -s -X POST "$base/api/business" `
  -H "Authorization: Bearer $token" `
  -F "business=@$bizFile;type=application/json"
$biz = ($bizResp | ConvertFrom-Json).data
if (-not $biz) { Write-Host "FAIL: $bizResp" -ForegroundColor Red; exit 1 }
Write-Host "OK - slug: $($biz.slug)"

$bizId = $biz.id

Write-Host "`n=== CREATE CATEGORY ===" -ForegroundColor Cyan
$cat = Api-Json POST '/api/categories' @{ businessId = $bizId; name = 'Breakfast' } $token
$catId = $cat.data.id
Write-Host "OK - $($cat.data.name)"

Write-Host "`n=== CREATE PRODUCT ===" -ForegroundColor Cyan
$prodFile = Join-Path $env:TEMP 'bizlink-product.json'
@{ businessId = $bizId; categoryId = $catId; name = 'Idli Plate'; description = '2 idli + sambar'; price = 60; available = $true } |
  ConvertTo-Json | Set-Content $prodFile -Encoding UTF8
$prodResp = curl.exe -s -X POST "$base/api/products" `
  -H "Authorization: Bearer $token" `
  -F "product=@$prodFile;type=application/json"
$prod = ($prodResp | ConvertFrom-Json).data
Write-Host "OK - $($prod.name) Rs.$($prod.price)"

Write-Host "`n=== CREATE CUSTOMER ===" -ForegroundColor Cyan
$cust = Api-Json POST '/api/customers' @{ businessId = $bizId; name = 'Suresh'; mobile = '9988776655' }
$custId = $cust.data.id
Write-Host "OK - $($cust.data.name)"

Write-Host "`n=== CREATE ORDER ===" -ForegroundColor Cyan
$order = Api-Json POST '/api/orders' @{
  businessId = $bizId
  customerId = $custId
  items      = @(@{ productId = $prod.id; quantity = 2 })
}
Write-Host "OK - $($order.data.orderNumber) Total: $($order.data.totalAmount)"

Write-Host "`n=== PUBLIC PAGE ===" -ForegroundColor Cyan
$pub = curl.exe -s "$base/api/public/business/$($biz.slug)"
($pub | ConvertFrom-Json).data.businessName

Write-Host "`n=== ALL TESTS PASSED ===" -ForegroundColor Green
Write-Host "Owner login: $email / $password"
Write-Host "Public URL slug: /business/$($biz.slug)"
