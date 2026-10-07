#Requires -RunAsAdministrator
<#
  辅助脚本：重置本机 MySQL 的 root 密码
  ------------------------------------------------------------------
  【什么时候用】
    忘了本机 MySQL 的 root 密码，导致项目跑不起来 / 连不上库。
    本脚本用 MySQL 官方的 --init-file 机制把 root 密码改掉，**不动任何业务数据**。

  【怎么用】
    1) 右键 PowerShell →「以管理员身份运行」
    2) 按你的实际安装情况传参（不传就用下面的默认值，默认适合标准 MySQL 8.0 安装）：
         .\reset-mysql-root.ps1
         .\reset-mysql-root.ps1 -ServiceName MySQL80 `
             -MySqlHome 'C:\Program Files\MySQL\MySQL Server 8.0' `
             -IniPath    'C:\ProgramData\MySQL\MySQL Server 8.0\my.ini'
    3) 按提示输入新密码（至少 8 位，只用英文 / 数字 / 符号）

  【怎么查自己的三个参数】
    - 服务名：  Get-Service *mysql*
    - 安装目录：看服务属性里的「可执行文件的路径」，去掉末尾的 \bin\mysqld.exe
    - 配置文件：同一条路径里若带 --defaults-file=...，那一串就是 IniPath

  ⚠️ 改完密码记得同步到 src/main/resources/application-local.yml（该文件不进 Git）。
  ⚠️ 本脚本会【停止并重启 MySQL 服务】，请先确认没有正在跑的导入 / 备份任务。
#>
param(
    [string]$ServiceName = 'MySQL80',
    [string]$MySqlHome   = 'C:\Program Files\MySQL\MySQL Server 8.0',
    [string]$IniPath     = 'C:\ProgramData\MySQL\MySQL Server 8.0\my.ini'
)

$ErrorActionPreference = 'Stop'

$tmp = Join-Path $env:TEMP 'fh-reset-root.sql'

Write-Host ''
Write-Host '======================================================'
Write-Host '  重置 MySQL root 密码'
Write-Host "  服务名  : $ServiceName"
Write-Host "  安装目录: $MySqlHome"
Write-Host "  配置文件: $IniPath"
Write-Host '  本操作不会删除任何数据'
Write-Host '======================================================'
Write-Host ''

foreach ($p in @((Join-Path $MySqlHome 'bin\mysqld.exe'), $IniPath)) {
    if (-not (Test-Path $p)) {
        Write-Host "找不到：$p" -ForegroundColor Red
        Write-Host '请用 -ServiceName / -MySqlHome / -IniPath 传正确路径（见脚本头部说明）。' -ForegroundColor Yellow
        Read-Host '按回车退出'
        exit 1
    }
}

$p1 = Read-Host '请输入新的 root 密码（至少 8 位，请用英文/数字/符号，别用中文）'
$p2 = Read-Host '请再输入一遍确认'

if ($p1 -ne $p2) { Write-Host '两次输入不一致，已取消。' -ForegroundColor Red; Read-Host '按回车退出'; exit 1 }
if ($p1.Length -lt 8) { Write-Host '密码太短（至少 8 位），已取消。' -ForegroundColor Red; Read-Host '按回车退出'; exit 1 }
if ($p1 -notmatch '^[\x20-\x7E]+$') { Write-Host '密码含非英文/数字/符号字符，已取消。' -ForegroundColor Red; Read-Host '按回车退出'; exit 1 }

$esc = $p1.Replace('\','\\').Replace("'","\'")
Set-Content -Path $tmp -Value ("ALTER USER 'root'@'localhost' IDENTIFIED BY '" + $esc + "';") -Encoding ASCII

Write-Host ''
Write-Host '[1/4] 停止 MySQL 服务 ...' -ForegroundColor Yellow
Stop-Service -Name $ServiceName -Force
Start-Sleep -Seconds 2

Write-Host '[2/4] 临时启动 MySQL 并写入新密码（约 15 秒，请稍等）...' -ForegroundColor Yellow
$mysqld = Join-Path $MySqlHome 'bin\mysqld.exe'
$proc = Start-Process -FilePath $mysqld -ArgumentList @("--defaults-file=$IniPath", "--init-file=$tmp", '--console') -PassThru -WindowStyle Hidden
Start-Sleep -Seconds 15

Write-Host '[3/4] 关闭临时实例 ...' -ForegroundColor Yellow
$admin = Join-Path $MySqlHome 'bin\mysqladmin.exe'
& $admin -u root ("--password=" + $p1) shutdown 2>$null
Start-Sleep -Seconds 3
if (-not $proc.HasExited) { Stop-Process -Id $proc.Id -Force; Start-Sleep -Seconds 2 }

Write-Host '[4/4] 重新启动 MySQL 服务并验证 ...' -ForegroundColor Yellow
Start-Service -Name $ServiceName
Start-Sleep -Seconds 5
Remove-Item $tmp -Force -ErrorAction SilentlyContinue

$mysql = Join-Path $MySqlHome 'bin\mysql.exe'
$out = & $mysql -u root ("--password=" + $p1) -e "SELECT VERSION() AS version;" 2>&1
if ($LASTEXITCODE -eq 0) {
    Write-Host ''
    Write-Host '重置成功！新密码已经生效。' -ForegroundColor Green
    $out | ForEach-Object { Write-Host ('    ' + $_) }
    Write-Host ''
    Write-Host '请把新密码记下来 —— 接下来要填到 src/main/resources/application-local.yml。' -ForegroundColor Green
} else {
    Write-Host ''
    Write-Host '验证失败，输出如下：' -ForegroundColor Red
    $out | ForEach-Object { Write-Host ('    ' + $_) }
}
Write-Host ''
Read-Host '按回车退出'
