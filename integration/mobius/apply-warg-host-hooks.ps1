param(
	[string] $WargSource = (Get-Location).Path
)

$ErrorActionPreference = "Stop"

$wargRoot = (Resolve-Path $WargSource).Path
$patchPath = Join-Path $PSScriptRoot "patches\warg-host-hooks.patch"
if (-not (Test-Path $patchPath))
{
	throw "Missing patch file: $patchPath"
}

$oldCeiling = $env:GIT_CEILING_DIRECTORIES
try
{
	# Keep git apply rooted at the requested Warg tree even when the lab folder
	# lives under another unrelated Git repository.
	$env:GIT_CEILING_DIRECTORIES = (Resolve-Path (Join-Path $wargRoot "..")).Path
	git -C $wargRoot apply --check $patchPath
	git -C $wargRoot apply $patchPath
}
finally
{
	if ($null -eq $oldCeiling)
	{
		Remove-Item Env:GIT_CEILING_DIRECTORIES -ErrorAction SilentlyContinue
	}
	else
	{
		$env:GIT_CEILING_DIRECTORIES = $oldCeiling
	}
}

Write-Host "Applied Warg host hooks to $wargRoot"
