# Run this in PowerShell while the app is running (port 8081).
# Fresh run only: creating set 5-9 a second time gives a CONFLICT, which is expected.
$base = "http://localhost:8081/api"

function Post($path, $body) {
    Invoke-RestMethod -Uri "$base$path" -Method Post -ContentType "application/json" -Body ($body | ConvertTo-Json -Depth 6)
}

Write-Host "1. master set 5-9"
$set = Post "/set-types" @{ sizes = @(5,6,7,8,9) }
$set

Write-Host "2. article Ruhi-7"
$art = Post "/articles" @{ name="Ruhi-7"; articleNumber="R7"; companyName="Test Co"; color="Black"; cp=300; sp=350; setTypeIds=@($set.id) }
$art

Write-Host "3. purchase 5:6 6:6 7:5 8:6 9:5"
Post "/purchases" @{ articleId=$art.id; cp=300; items=@(
    @{size=5;quantity=6}, @{size=6;quantity=6}, @{size=7;quantity=5}, @{size=8;quantity=6}, @{size=9;quantity=5}) }

Write-Host "4. availability (expect 5 sets, loose 5:1 6:1 8:1)"
Invoke-RestMethod "$base/articles/$($art.id)/availability" | ConvertTo-Json -Depth 6

Write-Host "5. customer"
$cust = Post "/customers" @{ name="Test Retailer"; phone="9999999999"; address="Ludhiana" }
$cust

Write-Host "6. invoice: 7 sets asked (only 5 available) + loose size 5 x1 + loose size 8 x1"
$inv = Post "/invoices" @{ customerId=$cust.id; items=@(
    @{articleId=$art.id; type="SET"; setTypeId=$set.id; quantity=7},
    @{articleId=$art.id; type="LOOSE"; size=5; quantity=1},
    @{articleId=$art.id; type="LOOSE"; size=8; quantity=1}) }
$inv | ConvertTo-Json -Depth 6
Write-Host "expected: set rate 350 (25 pieces <= 32), loose rate 355, total 9460"

Write-Host "7. payment 5000"
Post "/payments" @{ customerId=$cust.id; amount=5000; note="cash" }

Write-Host "8. balance (expect outstanding 4460)"
Invoke-RestMethod "$base/customers/$($cust.id)/balance"

Write-Host "9. availability after sale (expect 0 sets, loose 6:1)"
Invoke-RestMethod "$base/articles/$($art.id)/availability" | ConvertTo-Json -Depth 6

Write-Host "10. report for today"
$today = (Get-Date).ToString("yyyy-MM-dd")
Invoke-RestMethod "$base/reports/summary?from=$today&to=$today"
