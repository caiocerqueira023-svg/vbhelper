param(
    [string]$SourceDirectory = (Join-Path $PSScriptRoot '..\..\app\src\main\assets\Arena\Colosseum'),
    [string]$OutputPath = (Join-Path $PSScriptRoot '..\..\app\src\main\assets\Arena\Colosseum\Colosseum.glb'),
    [string]$ManifestPath = (Join-Path $PSScriptRoot '..\..\app\src\main\assets\Arena\Colosseum\arena.json'),
    [string]$DomeTexturePath = (Join-Path $PSScriptRoot 'Battle_stadium_sphere_purple.png')
)

$ErrorActionPreference = 'Stop'
$culture = [Globalization.CultureInfo]::InvariantCulture
$daePath = Join-Path $SourceDirectory 'BG_Battle_PVP_Base_01.dae'
if (-not (Test-Path -LiteralPath $daePath)) { throw "Collada arena not found: $daePath" }

[xml]$document = Get-Content -LiteralPath $daePath -Raw
$nodes = $document.SelectNodes('//*[local-name()="library_visual_scenes"]//*[local-name()="node"]')
$meshRecords = [System.Collections.Generic.List[object]]::new()
$sourceImages = @{}
foreach ($image in $document.SelectNodes('//*[local-name()="library_images"]/*[local-name()="image"]')) {
    $sourceImages[$image.GetAttribute('id')] = $image.SelectSingleNode('./*[local-name()="init_from"]').InnerText
}

foreach ($sceneNode in $nodes) {
    $instance = $sceneNode.SelectSingleNode('./*[local-name()="instance_controller"]')
    if ($null -eq $instance) { continue }
    $controllerId = $instance.GetAttribute('url').TrimStart('#')
    $controller = $document.SelectSingleNode("//*[local-name()='controller' and @id='$controllerId']")
    $skin = $controller.SelectSingleNode('./*[local-name()="skin"]')
    $geometryId = $skin.GetAttribute('source').TrimStart('#')
    $geometry = $document.SelectSingleNode("//*[local-name()='geometry' and @id='$geometryId']")
    $mesh = $geometry.SelectSingleNode('./*[local-name()="mesh"]')
    $triangles = $mesh.SelectSingleNode('./*[local-name()="triangles"]')
    $vertexInput = $triangles.SelectSingleNode('./*[local-name()="input"][@semantic="VERTEX"]')
    if ($null -eq $vertexInput) { throw "Mesh $($geometry.GetAttribute('name')) has no vertex index stream." }
    $vertices = $mesh.SelectSingleNode('./*[local-name()="vertices"]')
    $vertexStreams = @{}
    foreach ($input in $vertices.SelectNodes('./*[local-name()="input"]')) {
        $vertexStreams[$input.GetAttribute('semantic')] = $input.GetAttribute('source').TrimStart('#')
    }
    $streams = @{}
    foreach ($semantic in @('POSITION', 'NORMAL', 'TEXCOORD')) {
        $streamId = $vertexStreams[$semantic]
        if (-not $streamId) { continue }
        $source = $mesh.SelectSingleNode("./*[local-name()='source' and @id='$streamId']")
        $array = $source.SelectSingleNode('./*[local-name()="float_array"]')
        $accessor = $source.SelectSingleNode('./*[local-name()="technique_common"]/*[local-name()="accessor"]')
        $stride = if ($accessor.GetAttribute('stride')) { [int]$accessor.GetAttribute('stride') } else { if ($semantic -eq 'TEXCOORD') { 2 } else { 3 } }
        $values = [System.Collections.Generic.List[float]]::new()
        foreach ($value in ($array.InnerText.Trim() -split '\s+')) { $values.Add([float]::Parse($value, $culture)) }
        $streams[$semantic] = @{ Values = $values.ToArray(); Stride = $stride }
    }
    if (-not $streams.POSITION -or -not $streams.TEXCOORD) { throw "Mesh $($geometry.GetAttribute('name')) must have positions and texture coordinates." }
    $indices = @($triangles.SelectSingleNode('./*[local-name()="p"]').InnerText.Trim() -split '\s+' | ForEach-Object { [int]$_ })
    $stride = 1 + [int](@($triangles.SelectNodes('./*[local-name()="input"]') | ForEach-Object { [int]$_.GetAttribute('offset') } | Measure-Object -Maximum).Maximum)
    $indexOffset = [int]$vertexInput.GetAttribute('offset')
    $meshIndices = [System.Collections.Generic.List[int]]::new()
    for ($i = 0; $i -lt $indices.Count; $i += $stride) { $meshIndices.Add($indices[$i + $indexOffset]) }
    $positions = $streams.POSITION.Values
    $normals = if ($streams.NORMAL) { $streams.NORMAL.Values } else { $null }
    $uvs = $streams.TEXCOORD.Values
    $materialSymbol = $triangles.GetAttribute('material')
    $binding = $instance.SelectSingleNode("./*[local-name()='bind_material']/*[local-name()='technique_common']/*[local-name()='instance_material' and @symbol='$materialSymbol']")
    if ($null -eq $binding) { throw "Material binding $materialSymbol is missing for $($geometry.GetAttribute('name'))." }
    $materialId = $binding.GetAttribute('target').TrimStart('#')
    $material = $document.SelectSingleNode("//*[local-name()='material' and @id='$materialId']")
    $effectId = $material.SelectSingleNode('./*[local-name()="instance_effect"]').GetAttribute('url').TrimStart('#')
    $effect = $document.SelectSingleNode("//*[local-name()='effect' and @id='$effectId']")
    $imageId = $effect.SelectSingleNode('.//*[local-name()="profile_COMMON"]//*[local-name()="surface"]/*[local-name()="init_from"]')
    $imagePath = $sourceImages[$imageId.InnerText]
    if (-not $imagePath) { throw "Texture for $($geometry.GetAttribute('name')) was not resolved." }
    $meshRecords.Add(@{
        Name = $sceneNode.GetAttribute('name')
        Positions = $positions
        PositionStride = $streams.POSITION.Stride
        Normals = $normals
        NormalStride = if ($streams.NORMAL) { $streams.NORMAL.Stride } else { 0 }
        Uvs = $uvs
        UvStride = $streams.TEXCOORD.Stride
        Indices = $meshIndices.ToArray()
        ImagePath = $imagePath
        Matrix = $sceneNode.SelectSingleNode('./*[local-name()="matrix"]').InnerText
    })
}
if ($meshRecords.Count -ne 2) { throw "Expected two Collada stadium meshes, found $($meshRecords.Count)." }

$binary = [System.IO.MemoryStream]::new()
$writer = [System.IO.BinaryWriter]::new($binary)
$bufferViews = [System.Collections.Generic.List[object]]::new()
$accessors = [System.Collections.Generic.List[object]]::new()
$images = [System.Collections.Generic.List[object]]::new()
$textures = [System.Collections.Generic.List[object]]::new()
$materials = [System.Collections.Generic.List[object]]::new()
$meshes = [System.Collections.Generic.List[object]]::new()
$sceneNodes = [System.Collections.Generic.List[object]]::new()

function Add-BufferView([byte[]]$Bytes, [Nullable[int]]$Target = $null) {
    while ($script:binary.Position % 4 -ne 0) { $script:writer.Write([byte]0) }
    $entry = @{ buffer = 0; byteOffset = [int]$script:binary.Position; byteLength = $Bytes.Length }
    if ($Target.HasValue) { $entry.target = $Target.Value }
    $index = $script:bufferViews.Count
    $script:bufferViews.Add($entry)
    $script:writer.Write($Bytes)
    return $index
}

function Add-FloatAccessor([float[]]$Values, [int]$Components, [string]$Type, [int]$Target, [double[]]$Minimum, [double[]]$Maximum) {
    $bytes = [byte[]]::new($Values.Length * 4)
    [System.Buffer]::BlockCopy($Values, 0, $bytes, 0, $bytes.Length)
    $view = Add-BufferView $bytes $Target
    $accessor = @{ bufferView = $view; componentType = 5126; count = [int]($Values.Length / $Components); type = $Type }
    if ($Minimum) { $accessor.min = $Minimum; $accessor.max = $Maximum }
    $index = $script:accessors.Count
    $script:accessors.Add($accessor)
    return $index
}

foreach ($record in $meshRecords) {
    $vertexCount = [int]($record.Positions.Length / $record.PositionStride)
    $positions = [System.Collections.Generic.List[float]]::new()
    $positionMin = [double[]]::new(3); $positionMax = [double[]]::new(3)
    $positionMin[0] = [double]::PositiveInfinity; $positionMin[1] = [double]::PositiveInfinity; $positionMin[2] = [double]::PositiveInfinity
    $positionMax[0] = [double]::NegativeInfinity; $positionMax[1] = [double]::NegativeInfinity; $positionMax[2] = [double]::NegativeInfinity
    for ($vertex = 0; $vertex -lt $vertexCount; $vertex++) {
        for ($component = 0; $component -lt 3; $component++) {
            $value = [float]$record.Positions[$vertex * $record.PositionStride + $component]
            $positions.Add($value)
            $positionMin[$component] = [Math]::Min($positionMin[$component], $value)
            $positionMax[$component] = [Math]::Max($positionMax[$component], $value)
        }
    }
    $positionAccessor = Add-FloatAccessor $positions.ToArray() 3 'VEC3' 34962 $positionMin $positionMax
    $normalAccessor = $null
    if ($record.Normals) {
        $normalValues = [System.Collections.Generic.List[float]]::new()
        for ($vertex = 0; $vertex -lt $vertexCount; $vertex++) {
            for ($component = 0; $component -lt 3; $component++) {
                $normalValues.Add([float]$record.Normals[$vertex * $record.NormalStride + $component])
            }
        }
        $normalAccessor = Add-FloatAccessor $normalValues.ToArray() 3 'VEC3' 34962 $null $null
    }
    $uvValues = [System.Collections.Generic.List[float]]::new()
    for ($vertex = 0; $vertex -lt $vertexCount; $vertex++) {
        $uvValues.Add([float]$record.Uvs[$vertex * $record.UvStride])
        # Collada textures have their origin at the bottom; glTF uses the top.
        $uvValues.Add(1.0 - [float]$record.Uvs[$vertex * $record.UvStride + 1])
    }
    $uvAccessor = Add-FloatAccessor $uvValues.ToArray() 2 'VEC2' 34962 $null $null
    $indexBytes = [byte[]]::new($record.Indices.Length * 2)
    for ($i = 0; $i -lt $record.Indices.Length; $i++) {
        $value = [uint16]$record.Indices[$i]
        $indexBytes[$i * 2] = [byte]($value -band 255)
        $indexBytes[$i * 2 + 1] = [byte](($value -shr 8) -band 255)
    }
    $indexView = Add-BufferView $indexBytes 34963
    $indexAccessor = $accessors.Count
    $accessors.Add(@{ bufferView = $indexView; componentType = 5123; count = $record.Indices.Length; type = 'SCALAR' })

    $textureFile = if ($record.Name -eq 'Sphere001') {
        $DomeTexturePath
    } else {
        Join-Path $SourceDirectory $record.ImagePath
    }
    if (-not (Test-Path -LiteralPath $textureFile)) { throw "Stadium texture not found: $textureFile" }
    $imageView = Add-BufferView ([System.IO.File]::ReadAllBytes($textureFile)) $null
    $imageIndex = $images.Count
    $images.Add(@{ bufferView = $imageView; mimeType = 'image/png'; name = [System.IO.Path]::GetFileNameWithoutExtension($textureFile) })
    $textureIndex = $textures.Count
    $textures.Add(@{ sampler = 0; source = $imageIndex })
    $materialIndex = $materials.Count
    $materials.Add(@{
        name = $record.Name
        doubleSided = $true
        extensions = @{ KHR_materials_unlit = @{} }
        pbrMetallicRoughness = @{ baseColorTexture = @{ index = $textureIndex }; metallicFactor = 0; roughnessFactor = 1 }
    })
    $attributes = @{ POSITION = $positionAccessor; TEXCOORD_0 = $uvAccessor }
    if ($null -ne $normalAccessor) { $attributes.NORMAL = $normalAccessor }
    $meshes.Add(@{
        name = $record.Name
        primitives = @(@{ attributes = $attributes; indices = $indexAccessor; material = $materialIndex; mode = 4 })
    })
    $sourceMatrix = @($record.Matrix.Trim() -split '\s+' | ForEach-Object { [double]::Parse($_, $culture) })
    # Collada serializes matrices row-major, glTF column-major.
    $matrix = @(for ($column = 0; $column -lt 4; $column++) {
        for ($row = 0; $row -lt 4; $row++) { $sourceMatrix[$row * 4 + $column] }
    })
    $sceneNodes.Add(@{ name = $record.Name; mesh = $meshes.Count - 1; matrix = $matrix })
}

while ($binary.Position % 4 -ne 0) { $writer.Write([byte]0) }
$documentObject = @{
    asset = @{ version = '2.0'; generator = 'VBHelper Collada arena converter' }
    scene = 0
    extensionsUsed = @('KHR_materials_unlit')
    scenes = @(@{ nodes = @(0, 1) })
    nodes = @($sceneNodes)
    meshes = @($meshes)
    materials = @($materials)
    textures = @($textures)
    samplers = @(@{ magFilter = 9729; minFilter = 9987; wrapS = 10497; wrapT = 10497 })
    images = @($images)
    accessors = @($accessors)
    bufferViews = @($bufferViews)
    buffers = @(@{ byteLength = [int]$binary.Length })
}
$json = [System.Text.Encoding]::UTF8.GetBytes(($documentObject | ConvertTo-Json -Depth 80 -Compress))
$jsonPadding = (4 - ($json.Length % 4)) % 4
$jsonChunkLength = $json.Length + $jsonPadding
$binaryBytes = $binary.ToArray()
$totalLength = 12 + 8 + $jsonChunkLength + 8 + $binaryBytes.Length
$output = [System.IO.MemoryStream]::new()
$outputWriter = [System.IO.BinaryWriter]::new($output)
$outputWriter.Write([uint32]0x46546C67)
$outputWriter.Write([uint32]2)
$outputWriter.Write([uint32]$totalLength)
$outputWriter.Write([uint32]$jsonChunkLength)
$outputWriter.Write([uint32]0x4E4F534A)
$outputWriter.Write($json)
for ($i = 0; $i -lt $jsonPadding; $i++) { $outputWriter.Write([byte]0x20) }
$outputWriter.Write([uint32]$binaryBytes.Length)
$outputWriter.Write([uint32]0x004E4942)
$outputWriter.Write($binaryBytes)
$outputPathResolved = [System.IO.Path]::GetFullPath($OutputPath)
[System.IO.Directory]::CreateDirectory([System.IO.Path]::GetDirectoryName($outputPathResolved)) | Out-Null
[System.IO.File]::WriteAllBytes($outputPathResolved, $output.ToArray())
$manifestPathResolved = [System.IO.Path]::GetFullPath($ManifestPath)
$manifest = [ordered]@{
    schemaVersion = 1
    assetPath = 'Arena/Colosseum/Colosseum.glb'
    upAxis = 'Y'
    playPlane = 'XZ'
    sourceUnits = 'Collada units scaled for the Filament scene'
    # Central flat disc radius = 11.8936 source units => 8.32552 world units.
    # Raised seating starts near 23.06 source units => 16.14 world units.
    # Keep the camera at 15.2 world units to preserve a clear view of the fighters.
    visualScale = 0.7
    positionScale = 1.0
    fighterScale = 1.65
    playableRadius = 8.0
    cameraCollisionRadius = 15.2
    lineupRowSpacing = 3.4
    lineupDepthRatio = 0.38
    minimumLineupDepth = 1.2
    camera = [ordered]@{
        distance = 19.0; pitchRadians = 0.62; targetY = 0.7
        minDistance = 10.0; maxDistance = 22.0; minPitchRadians = 0.2; maxPitchRadians = 1.15
    }
    meshes = [ordered]@{ floor = 'Ground'; dome = 'Sphere001' }
    blockedRegions = @()
}
[System.IO.Directory]::CreateDirectory([System.IO.Path]::GetDirectoryName($manifestPathResolved)) | Out-Null
[System.IO.File]::WriteAllText($manifestPathResolved, ($manifest | ConvertTo-Json -Depth 8), [System.Text.UTF8Encoding]::new($false))
Write-Output "Wrote $outputPathResolved ($($output.Length) bytes; $($meshRecords.Count) meshes)."
Write-Output "Wrote arena runtime manifest $manifestPathResolved."
