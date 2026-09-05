$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    New-Item -ItemType Directory -Force .latex-build, build | Out-Null
    # Preserve auxiliary files between passes so contents and references resolve.
    for ($pass = 1; $pass -le 3; $pass++) {
        & xelatex -synctex=1 -interaction=nonstopmode -halt-on-error '-output-directory=.latex-build' report.tex
        if ($LASTEXITCODE -ne 0) { throw "XeLaTeX failed on pass $pass." }
    }
    Copy-Item -LiteralPath .latex-build/report.pdf -Destination build/report.pdf
    Copy-Item -LiteralPath .latex-build/report.synctex.gz -Destination build/report.synctex.gz
} finally {
    Pop-Location
}
