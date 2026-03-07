param(
  [string]$SourceRoot = "docs/Myscript/SDK"
)

$ErrorActionPreference = "Stop"

$repoRoot = (Get-Location).Path
$sourcePath = Join-Path $repoRoot $SourceRoot
$aiRoot = Join-Path $sourcePath "_ai"
$textRoot = Join-Path $aiRoot "text"

if (-not (Test-Path -LiteralPath $sourcePath)) {
  throw "Source root not found: $sourcePath"
}

if (Test-Path -LiteralPath $aiRoot) {
  Remove-Item -LiteralPath $aiRoot -Recurse -Force
}

[System.IO.Directory]::CreateDirectory($textRoot) | Out-Null

function Decode-Html {
  param([string]$Value)
  return [System.Net.WebUtility]::HtmlDecode($Value)
}

function Clean-Text {
  param([string]$Value)

  if ([string]::IsNullOrWhiteSpace($Value)) {
    return ""
  }

  $normalized = $Value -replace "`r", ""
  $normalized = $normalized -replace "[`t ]+", " "
  $normalized = $normalized -replace " *`n *", "`n"
  $normalized = $normalized -replace "`n{3,}", "`n`n"
  return $normalized.Trim()
}

function Strip-Html {
  param([string]$Value)

  if ([string]::IsNullOrWhiteSpace($Value)) {
    return ""
  }

  $stripped = [regex]::Replace(
    $Value,
    "<[^>]+>",
    " ",
    [System.Text.RegularExpressions.RegexOptions]::IgnoreCase
  )
  return Clean-Text (Decode-Html $stripped)
}

function Get-MatchValue {
  param(
    [string]$Content,
    [string]$Pattern
  )

  $match = [regex]::Match(
    $Content,
    $Pattern,
    [System.Text.RegularExpressions.RegexOptions]::IgnoreCase `
      -bor [System.Text.RegularExpressions.RegexOptions]::Singleline
  )
  if ($match.Success) {
    return $match.Groups[1].Value
  }

  return ""
}

function Convert-HtmlToText {
  param(
    [string]$Html,
    [string]$RelativePath
  )

  $main = Get-MatchValue $Html "<main\b[^>]*>(.*?)</main>"
  if ([string]::IsNullOrWhiteSpace($main)) {
    $main = $Html
  }

  $text = $main
  $text = [regex]::Replace(
    $text,
    "<script\b[^>]*>.*?</script>",
    "",
    [System.Text.RegularExpressions.RegexOptions]::IgnoreCase `
      -bor [System.Text.RegularExpressions.RegexOptions]::Singleline
  )
  $text = [regex]::Replace(
    $text,
    "<style\b[^>]*>.*?</style>",
    "",
    [System.Text.RegularExpressions.RegexOptions]::IgnoreCase `
      -bor [System.Text.RegularExpressions.RegexOptions]::Singleline
  )
  $text = [regex]::Replace(
    $text,
    "<noscript\b[^>]*>.*?</noscript>",
    "",
    [System.Text.RegularExpressions.RegexOptions]::IgnoreCase `
      -bor [System.Text.RegularExpressions.RegexOptions]::Singleline
  )

  $linkPattern = "<a\b[^>]*href=""([^""]+)""[^>]*>(.*?)</a>"
  $text = [regex]::Replace(
    $text,
    $linkPattern,
    {
      param($match)
      $href = $match.Groups[1].Value.Trim()
      $label = Clean-Text (Decode-Html $match.Groups[2].Value)
      if ([string]::IsNullOrWhiteSpace($label)) {
        return ""
      }
      if ($href.StartsWith("http://") -or $href.StartsWith("https://")) {
        return "$label [$href]"
      }
      return "$label [link: $href]"
    },
    [System.Text.RegularExpressions.RegexOptions]::IgnoreCase `
      -bor [System.Text.RegularExpressions.RegexOptions]::Singleline
  )

  foreach ($tag in @("br", "p", "div", "section", "li", "tr", "dt", "dd", "h1", "h2", "h3", "h4", "h5", "h6", "caption", "summary")) {
    $text = [regex]::Replace($text, "</?$tag\b[^>]*>", "`n", [System.Text.RegularExpressions.RegexOptions]::IgnoreCase)
  }

  foreach ($tag in @("td", "th", "span", "code", "pre", "ul", "ol", "dl", "table", "tbody", "thead", "wbr", "main")) {
    $text = [regex]::Replace($text, "</?$tag\b[^>]*>", " ", [System.Text.RegularExpressions.RegexOptions]::IgnoreCase)
  }

  $text = [regex]::Replace($text, "<[^>]+>", " ", [System.Text.RegularExpressions.RegexOptions]::IgnoreCase)
  $text = Decode-Html $text
  $text = $text -replace [char]0x00A0, " "
  $text = Clean-Text $text

  return @"
SOURCE: $RelativePath

$text
"@
}

function Convert-ToSlug {
  param([string]$Value)

  $lower = $Value.ToLowerInvariant()
  $slug = [regex]::Replace($lower, "[^a-z0-9]+", "-")
  return $slug.Trim("-")
}

function Get-RelativePath {
  param(
    [string]$BasePath,
    [string]$TargetPath
  )

  $base = [System.IO.Path]::GetFullPath($BasePath)
  $target = [System.IO.Path]::GetFullPath($TargetPath)
  if ($target.StartsWith($base, [System.StringComparison]::OrdinalIgnoreCase)) {
    return $target.Substring($base.Length).TrimStart("\", "/")
  }

  return $target
}

$manifest = New-Object System.Collections.Generic.List[object]
$catalog = New-Object System.Collections.Generic.List[string]
$textFiles = New-Object System.Collections.Generic.List[string]

$htmlFiles = Get-ChildItem -Path $sourcePath -Recurse -File -Filter *.html |
  Where-Object { $_.FullName -notlike "*\_ai\*" } |
  Sort-Object FullName

foreach ($file in $htmlFiles) {
  $relativePath = (Get-RelativePath -BasePath $sourcePath -TargetPath $file.FullName).Replace("\", "/")
  $html = Get-Content -LiteralPath $file.FullName -Raw
  $title = Clean-Text (Decode-Html (Get-MatchValue $html "<title>(.*?)</title>"))
  $pageDescription = Clean-Text (Decode-Html (Get-MatchValue $html "<meta\s+name=""description""\s+content=""(.*?)"""))
  $packageName = Clean-Text (Decode-Html (Get-MatchValue $html 'Package</span>&nbsp;<a [^>]*>(.*?)</a>'))
  $className = Clean-Text (Decode-Html (Get-MatchValue $html '<h1[^>]*class="title"[^>]*>(.*?)</h1>'))
  $firstBlock = Clean-Text (Decode-Html (Get-MatchValue $html '<div class="block">(.*?)</div>'))

  $kind = "page"
  if ($relativePath -match "/package-summary\.html$" -or $relativePath -eq "index.html") {
    $kind = "package"
  } elseif ($relativePath -match "/package-tree\.html$" -or $relativePath -eq "overview-tree.html") {
    $kind = "tree"
  } elseif ($relativePath -in @("index-all.html", "allclasses-index.html", "allpackages-index.html", "constant-values.html", "help-doc.html")) {
    $kind = "index"
  } elseif ($className -match '^(Class|Interface|Enum Class|Annotation Interface) ') {
    $kind = "symbol"
  }

  $summary = $firstBlock
  if ([string]::IsNullOrWhiteSpace($summary)) {
    $summary = $pageDescription
  }

  $plainText = Convert-HtmlToText -Html $html -RelativePath $relativePath
  $targetRelative = [System.IO.Path]::ChangeExtension($relativePath, ".txt")
  $targetPath = Join-Path $textRoot $targetRelative
  $targetDir = Split-Path -Parent $targetPath
  if ($targetDir) {
    [System.IO.Directory]::CreateDirectory($targetDir) | Out-Null
  }
  [System.IO.File]::WriteAllText($targetPath, $plainText, [System.Text.UTF8Encoding]::new($false))

  $targetRelativeNormalized = (Get-RelativePath -BasePath $aiRoot -TargetPath $targetPath).Replace("\", "/")
  $manifest.Add([ordered]@{
    source = $relativePath
    text = $targetRelativeNormalized
    kind = $kind
    title = $title
    package = $packageName
    symbol = $className
    summary = $summary
  }) | Out-Null
  $textFiles.Add($targetRelativeNormalized) | Out-Null

  $catalogLine = "{0}`t{1}`t{2}`t{3}`t{4}" -f `
    $kind, `
    $relativePath, `
    $targetRelativeNormalized, `
    $title.Replace("`t", " "), `
    $summary.Replace("`t", " ")
  $catalog.Add($catalogLine) | Out-Null
}

$indexHtmlPath = Join-Path $sourcePath "index-all.html"
$indexEntries = New-Object System.Collections.Generic.List[string]
if (Test-Path -LiteralPath $indexHtmlPath) {
  $indexHtml = Get-Content -LiteralPath $indexHtmlPath -Raw
  $indexMatches = [regex]::Matches(
    $indexHtml,
    "<dt><a href=""([^""]+)""[^>]*>(.*?)</a>\s*-\s*(.*?)</dt>\s*<dd>\s*<div class=""block"">(.*?)</div>",
    [System.Text.RegularExpressions.RegexOptions]::IgnoreCase `
      -bor [System.Text.RegularExpressions.RegexOptions]::Singleline
  )

  foreach ($match in $indexMatches) {
    $href = $match.Groups[1].Value.Trim()
    $name = Strip-Html $match.Groups[2].Value
    $context = Strip-Html $match.Groups[3].Value
    $summary = Strip-Html $match.Groups[4].Value
    $indexEntries.Add(("{0}`t{1}`t{2}`t{3}" -f $name, $context, $href, $summary)) | Out-Null
  }
}

$readmeLines = @(
  '# MyScript SDK Docs Mirror',
  '',
  'This directory contains a local mirror of the MyScript Interactive Ink Android 4.3 Javadocs plus an AI-friendly plaintext companion layer.',
  '',
  '## Use This First',
  '',
  '- Raw HTML mirror: `docs/Myscript/SDK/`',
  '- Plaintext mirror: `docs/Myscript/SDK/_ai/text/`',
  '- Page manifest: `docs/Myscript/SDK/_ai/manifest.json`',
  '- Quick catalog: `docs/Myscript/SDK/_ai/catalog.tsv`',
  '- Symbol/member index extracted from `index-all.html`: `docs/Myscript/SDK/_ai/symbols.tsv`',
  '',
  '## Terminal Usage',
  '',
  '- Search symbols: `rg "Editor|ContentPart|addBlock" docs/Myscript/SDK/_ai/text`',
  '- Find the best page first: `rg "addBlock" docs/Myscript/SDK/_ai/symbols.tsv`',
  '- Open a normalized page: `Get-Content docs/Myscript/SDK/_ai/text/com/myscript/iink/Editor.txt -TotalCount 120`',
  '',
  '## Notes',
  '',
  '- The upstream Javadoc stylesheet references `resources/fonts/dejavu.css`, but that file returns `404` on the source site too.',
  '- The `_ai` directory is generated from the raw mirror by `scripts/normalize-myscript-sdk-docs.ps1`.'
)

[System.IO.File]::WriteAllText(
  (Join-Path $sourcePath "README.md"),
  ($readmeLines -join "`n"),
  [System.Text.UTF8Encoding]::new($false)
)

[System.IO.File]::WriteAllText(
  (Join-Path $aiRoot "catalog.tsv"),
  ("kind`tsource`ttext`ttitle`tsummary`n" + ($catalog -join "`n")),
  [System.Text.UTF8Encoding]::new($false)
)

[System.IO.File]::WriteAllText(
  (Join-Path $aiRoot "symbols.tsv"),
  ("name`tcontext`thref`tsummary`n" + ($indexEntries -join "`n")),
  [System.Text.UTF8Encoding]::new($false)
)

$manifestJson = $manifest | ConvertTo-Json -Depth 4
[System.IO.File]::WriteAllText(
  (Join-Path $aiRoot "manifest.json"),
  $manifestJson,
  [System.Text.UTF8Encoding]::new($false)
)

$overviewLines = @(
  "# MyScript SDK AI Index",
  "",
  "Generated plaintext pages: $($textFiles.Count)",
  "",
  "Top entry points:",
  "- index.txt",
  "- index-all.txt",
  "- allclasses-index.txt",
  "- com/myscript/iink/Editor.txt",
  "- com/myscript/iink/Engine.txt",
  "- com/myscript/iink/ContentPart.txt"
)
[System.IO.File]::WriteAllText(
  (Join-Path $aiRoot "INDEX.md"),
  ($overviewLines -join "`n"),
  [System.Text.UTF8Encoding]::new($false)
)

Write-Host "Normalized $($textFiles.Count) HTML pages into $textRoot"
