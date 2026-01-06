$env:BISECT_PASS = [System.Environment]::GetEnvironmentVariable('BISECT_PASS', 'User')
Set-Location 'C:\Users\henry\Projects\Arcane Sigils'
python deploy.py @args
