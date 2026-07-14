# BizLink - Seed a full category-wise menu via APIs (no Liquibase)
# Usage:
#   powershell -File scripts/seed-menu.ps1
#   powershell -File scripts/seed-menu.ps1 -Email you@mail.com -Password Yourpass
# Backend must be running on http://localhost:8080
# It logs in, picks your first business, and adds categories + items.
# After it finishes, scanning that business QR shows exactly these items,
# grouped category-wise. Items belong ONLY to this business.

param(
  [string]$Base = 'http://localhost:8080',
  [string]$Email = 'ravi.tiffin@bizlink.com',
  [string]$Password = 'Owner@123'
)

$ErrorActionPreference = 'Stop'

function Api-Json($method, $uri, $body, $token) {
  $headers = @{ 'Content-Type' = 'application/json' }
  if ($token) { $headers['Authorization'] = "Bearer $token" }
  $params = @{ Uri = "$Base$uri"; Method = $method; Headers = $headers }
  if ($body) { $params.Body = ($body | ConvertTo-Json -Depth 6) }
  return Invoke-RestMethod @params
}

function New-Product($token, $bizId, $catId, $name, $desc, $price) {
  $file = Join-Path $env:TEMP ("bizlink-prod-" + [guid]::NewGuid().ToString('N') + '.json')
  @{ businessId = $bizId; categoryId = $catId; name = $name; description = $desc; price = $price; available = $true } |
    ConvertTo-Json | Set-Content $file -Encoding UTF8
  $resp = curl.exe -s -X POST "$Base/api/products" `
    -H "Authorization: Bearer $token" `
    -F "product=@$file;type=application/json"
  Remove-Item $file -ErrorAction SilentlyContinue
  $p = ($resp | ConvertFrom-Json).data
  if (-not $p) { Write-Host "   FAIL: $resp" -ForegroundColor Red; return }
  Write-Host ("   + {0}  Rs.{1}" -f $p.name, $p.price)
}

Write-Host "`n=== LOGIN ===" -ForegroundColor Cyan
$login = Api-Json POST '/api/auth/login' @{ email = $Email; password = $Password }
$token = $login.data.token
Write-Host ("OK - {0} ({1})" -f $login.data.user.name, $login.data.user.role)

Write-Host "`n=== FIND BUSINESS ===" -ForegroundColor Cyan
$mine = Api-Json GET '/api/business/my' $null $token
$biz = $mine.data | Select-Object -First 1
if (-not $biz) { Write-Host "No business found for this owner. Create one first." -ForegroundColor Red; exit 1 }
$bizId = $biz.id
Write-Host ("OK - {0}  (slug: {1})" -f $biz.businessName, $biz.slug)

# Category -> items. Each item ONLY added to THIS business.
$menu = [ordered]@{
  'Starters'    = @(
    @{ n = 'Paneer Tikka';      d = 'Chargrilled cottage cheese';   p = 220 },
    @{ n = 'Veg Manchurian';    d = 'Crispy, tangy, spicy';         p = 160 },
    @{ n = 'Chicken 65';        d = 'Spicy fried chicken';          p = 240 }
  )
  'Main Course' = @(
    @{ n = 'Paneer Butter Masala'; d = 'Rich creamy gravy';         p = 260 },
    @{ n = 'Chicken Biryani';      d = 'Hyderabadi dum biryani';    p = 290 },
    @{ n = 'Dal Tadka';            d = 'Yellow dal, ghee tempered';  p = 180 },
    @{ n = 'Veg Fried Rice';       d = 'Wok-tossed rice';            p = 170 }
  )
  'Breads'      = @(
    @{ n = 'Butter Naan';  d = 'Soft tandoor naan';  p = 45 },
    @{ n = 'Tandoori Roti'; d = 'Whole wheat';        p = 30 },
    @{ n = 'Garlic Naan';  d = 'Garlic + butter';    p = 55 }
  )
  'Beverages'   = @(
    @{ n = 'Sweet Lassi';   d = 'Chilled yogurt drink'; p = 70 },
    @{ n = 'Masala Chai';   d = 'Spiced Indian tea';    p = 30 },
    @{ n = 'Fresh Lime Soda'; d = 'Sweet / salt';       p = 60 }
  )
  'Desserts'    = @(
    @{ n = 'Gulab Jamun';  d = '2 pcs, warm';       p = 80 },
    @{ n = 'Gajar Halwa';  d = 'Carrot + dry fruits'; p = 110 }
  )
}

foreach ($catName in $menu.Keys) {
  Write-Host ("`n=== CATEGORY: {0} ===" -f $catName) -ForegroundColor Cyan
  $cat = Api-Json POST '/api/categories' @{ businessId = $bizId; name = $catName } $token
  $catId = $cat.data.id
  foreach ($item in $menu[$catName]) {
    New-Product $token $bizId $catId $item.n $item.d $item.p
  }
}

Write-Host "`n=== DONE ===" -ForegroundColor Green
Write-Host ("Scan URL / Public page:  {0}/business/{1}" -f 'http://localhost:5173', $biz.slug)
Write-Host "Open that URL (or scan the QR from Dashboard - QR Code) to see the category-wise menu."
