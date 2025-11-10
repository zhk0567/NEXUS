# Git Auto Install Script
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8

Write-Host "Starting Git installation..." -ForegroundColor Green

# Git download URL
$gitUrl = "https://github.com/git-for-windows/git/releases/download/v2.43.0.windows.1/Git-2.43.0-64-bit.exe"
$installerPath = "$env:TEMP\GitInstaller.exe"

try {
    # Download Git installer
    Write-Host "Downloading Git installer..." -ForegroundColor Yellow
    Invoke-WebRequest -Uri $gitUrl -OutFile $installerPath -UseBasicParsing
    
    if (Test-Path $installerPath) {
        Write-Host "Download complete, starting installation..." -ForegroundColor Yellow
        
        # Silent install Git
        $installArgs = @(
            "/SILENT",
            "/NORESTART",
            "/COMPONENTS=icons,ext\shellhere,assoc,assoc_sh",
            "/DIR=C:\Program Files\Git"
        )
        
        $process = Start-Process -FilePath $installerPath -ArgumentList $installArgs -Wait -PassThru
        
        if ($process.ExitCode -eq 0) {
            Write-Host "Git installed successfully!" -ForegroundColor Green
            
            # Add to PATH
            $gitPath = "C:\Program Files\Git\cmd"
            $currentPath = [Environment]::GetEnvironmentVariable("Path", "Machine")
            
            if ($currentPath -notlike "*$gitPath*") {
                [Environment]::SetEnvironmentVariable("Path", "$currentPath;$gitPath", "Machine")
                Write-Host "Git added to system PATH" -ForegroundColor Green
            }
            
            # Configure Git for UTF-8
            Write-Host "Configuring Git for UTF-8..." -ForegroundColor Yellow
            $env:Path = "$gitPath;$env:Path"
            
            Start-Sleep -Seconds 3
            
            # Configure Git global settings
            & "C:\Program Files\Git\cmd\git.exe" config --global core.quotepath false
            & "C:\Program Files\Git\cmd\git.exe" config --global i18n.commitencoding utf-8
            & "C:\Program Files\Git\cmd\git.exe" config --global i18n.logoutputencoding utf-8
            & "C:\Program Files\Git\cmd\git.exe" config --global core.autocrlf false
            
            Write-Host "Git configuration complete!" -ForegroundColor Green
            Write-Host "Please restart PowerShell for Git commands to take effect" -ForegroundColor Yellow
            
        } else {
            Write-Host "Git installation failed, exit code: $($process.ExitCode)" -ForegroundColor Red
            exit 1
        }
        
        # Cleanup
        Remove-Item $installerPath -Force -ErrorAction SilentlyContinue
        
    } else {
        Write-Host "Download failed, installer not found" -ForegroundColor Red
        exit 1
    }
    
} catch {
    Write-Host "Error during installation: $_" -ForegroundColor Red
    exit 1
}
