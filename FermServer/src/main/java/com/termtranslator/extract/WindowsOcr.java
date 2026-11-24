package com.termtranslator.extract;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 글자 인식(OCR). Windows 10/11 에 들어 있는 기능(Windows.Media.Ocr)을 PowerShell 로 불러 쓴다.
 * 따로 설치할 프로그램이나 라이브러리가 없고, 그림과 PDF(페이지를 그림으로 그린 뒤 인식)를 처리한다.
 *
 * 한계: 한글 문장은 잘 읽지만 수식 기호(σ, ² 등)와 영문 식별자(l/I, 0/O)는 틀릴 수 있다.
 *       Windows 가 아닌 곳에서는 쓸 수 없다. 다른 OCR 로 바꾸려면 이 클래스만 교체하면 된다.
 *
 * 주고받는 방식: 입력은 환경변수(TT_OCR_*)로 넘기고, 결과는 페이지마다 UTF-8 파일(00001.txt ...)로 받는다.
 *               진행 상황은 표준 출력의 "PAGE 현재 전체" 줄로 받는다.
 */
final class WindowsOcr {

    private static final String SCRIPT = """
            $ErrorActionPreference = 'Stop'
            [Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false)

            Add-Type -AssemblyName System.Runtime.WindowsRuntime
            $null = [Windows.Storage.StorageFile, Windows.Storage, ContentType = WindowsRuntime]
            $null = [Windows.Storage.Streams.InMemoryRandomAccessStream, Windows.Storage.Streams, ContentType = WindowsRuntime]
            $null = [Windows.Graphics.Imaging.BitmapDecoder, Windows.Graphics.Imaging, ContentType = WindowsRuntime]
            $null = [Windows.Media.Ocr.OcrEngine, Windows.Foundation, ContentType = WindowsRuntime]
            $null = [Windows.Globalization.Language, Windows.Globalization, ContentType = WindowsRuntime]
            $null = [Windows.Data.Pdf.PdfDocument, Windows.Data.Pdf, ContentType = WindowsRuntime]

            $asTaskMethods = [System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object { $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 }
            $asTaskOperation = ($asTaskMethods | Where-Object { $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' })[0]
            $asTaskAction = ($asTaskMethods | Where-Object { $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncAction' })[0]

            function Await($operation, [Type]$resultType) {
                $task = $asTaskOperation.MakeGenericMethod($resultType).Invoke($null, @($operation))
                $null = $task.Wait(-1)
                $task.Result
            }

            function AwaitAction($action) {
                $task = $asTaskAction.Invoke($null, @($action))
                $null = $task.Wait(-1)
            }

            $engine = [Windows.Media.Ocr.OcrEngine]::TryCreateFromLanguage((New-Object Windows.Globalization.Language 'ko'))
            if ($null -eq $engine) { $engine = [Windows.Media.Ocr.OcrEngine]::TryCreateFromUserProfileLanguages() }
            if ($null -eq $engine) { throw 'NO_OCR_LANGUAGE' }
            $maxSide = [double][Windows.Media.Ocr.OcrEngine]::MaxImageDimension
            $utf8 = New-Object System.Text.UTF8Encoding($false)

            function Read-Stream($stream) {
                $decoder = Await ([Windows.Graphics.Imaging.BitmapDecoder]::CreateAsync($stream)) ([Windows.Graphics.Imaging.BitmapDecoder])
                $longest = [Math]::Max([double]$decoder.PixelWidth, [double]$decoder.PixelHeight)
                if ($longest -gt $maxSide) {
                    $ratio = $maxSide / $longest
                    $transform = New-Object Windows.Graphics.Imaging.BitmapTransform
                    $transform.ScaledWidth = [uint32][Math]::Floor($decoder.PixelWidth * $ratio)
                    $transform.ScaledHeight = [uint32][Math]::Floor($decoder.PixelHeight * $ratio)
                    $transform.InterpolationMode = [Windows.Graphics.Imaging.BitmapInterpolationMode]::Fant
                    $bitmap = Await ($decoder.GetSoftwareBitmapAsync([Windows.Graphics.Imaging.BitmapPixelFormat]::Bgra8,
                        [Windows.Graphics.Imaging.BitmapAlphaMode]::Premultiplied, $transform,
                        [Windows.Graphics.Imaging.ExifOrientationMode]::RespectExifOrientation,
                        [Windows.Graphics.Imaging.ColorManagementMode]::DoNotColorManage)) ([Windows.Graphics.Imaging.SoftwareBitmap])
                } else {
                    $bitmap = Await ($decoder.GetSoftwareBitmapAsync()) ([Windows.Graphics.Imaging.SoftwareBitmap])
                }
                $result = Await ($engine.RecognizeAsync($bitmap)) ([Windows.Media.Ocr.OcrResult])
                $bitmap.Dispose()
                Join-Lines $result
            }

            # OCR returns lines in its own order (table columns come out grouped).
            # Re-order by position: lines on the same height become one row, joined left to right with ' | '.
            function Join-Lines($result) {
                $found = @(foreach ($line in $result.Lines) {
                    $left = [double]::MaxValue; $top = [double]::MaxValue; $bottom = 0.0
                    foreach ($word in $line.Words) {
                        $r = $word.BoundingRect
                        if ($r.X -lt $left) { $left = $r.X }
                        if ($r.Y -lt $top) { $top = $r.Y }
                        if (($r.Y + $r.Height) -gt $bottom) { $bottom = $r.Y + $r.Height }
                    }
                    [pscustomobject]@{ Text = $line.Text; Left = $left; Top = $top; Bottom = $bottom; Mid = ($top + $bottom) / 2 }
                })
                $items = @($found | Sort-Object Mid)
                $rows = New-Object System.Collections.ArrayList
                foreach ($item in $items) {
                    $row = $null
                    if ($rows.Count -gt 0) { $row = $rows[$rows.Count - 1] }
                    if ($null -ne $row -and $item.Mid -ge $row[0].Top -and $item.Mid -le $row[0].Bottom) {
                        $null = $row.Add($item)
                    } else {
                        $new = New-Object System.Collections.ArrayList
                        $null = $new.Add($item)
                        $null = $rows.Add($new)
                    }
                }
                ($rows | ForEach-Object { (@($_ | Sort-Object Left) | ForEach-Object { $_.Text }) -join ' | ' }) -join "`n"
            }

            function Save-Text([int]$number, [string]$text) {
                [System.IO.File]::WriteAllText((Join-Path $env:TT_OCR_OUT ('{0:D5}.txt' -f $number)), $text, $utf8)
            }

            if ($env:TT_OCR_MODE -eq 'pdf') {
                $file = Await ([Windows.Storage.StorageFile]::GetFileFromPathAsync($env:TT_OCR_INPUT)) ([Windows.Storage.StorageFile])
                $pdf = Await ([Windows.Data.Pdf.PdfDocument]::LoadFromFileAsync($file)) ([Windows.Data.Pdf.PdfDocument])
                $count = [int]$pdf.PageCount
                Write-Output "COUNT $count"
                $wanted = @()
                if ($env:TT_OCR_PAGES) { $wanted = @($env:TT_OCR_PAGES.Split(',') | ForEach-Object { [int]$_ }) }
                for ($i = 0; $i -lt $count; $i++) {
                    $number = $i + 1
                    if ($wanted.Count -gt 0 -and $wanted -notcontains $number) { continue }
                    Write-Output "PAGE $number $count"
                    $page = $pdf.GetPage([uint32]$i)
                    $longest = [Math]::Max([double]$page.Size.Width, [double]$page.Size.Height)
                    $scale = [Math]::Min(2400.0 / $longest, $maxSide / $longest)
                    $options = New-Object Windows.Data.Pdf.PdfPageRenderOptions
                    $options.DestinationWidth = [uint32][Math]::Round($page.Size.Width * $scale)
                    $options.DestinationHeight = [uint32][Math]::Round($page.Size.Height * $scale)
                    $stream = New-Object Windows.Storage.Streams.InMemoryRandomAccessStream
                    AwaitAction ($page.RenderToStreamAsync($stream, $options))
                    Save-Text $number (Read-Stream $stream)
                    $stream.Dispose()
                    $page.Dispose()
                }
            } else {
                $paths = @([System.IO.File]::ReadAllLines($env:TT_OCR_INPUT, $utf8) | Where-Object { $_.Trim() })
                $count = $paths.Count
                Write-Output "COUNT $count"
                $number = 0
                foreach ($path in $paths) {
                    $number++
                    Write-Output "PAGE $number $count"
                    $file = Await ([Windows.Storage.StorageFile]::GetFileFromPathAsync($path)) ([Windows.Storage.StorageFile])
                    $stream = Await ($file.OpenAsync([Windows.Storage.FileAccessMode]::Read)) ([Windows.Storage.Streams.IRandomAccessStream])
                    Save-Text $number (Read-Stream $stream)
                    $stream.Dispose()
                }
            }
            Write-Output 'DONE'
            """;

    private WindowsOcr() {}

    static boolean available() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
    }

    /** 그림 파일들을 순서대로 인식한다. 결과 목록은 입력과 같은 순서, 같은 개수. */
    static List<String> images(List<Path> files, Consumer<String> log) throws IOException, InterruptedException {
        requireWindows();
        Path work = Files.createTempDirectory("tt-ocr");
        try {
            Path list = work.resolve("list.txt");
            Files.write(list, files.stream().map(f -> f.toAbsolutePath().normalize().toString()).toList(), StandardCharsets.UTF_8);
            Map<Integer, String> out = run(work, "images", list.toString(), "", log);
            List<String> texts = new ArrayList<>();
            for (int i = 1; i <= files.size(); i++) texts.add(out.getOrDefault(i, ""));
            return texts;
        } finally {
            deleteTree(work);
        }
    }

    /** PDF 페이지를 그림으로 그려서 인식한다. pages 가 비어 있으면 전체, 아니면 그 페이지(1부터)만. */
    static Map<Integer, String> pdf(Path pdf, List<Integer> pages, Consumer<String> log) throws IOException, InterruptedException {
        requireWindows();
        Path work = Files.createTempDirectory("tt-ocr");
        try {
            String wanted = pages.stream().map(String::valueOf).collect(Collectors.joining(","));
            return run(work, "pdf", pdf.toAbsolutePath().normalize().toString(), wanted, log);
        } finally {
            deleteTree(work);
        }
    }

    private static void requireWindows() throws IOException {
        if (!available()) {
            throw new IOException("글자 인식(OCR)은 Windows 에서만 지원합니다. 그림이나 스캔한 PDF 는 Windows 에서 추출하세요.");
        }
    }

    private static Map<Integer, String> run(Path work, String mode, String input, String pages, Consumer<String> log)
            throws IOException, InterruptedException {
        Path script = work.resolve("ocr.ps1");
        Path outDir = Files.createDirectory(work.resolve("out"));
        Path console = work.resolve("console.txt");
        Files.writeString(script, SCRIPT, StandardCharsets.UTF_8);

        // 스크립트 파일을 "실행"하지 않고 내용을 읽어서 명령으로 넘긴다 (실행 정책 설정을 건드리지 않기 위해)
        ProcessBuilder pb = new ProcessBuilder(powershell(), "-NoProfile", "-NonInteractive", "-Command",
                "[Console]::OutputEncoding = [Text.Encoding]::UTF8; "
                        + "& ([scriptblock]::Create([IO.File]::ReadAllText($env:TT_OCR_SCRIPT, [Text.Encoding]::UTF8)))");
        pb.environment().put("TT_OCR_SCRIPT", script.toString());
        pb.environment().put("TT_OCR_MODE", mode);
        pb.environment().put("TT_OCR_INPUT", input);
        pb.environment().put("TT_OCR_OUT", outDir.toString());
        pb.environment().put("TT_OCR_PAGES", pages);
        pb.redirectErrorStream(true);
        pb.redirectOutput(console.toFile());

        log.accept("글자 인식(OCR) 준비 중 (시작할 때 몇 초 걸립니다)");
        Process process = pb.start();
        process.getOutputStream().close();
        int seen = 0;
        try {
            while (!process.waitFor(300, TimeUnit.MILLISECONDS)) seen = report(console, seen, log);
        } catch (InterruptedException e) {
            process.destroyForcibly();
            throw e;
        }
        report(console, seen, log);

        List<String> lines = consoleLines(console);
        if (process.exitValue() != 0 || !lines.contains("DONE")) {
            String detail = lines.stream().filter(l -> !l.matches("(COUNT|PAGE) .*") && !l.isBlank())
                    .map(String::strip).collect(Collectors.joining(" "));
            if (detail.contains("NO_OCR_LANGUAGE")) {
                throw new IOException("Windows 에 글자 인식(OCR) 언어가 설치되어 있지 않습니다.\n"
                        + "  [설정 > 시간 및 언어 > 언어 및 지역]에서 한국어의 '광학 문자 인식' 기능을 추가하세요.");
            }
            throw new IOException("글자 인식(OCR)에 실패했습니다: "
                    + (detail.length() > 500 ? detail.substring(0, 500) + " ..." : detail));
        }

        Map<Integer, String> out = new HashMap<>();
        try (Stream<Path> s = Files.list(outDir)) {
            for (Path f : s.toList()) {
                String name = f.getFileName().toString();
                if (name.matches("\\d+\\.txt")) {
                    out.put(Integer.parseInt(name.substring(0, name.length() - 4)), Files.readString(f, StandardCharsets.UTF_8));
                }
            }
        }
        return out;
    }

    /** 새로 나온 "PAGE 현재 전체" 줄을 진행 상황으로 알린다. 돌려주는 값은 지금까지 본 줄 수. */
    private static int report(Path console, int seen, Consumer<String> log) {
        List<String> lines = consoleLines(console);
        for (int i = seen; i < lines.size(); i++) {
            String[] parts = lines.get(i).split(" ");
            if (parts.length == 3 && parts[0].equals("PAGE")) log.accept("글자 인식 중: " + parts[1] + " / " + parts[2]);
        }
        return lines.size();
    }

    /** 끝까지 쓰인 줄만 돌려준다 (쓰는 도중인 마지막 줄은 다음에 읽는다). */
    private static List<String> consoleLines(Path console) {
        try {
            String text = new String(Files.readAllBytes(console), StandardCharsets.UTF_8).replace("\r", "");
            int end = text.lastIndexOf('\n');
            return end < 0 ? List.of() : List.of(text.substring(0, end).split("\n"));
        } catch (IOException e) {
            return List.of();
        }
    }

    private static String powershell() {
        String root = System.getenv("SystemRoot");
        if (root != null) {
            Path exe = Path.of(root, "System32", "WindowsPowerShell", "v1.0", "powershell.exe");
            if (Files.isRegularFile(exe)) return exe.toString();
        }
        return "powershell.exe";
    }

    private static void deleteTree(Path dir) {
        try (Stream<Path> s = Files.walk(dir)) {
            for (Path p : s.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
        } catch (IOException e) {
            // 임시 폴더라 남아도 문제없다
        }
    }
}
