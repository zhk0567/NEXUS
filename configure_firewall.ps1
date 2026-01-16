# Windows Firewall Port Mapping Configuration Script
# Configure firewall to allow external access to port 5000

[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Windows Firewall Port Configuration" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Check administrator privileges
$isAdmin = ([Security.Principal.WindowsPrincipal] [Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $isAdmin) {
    Write-Host "Error: Administrator privileges required to configure firewall rules" -ForegroundColor Red
    Write-Host "Please run PowerShell as Administrator and execute this script again" -ForegroundColor Yellow
    exit 1
}

Write-Host "Configuring firewall rules..." -ForegroundColor Yellow

# Remove existing rule if present
$existingRule = Get-NetFirewallRule -DisplayName "NEXUS Server Port 5000" -ErrorAction SilentlyContinue
if ($existingRule) {
    Write-Host "Removing existing rule..." -ForegroundColor Yellow
    Remove-NetFirewallRule -DisplayName "NEXUS Server Port 5000"
}

# Create inbound rule - Allow TCP port 5000
try {
    New-NetFirewallRule -DisplayName "NEXUS Server Port 5000" `
        -Direction Inbound `
        -Protocol TCP `
        -LocalPort 5000 `
        -Action Allow `
        -Description "Allow external access to NEXUS server port 5000" `
        -Profile Any
    
    Write-Host "[OK] Inbound rule created successfully" -ForegroundColor Green
} catch {
    Write-Host "[FAIL] Failed to create inbound rule: $_" -ForegroundColor Red
    exit 1
}

# Create outbound rule (usually not needed, but for completeness)
try {
    $outboundRule = Get-NetFirewallRule -DisplayName "NEXUS Server Port 5000 Outbound" -ErrorAction SilentlyContinue
    if (-not $outboundRule) {
        New-NetFirewallRule -DisplayName "NEXUS Server Port 5000 Outbound" `
            -Direction Outbound `
            -Protocol TCP `
            -LocalPort 5000 `
            -Action Allow `
            -Description "NEXUS server outbound connection port 5000" `
            -Profile Any
        
        Write-Host "[OK] Outbound rule created successfully" -ForegroundColor Green
    }
} catch {
    Write-Host "[WARN] Failed to create outbound rule (may already exist): $_" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Firewall configuration completed!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Configuration Information:" -ForegroundColor Yellow
Write-Host "  Public IP: 94.74.95.80:5000" -ForegroundColor White
Write-Host "  Private IP: 192.168.0.239:5000" -ForegroundColor White
Write-Host "  Port: 5000 (TCP)" -ForegroundColor White
Write-Host ""
Write-Host "Next Steps:" -ForegroundColor Yellow
Write-Host "  1. Configure port mapping on router/firewall:" -ForegroundColor White
Write-Host "     Public IP: 94.74.95.80:5000 -> Private IP: 192.168.0.239:5000" -ForegroundColor White
Write-Host "  2. Ensure server firewall allows port 5000 (completed)" -ForegroundColor White
Write-Host "  3. Start server: python app.py" -ForegroundColor White
Write-Host ""

# Verify rule
Write-Host "Verifying firewall rule..." -ForegroundColor Yellow
$rule = Get-NetFirewallRule -DisplayName "NEXUS Server Port 5000" -ErrorAction SilentlyContinue
if ($rule) {
    Write-Host "[OK] Firewall rule verified successfully" -ForegroundColor Green
    Write-Host "  Rule Name: $($rule.DisplayName)" -ForegroundColor White
    Write-Host "  Status: $($rule.Enabled)" -ForegroundColor White
    Write-Host "  Direction: $($rule.Direction)" -ForegroundColor White
} else {
    Write-Host "[FAIL] Unable to verify firewall rule" -ForegroundColor Red
}
