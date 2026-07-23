#Requires -Version 5.1

[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$EvidenceDirectory,

    [Parameter(Mandatory = $true)]
    [string]$ProbeRoot,

    [Parameter(Mandatory = $true)]
    [string]$NonElevatedProbeBundle
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$Utf8NoBom = New-Object System.Text.UTF8Encoding($false)
$RepositoryRoot = [System.IO.Path]::GetFullPath(
    (Join-Path $PSScriptRoot "..\..\..")
)
$CurrentRoot = [System.IO.Path]::GetFullPath((Get-Location).Path)
if (-not $CurrentRoot.Equals(
        $RepositoryRoot,
        [System.StringComparison]::OrdinalIgnoreCase
    )) {
    throw "Run this collector from the repository root."
}

$LocalAppData = [System.IO.Path]::GetFullPath(
    [Environment]::GetFolderPath("LocalApplicationData")
)
$AllowedEvidenceRoot = Join-Path $LocalAppData "MediaEcosystem\Evidence"
$AllowedProbeRoot = Join-Path $LocalAppData "MediaEcosystem\ProbeRoots"
$AllowedNonElevatedBundleRoot = Join-Path (
    [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath())
) "MediaEcosystem\NonElevatedEvidence"

function Assert-ChildPath {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Candidate,

        [Parameter(Mandatory = $true)]
        [string]$AllowedParent,

        [Parameter(Mandatory = $true)]
        [string]$Description
    )

    $FullCandidate = [System.IO.Path]::GetFullPath($Candidate)
    $FullParent = [System.IO.Path]::GetFullPath($AllowedParent).TrimEnd(
        [System.IO.Path]::DirectorySeparatorChar
    )
    $Prefix = $FullParent + [System.IO.Path]::DirectorySeparatorChar
    if (-not $FullCandidate.StartsWith(
            $Prefix,
            [System.StringComparison]::OrdinalIgnoreCase
        )) {
        throw "$Description must be a child of its dedicated Local AppData root."
    }
    return $FullCandidate
}

$EvidenceDirectory = Assert-ChildPath `
    -Candidate $EvidenceDirectory `
    -AllowedParent $AllowedEvidenceRoot `
    -Description "Evidence directory"
$ProbeRoot = Assert-ChildPath `
    -Candidate $ProbeRoot `
    -AllowedParent $AllowedProbeRoot `
    -Description "Probe root"
$NonElevatedProbeBundle = Assert-ChildPath `
    -Candidate $NonElevatedProbeBundle `
    -AllowedParent $AllowedNonElevatedBundleRoot `
    -Description "Non-elevated probe bundle"

New-Item -ItemType Directory -Path $AllowedEvidenceRoot -Force | Out-Null
New-Item -ItemType Directory -Path $AllowedProbeRoot -Force | Out-Null
if (Test-Path -LiteralPath $EvidenceDirectory) {
    throw "Refusing to overwrite an existing evidence directory."
}
New-Item -ItemType Directory -Path $EvidenceDirectory | Out-Null
if (-not (Test-Path -LiteralPath $ProbeRoot -PathType Container)) {
    New-Item -ItemType Directory -Path $ProbeRoot | Out-Null
}
if (-not (
        Test-Path -LiteralPath $NonElevatedProbeBundle -PathType Container
    )) {
    throw "The non-elevated probe bundle does not exist."
}

$NonElevatedManifest = Join-Path `
    $NonElevatedProbeBundle `
    "manifest.sha256"
if (-not (Test-Path -LiteralPath $NonElevatedManifest -PathType Leaf)) {
    throw "The non-elevated probe bundle has no manifest."
}
$NonElevatedListed = [System.Collections.Generic.HashSet[string]]::new(
    [System.StringComparer]::Ordinal
)
foreach ($Line in [System.IO.File]::ReadAllLines($NonElevatedManifest)) {
    if ($Line -notmatch '^([0-9a-f]{64})  (.+)$') {
        throw "Malformed non-elevated probe manifest line."
    }
    $ExpectedHash = $Matches[1]
    $Relative = $Matches[2]
    if (-not $NonElevatedListed.Add($Relative)) {
        throw "Duplicate non-elevated probe manifest path."
    }
    $Candidate = Join-Path `
        $NonElevatedProbeBundle `
        $Relative.Replace('/', '\')
    $ActualHash = (
        Get-FileHash -LiteralPath $Candidate -Algorithm SHA256
    ).Hash.ToLowerInvariant()
    if ($ActualHash -ne $ExpectedHash) {
        throw "Non-elevated probe manifest verification failed."
    }
}
$NonElevatedFiles = @(
    Get-ChildItem -LiteralPath $NonElevatedProbeBundle -Recurse -File |
        Where-Object { $_.FullName -ne $NonElevatedManifest }
)
if ($NonElevatedFiles.Count -ne $NonElevatedListed.Count) {
    throw "Non-elevated probe manifest coverage failed."
}
$NonElevatedManifestHash = (
    Get-FileHash -LiteralPath $NonElevatedManifest -Algorithm SHA256
).Hash.ToLowerInvariant()
$NonElevatedDestination = Join-Path `
    $EvidenceDirectory `
    "non-elevated-storage"
New-Item -ItemType Directory -Path $NonElevatedDestination | Out-Null
Copy-Item `
    -Path (Join-Path $NonElevatedProbeBundle "*") `
    -Destination $NonElevatedDestination `
    -Recurse

# This process-local override keeps every Python temporary fixture under the
# dedicated probe root. It does not alter user or system configuration.
$env:TEMP = $ProbeRoot
$env:TMP = $ProbeRoot

function Write-Utf8 {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path,

        [AllowEmptyString()]
        [Parameter(Mandatory = $true)]
        [string]$Value
    )

    [System.IO.File]::WriteAllText($Path, $Value, $Utf8NoBom)
}

function Write-Json {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path,

        [Parameter(Mandatory = $true)]
        [object]$Value
    )

    $Json = $Value | ConvertTo-Json -Depth 12
    Write-Utf8 -Path $Path -Value ($Json + "`n")
}

function ConvertTo-ProcessArgument {
    param(
        [AllowEmptyString()]
        [Parameter(Mandatory = $true)]
        [string]$Value
    )

    if ($Value.Length -gt 0 -and $Value -notmatch '[\s"]') {
        return $Value
    }
    return '"' + $Value.Replace('"', '\"') + '"'
}

$CommandRecords = New-Object System.Collections.Generic.List[object]

function Invoke-CapturedCommand {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Name,

        [Parameter(Mandatory = $true)]
        [string]$FilePath,

        [Parameter(Mandatory = $true)]
        [string[]]$Arguments,

        [int[]]$ExpectedExitCodes = @(0)
    )

    $StdoutName = "$Name.stdout.txt"
    $StderrName = "$Name.stderr.txt"
    $MetadataName = "$Name.command.json"
    $StdoutPath = Join-Path $EvidenceDirectory $StdoutName
    $StderrPath = Join-Path $EvidenceDirectory $StderrName
    $MetadataPath = Join-Path $EvidenceDirectory $MetadataName

    $StartUtc = [DateTime]::UtcNow
    $Stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
    $StartInfo = New-Object System.Diagnostics.ProcessStartInfo
    $StartInfo.FileName = $FilePath
    $StartInfo.Arguments = (
        $Arguments | ForEach-Object { ConvertTo-ProcessArgument -Value $_ }
    ) -join " "
    $StartInfo.WorkingDirectory = $RepositoryRoot
    $StartInfo.UseShellExecute = $false
    $StartInfo.CreateNoWindow = $true
    $StartInfo.RedirectStandardOutput = $true
    $StartInfo.RedirectStandardError = $true
    $StartInfo.StandardOutputEncoding = $Utf8NoBom
    $StartInfo.StandardErrorEncoding = $Utf8NoBom

    $Process = New-Object System.Diagnostics.Process
    $Process.StartInfo = $StartInfo
    if (-not $Process.Start()) {
        throw "Failed to start captured command $Name."
    }
    $StdoutTask = $Process.StandardOutput.ReadToEndAsync()
    $StderrTask = $Process.StandardError.ReadToEndAsync()
    $Process.WaitForExit()
    $Stdout = $StdoutTask.GetAwaiter().GetResult()
    $Stderr = $StderrTask.GetAwaiter().GetResult()
    $Stopwatch.Stop()

    Write-Utf8 -Path $StdoutPath -Value $Stdout
    Write-Utf8 -Path $StderrPath -Value $Stderr
    $Record = [ordered]@{
        name = $Name
        executable = $FilePath
        arguments = @($Arguments)
        working_directory = $RepositoryRoot
        started_at_utc = $StartUtc.ToString("o")
        duration_seconds = [Math]::Round($Stopwatch.Elapsed.TotalSeconds, 6)
        exit_code = $Process.ExitCode
        expected_exit_codes = @($ExpectedExitCodes)
        stdout_file = $StdoutName
        stderr_file = $StderrName
    }
    Write-Json -Path $MetadataPath -Value $Record
    $CommandRecords.Add([pscustomobject]$Record)
    if ($ExpectedExitCodes -notcontains $Process.ExitCode) {
        throw "Captured command $Name exited $($Process.ExitCode)."
    }
}

function Get-ErrorObservation {
    param(
        [Parameter(Mandatory = $true)]
        [System.Management.Automation.ErrorRecord]$ErrorRecord
    )

    return [ordered]@{
        available = $false
        error_type = $ErrorRecord.Exception.GetType().FullName
        hresult = $ErrorRecord.Exception.HResult
    }
}

function Get-VolumeObservation {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path
    )

    try {
        $Root = [System.IO.Path]::GetPathRoot(
            [System.IO.Path]::GetFullPath($Path)
        )
        $DriveLetter = $Root.Substring(0, 1)
        $Volume = Get-Volume -DriveLetter $DriveLetter -ErrorAction Stop
        return [ordered]@{
            available = $true
            drive_letter = $DriveLetter
            filesystem = $Volume.FileSystem
            capacity_bytes = [int64]$Volume.Size
            available_bytes = [int64]$Volume.SizeRemaining
        }
    }
    catch {
        try {
            $Drive = New-Object System.IO.DriveInfo(
                [System.IO.Path]::GetPathRoot(
                    [System.IO.Path]::GetFullPath($Path)
                )
            )
            return [ordered]@{
                available = $true
                drive_letter = $Drive.Name.Substring(0, 1)
                filesystem = $Drive.DriveFormat
                capacity_bytes = [int64]$Drive.TotalSize
                available_bytes = [int64]$Drive.AvailableFreeSpace
            }
        }
        catch {
            return Get-ErrorObservation -ErrorRecord $_
        }
    }
}

function Get-PowerObservation {
    $Observation = [ordered]@{}
    try {
        Add-Type -AssemblyName System.Windows.Forms -ErrorAction Stop
        $Status = [System.Windows.Forms.SystemInformation]::PowerStatus
        $Observation.system_power_status = [ordered]@{
            available = $true
            power_line_status = $Status.PowerLineStatus.ToString()
            battery_charge_status = $Status.BatteryChargeStatus.ToString()
            battery_life_percent = if ($Status.BatteryLifePercent -ge 0) {
                [Math]::Round($Status.BatteryLifePercent * 100, 3)
            } else {
                $null
            }
            battery_life_remaining_seconds = $Status.BatteryLifeRemaining
            battery_full_lifetime_seconds = $Status.BatteryFullLifetime
        }
    }
    catch {
        $Observation.system_power_status = Get-ErrorObservation -ErrorRecord $_
    }

    try {
        $Batteries = @(
            Get-CimInstance -ClassName Win32_Battery -ErrorAction Stop |
                ForEach-Object {
                    [ordered]@{
                        battery_status = $_.BatteryStatus
                        estimated_charge_remaining_percent =
                            $_.EstimatedChargeRemaining
                        estimated_run_time_minutes = $_.EstimatedRunTime
                        status = $_.Status
                        availability = $_.Availability
                        config_manager_error_code = $_.ConfigManagerErrorCode
                        design_voltage_millivolts = $_.DesignVoltage
                    }
                }
        )
        $Observation.win32_battery = [ordered]@{
            available = $Batteries.Count -gt 0
            batteries = $Batteries
            reason = if ($Batteries.Count -eq 0) {
                "Win32_Battery returned no records"
            } else {
                $null
            }
        }
    }
    catch {
        $Observation.win32_battery = Get-ErrorObservation -ErrorRecord $_
    }

    try {
        $Statuses = @(
            Get-CimInstance `
                -Namespace "root\wmi" `
                -ClassName BatteryStatus `
                -ErrorAction Stop |
                ForEach-Object {
                    [ordered]@{
                        active = $_.Active
                        power_online = $_.PowerOnline
                        discharging = $_.Discharging
                        charging = $_.Charging
                        critical = $_.Critical
                        voltage_millivolts = $_.Voltage
                        rate_milliwatts = $_.Rate
                        remaining_capacity_milliwatt_hours =
                            $_.RemainingCapacity
                    }
                }
        )
        $Observation.wmi_battery_status = [ordered]@{
            available = $Statuses.Count -gt 0
            batteries = $Statuses
            reason = if ($Statuses.Count -eq 0) {
                "BatteryStatus returned no records"
            } else {
                $null
            }
        }
    }
    catch {
        $Observation.wmi_battery_status = Get-ErrorObservation -ErrorRecord $_
    }
    return $Observation
}

function Get-ThermalObservation {
    $Observation = [ordered]@{}
    try {
        $Zones = @(
            Get-CimInstance `
                -Namespace "root\wmi" `
                -ClassName MSAcpi_ThermalZoneTemperature `
                -ErrorAction Stop |
                ForEach-Object {
                    [ordered]@{
                        current_temperature_tenths_kelvin =
                            $_.CurrentTemperature
                        critical_trip_point_tenths_kelvin =
                            $_.CriticalTripPoint
                        passive_trip_point_tenths_kelvin =
                            $_.PassiveTripPoint
                        thermal_stamp = $_.ThermalStamp
                    }
                }
        )
        $Observation.acpi_thermal_zones = [ordered]@{
            available = $Zones.Count -gt 0
            zones = $Zones
            reason = if ($Zones.Count -eq 0) {
                "MSAcpi_ThermalZoneTemperature returned no records"
            } else {
                $null
            }
        }
    }
    catch {
        $Observation.acpi_thermal_zones =
            Get-ErrorObservation -ErrorRecord $_
    }

    try {
        $Temperatures = @(
            Get-CimInstance `
                -Namespace "root\wmi" `
                -ClassName BatteryTemperature `
                -ErrorAction Stop |
                ForEach-Object {
                    [ordered]@{
                        temperature_tenths_kelvin = $_.Temperature
                    }
                }
        )
        $Observation.battery_temperature = [ordered]@{
            available = $Temperatures.Count -gt 0
            batteries = $Temperatures
            reason = if ($Temperatures.Count -eq 0) {
                "BatteryTemperature returned no records"
            } else {
                $null
            }
        }
    }
    catch {
        $Observation.battery_temperature =
            Get-ErrorObservation -ErrorRecord $_
    }
    return $Observation
}

function Get-EnvironmentObservation {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Stage
    )

    $ComputerSystem = Get-CimInstance `
        -ClassName Win32_ComputerSystem `
        -ErrorAction Stop
    $OperatingSystem = Get-CimInstance `
        -ClassName Win32_OperatingSystem `
        -ErrorAction Stop
    $CurrentVersion = Get-ItemProperty `
        "HKLM:\SOFTWARE\Microsoft\Windows NT\CurrentVersion"
    $Identity = [Security.Principal.WindowsIdentity]::GetCurrent()
    $Principal = New-Object Security.Principal.WindowsPrincipal($Identity)
    try {
        $ProcessArchitecture =
            [System.Runtime.InteropServices.RuntimeInformation]::
                ProcessArchitecture.ToString()
    }
    catch {
        $ProcessArchitecture = if ([Environment]::Is64BitProcess) {
            "64-bit"
        } else {
            "32-bit"
        }
    }

    return [ordered]@{
        schema_version = 1
        stage = $Stage
        captured_at_utc = [DateTime]::UtcNow.ToString("o")
        device = [ordered]@{
            manufacturer = $ComputerSystem.Manufacturer
            model = $ComputerSystem.Model
        }
        windows = [ordered]@{
            caption = $OperatingSystem.Caption
            product_name = $CurrentVersion.ProductName
            edition_id = $CurrentVersion.EditionID
            display_version = $CurrentVersion.DisplayVersion
            version = $OperatingSystem.Version
            build_number = $OperatingSystem.BuildNumber
            update_build_revision = $CurrentVersion.UBR
            installation_type = $CurrentVersion.InstallationType
            os_architecture = $OperatingSystem.OSArchitecture
        }
        process = [ordered]@{
            architecture = $ProcessArchitecture
            is_64_bit = [Environment]::Is64BitProcess
            elevated = $Principal.IsInRole(
                [Security.Principal.WindowsBuiltInRole]::Administrator
            )
        }
        powershell = [ordered]@{
            edition = $PSVersionTable.PSEdition
            version = $PSVersionTable.PSVersion.ToString()
            clr_version = $PSVersionTable.CLRVersion.ToString()
        }
        locale = [ordered]@{
            culture = [Globalization.CultureInfo]::CurrentCulture.Name
            ui_culture = [Globalization.CultureInfo]::CurrentUICulture.Name
            installed_ui_culture =
                [Globalization.CultureInfo]::InstalledUICulture.Name
        }
        system_drive = Get-VolumeObservation -Path $env:SystemDrive
        probe_volume = Get-VolumeObservation -Path $ProbeRoot
        power = Get-PowerObservation
        thermal = Get-ThermalObservation
    }
}

$EnvironmentBefore = Get-EnvironmentObservation -Stage "before"
Write-Json `
    -Path (Join-Path $EvidenceDirectory "environment-before.json") `
    -Value $EnvironmentBefore

$PyCommand = Get-Command py -ErrorAction Stop
$PyPath = $PyCommand.Source
$PythonVersion = (
    & $PyPath -3 -c "import platform; print(platform.python_version())"
).Trim()
$PythonRuntime = "Python $PythonVersion"
$PythonLauncherVersion = (
    Get-Item -LiteralPath $PyPath
).VersionInfo.FileVersion
Write-Json `
    -Path (Join-Path $EvidenceDirectory "python-launcher.json") `
    -Value ([ordered]@{
        executable = $PyPath
        file_version = $PythonLauncherVersion
        selected_python_runtime = $PythonRuntime
    })

$OsLabel = (
    "$($EnvironmentBefore.windows.caption) " +
    "$($EnvironmentBefore.windows.display_version) " +
    "build $($EnvironmentBefore.windows.build_number)." +
    "$($EnvironmentBefore.windows.update_build_revision)"
)
$ArchitectureLabel = (
    "$($EnvironmentBefore.windows.os_architecture) OS; " +
    "$($EnvironmentBefore.process.architecture) process"
)
$Harness = "spikes/shared-core-foundations/scripts/harness.py"

Invoke-CapturedCommand `
    -Name "01-git-head" `
    -FilePath (Get-Command git -ErrorAction Stop).Source `
    -Arguments @("rev-parse", "HEAD")
Invoke-CapturedCommand `
    -Name "02-git-status" `
    -FilePath (Get-Command git -ErrorAction Stop).Source `
    -Arguments @("status", "--short", "--branch")
Invoke-CapturedCommand `
    -Name "03-python-launcher-inventory" `
    -FilePath $PyPath `
    -Arguments @("-0p")
Invoke-CapturedCommand `
    -Name "04-python-environment" `
    -FilePath $PyPath `
    -Arguments @(
        "-3",
        "-c",
        (
            "import json,locale,platform,sys,tempfile;" +
            "print(json.dumps({" +
            "'exact_version':sys.version," +
            "'implementation':platform.python_implementation()," +
            "'architecture':platform.machine()," +
            "'filesystem_encoding':sys.getfilesystemencoding()," +
            "'locale_encoding':locale.getencoding()," +
            "'temporary_directory':tempfile.gettempdir()" +
            "},sort_keys=True))"
        )
    )
Invoke-CapturedCommand `
    -Name "05-shared-core-verify" `
    -FilePath $PyPath `
    -Arguments @(
        "-3",
        $Harness,
        "verify",
        "--seeds",
        "7,20260723"
    )
Invoke-CapturedCommand `
    -Name "06-events-seed-7" `
    -FilePath $PyPath `
    -Arguments @(
        "-3",
        $Harness,
        "events",
        "--seed",
        "7",
        "--iterations",
        "100"
    )
Invoke-CapturedCommand `
    -Name "07-events-seed-20260723" `
    -FilePath $PyPath `
    -Arguments @(
        "-3",
        $Harness,
        "events",
        "--seed",
        "20260723",
        "--iterations",
        "100"
    )
Invoke-CapturedCommand `
    -Name "08-windows-storage-probe" `
    -FilePath $PyPath `
    -Arguments @(
        "-3",
        $Harness,
        "storage-probe",
        "--target-root",
        $ProbeRoot,
        "--storage-context",
        "windows-internal",
        "--target-label",
        "windows-internal-volume"
    )
Invoke-CapturedCommand `
    -Name "09-hash-cancellation" `
    -FilePath $PyPath `
    -Arguments @(
        "-3",
        $Harness,
        "hash-cancellation",
        "--work-root",
        $ProbeRoot,
        "--size-mib",
        "64",
        "--chunk-mib",
        "1",
        "--cancel-after-seconds",
        "0.5"
    )
Invoke-CapturedCommand `
    -Name "10-hash-benchmark" `
    -FilePath $PyPath `
    -Arguments @(
        "-3",
        $Harness,
        "hash-benchmark",
        "--sizes-mib",
        "1,16,64,256",
        "--repeats",
        "3",
        "--chunk-mib",
        "1",
        "--device-label",
        "windows-surface-book-3",
        "--platform",
        "windows",
        "--os-label",
        $OsLabel,
        "--architecture",
        $ArchitectureLabel,
        "--runtime-label",
        $PythonRuntime,
        "--battery-power-observation",
        "captured in the raw before/after environment observations",
        "--thermal-observation",
        "captured in the raw before/after environment observations",
        "--cancellation-observation",
        "separate automated cancellation proof passed before this benchmark",
        "--json-output",
        (Join-Path $EvidenceDirectory "windows-internal-sha256.raw.json"),
        "--markdown-output",
        (Join-Path $EvidenceDirectory "windows-internal-sha256.raw.md")
    )

$EnvironmentAfter = Get-EnvironmentObservation -Stage "after"
Write-Json `
    -Path (Join-Path $EvidenceDirectory "environment-after.json") `
    -Value $EnvironmentAfter

Write-Json `
    -Path (Join-Path $EvidenceDirectory "command-index.json") `
    -Value ([ordered]@{
        schema_version = 1
        commands = $CommandRecords.ToArray()
        non_elevated_storage_bundle = [ordered]@{
            relative_root = "non-elevated-storage"
            manifest_sha256 = $NonElevatedManifestHash
            command_count = @(
                Get-ChildItem `
                    -LiteralPath $NonElevatedDestination `
                    -Filter "*.command.json" `
                    -File
            ).Count
        }
    })

$ResultNames = @(
    "08-windows-storage-probe.stdout.txt",
    "09-hash-cancellation.stdout.txt",
    "windows-internal-sha256.raw.json",
    "windows-internal-sha256.raw.md",
    "environment-before.json",
    "environment-after.json"
)
$ResultChecksums = foreach ($Name in $ResultNames) {
    $Path = Join-Path $EvidenceDirectory $Name
    [ordered]@{
        path = $Name
        sha256 = (
            Get-FileHash -LiteralPath $Path -Algorithm SHA256
        ).Hash.ToLowerInvariant()
    }
}
Write-Json `
    -Path (Join-Path $EvidenceDirectory "result-checksums.json") `
    -Value ([ordered]@{
        schema_version = 1
        algorithm = "sha256"
        results = @($ResultChecksums)
    })

$ManifestPath = Join-Path $EvidenceDirectory "manifest.sha256"
$ManifestLines = foreach ($File in (
        Get-ChildItem -LiteralPath $EvidenceDirectory -Recurse -File |
            Where-Object { $_.FullName -ne $ManifestPath } |
            Sort-Object FullName
    )) {
    $Relative = $File.FullName.Substring(
        $EvidenceDirectory.Length
    ).TrimStart('\').Replace('\', '/')
    $Hash = (
        Get-FileHash -LiteralPath $File.FullName -Algorithm SHA256
    ).Hash.ToLowerInvariant()
    "$Hash  $Relative"
}
Write-Utf8 -Path $ManifestPath -Value (($ManifestLines -join "`n") + "`n")

$Listed = [System.Collections.Generic.HashSet[string]]::new(
    [System.StringComparer]::Ordinal
)
foreach ($Line in [System.IO.File]::ReadAllLines($ManifestPath)) {
    if ($Line -notmatch '^([0-9a-f]{64})  (.+)$') {
        throw "Malformed evidence manifest line."
    }
    $ExpectedHash = $Matches[1]
    $Relative = $Matches[2]
    if (-not $Listed.Add($Relative)) {
        throw "Duplicate evidence manifest path."
    }
    $Candidate = Join-Path $EvidenceDirectory $Relative.Replace('/', '\')
    $ActualHash = (
        Get-FileHash -LiteralPath $Candidate -Algorithm SHA256
    ).Hash.ToLowerInvariant()
    if ($ActualHash -ne $ExpectedHash) {
        throw "Evidence manifest verification failed."
    }
}
$RawFiles = @(
    Get-ChildItem -LiteralPath $EvidenceDirectory -Recurse -File |
        Where-Object { $_.FullName -ne $ManifestPath }
)
if ($Listed.Count -ne $RawFiles.Count) {
    throw "Evidence manifest does not cover every raw evidence file."
}

$ManifestHash = (
    Get-FileHash -LiteralPath $ManifestPath -Algorithm SHA256
).Hash.ToLowerInvariant()
[ordered]@{
    result = "passed"
    evidence_directory = $EvidenceDirectory
    raw_file_count = $RawFiles.Count
    command_count = $CommandRecords.Count + @(
        Get-ChildItem `
            -LiteralPath $NonElevatedDestination `
            -Filter "*.command.json" `
            -File
    ).Count
    manifest = "manifest.sha256"
    manifest_sha256 = $ManifestHash
} | ConvertTo-Json -Depth 4
