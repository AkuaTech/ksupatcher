package org.akuatech.ksupatcher.viewmodel

import android.app.Application
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.akuatech.ksupatcher.BuildConfig
import org.akuatech.ksupatcher.data.UpdateConfig
import org.akuatech.ksupatcher.network.DownloadRepository
import org.akuatech.ksupatcher.network.GitHubReleaseRepository
import org.akuatech.ksupatcher.root.RootShell
import org.akuatech.ksupatcher.util.RomZipExtractor
import java.io.File
import java.util.Locale

class KsuEngine(
    private val app: Application,
    private val downloadRepository: DownloadRepository,
    private val releaseRepository: GitHubReleaseRepository,
) {

    fun workDir(): File {
        val dir = File(app.codeCacheDir, "work")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun resolveBinary(variant: KsuVariant): File {
        val fileName = when (variant) {
            KsuVariant.KSU -> "libksud.so"
            KsuVariant.KSUN -> "libksud_next.so"
        }
        try {
            return resolveBundledBinary(fileName)
        } catch (e: Throwable) {
            if (variant == KsuVariant.KSUN) {
                try {
                    return resolveBundledBinary("libksud.so")
                } catch (_: Throwable) {
                    throw IllegalStateException("Missing bundled KSUN binary: $fileName and no fallback libksud.so available", e)
                }
            }
            throw e
        }
    }

    private fun resolveBundledBinary(fileName: String): File {
        val nativeLibDir = File(app.applicationInfo.nativeLibraryDir)
        val file = File(nativeLibDir, fileName)
        if (!file.exists()) {
            val available = nativeLibDir.listFiles()?.joinToString(",") { it.name } ?: "none"
            error("Bundled binary not found: ${file.absolutePath}. Available: $available")
        }
        return file
    }

    fun prepareKsud(variant: KsuVariant): File {
        File(File(app.filesDir, "work"), "ksud").delete()
        val ksud = resolveBinary(variant)
        ksud.setExecutable(true, false)
        if (!ksud.canExecute()) {
            error("Bundled binary is not executable: ${ksud.absolutePath}")
        }
        return ksud
    }

    suspend fun resolveModule(variant: KsuVariant, existing: String?): Result<Pair<String?, String>> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (!existing.isNullOrBlank()) return@runCatching null to existing
                val tag = releaseRepository.fetchLatestTag(UpdateConfig.ksuLkmOwner, UpdateConfig.ksuLkmRepo).getOrThrow()
                val asset = when (variant) {
                    KsuVariant.KSU -> UpdateConfig.ksuModuleAsset
                    KsuVariant.KSUN -> UpdateConfig.ksunModuleAsset
                }
                val moduleFile = File(workDir(), asset)
                val url = "https://github.com/${UpdateConfig.ksuLkmOwner}/${UpdateConfig.ksuLkmRepo}/releases/download/${tag}/${asset}"
                downloadRepository.download(url, moduleFile) { }.getOrThrow()
                asset to moduleFile.absolutePath
            }
        }

    fun findPatchedImage(workDir: File): File? {
        val candidates = workDir.listFiles()?.filter { file ->
            val name = file.name.lowercase(Locale.ROOT)
            file.isFile && name.endsWith(".img") && (
                name.startsWith("kernelsu_") ||
                    name.contains("patched") ||
                    name == "boot-patched.img"
                )
        } ?: emptyList()
        return candidates.maxByOrNull { it.lastModified() }
    }

    private fun shellQuote(arg: String): String = "'" + arg.replace("'", "'\\''") + "'"

    suspend fun runCommand(
        command: List<String>,
        workDir: File,
        displayCommand: String? = null,
        onLine: (String) -> Unit,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val process = try {
                ProcessBuilder(command)
                    .directory(workDir)
                    .redirectErrorStream(true)
                    .start()
            } catch (error: Throwable) {
                val execPath = command.firstOrNull().orEmpty()
                val execFile = if (execPath.isBlank()) null else File(execPath)
                val diagnostics = if (execFile == null) {
                    "execPath=unknown"
                } else {
                    "execPath=${execFile.absolutePath}, exists=${execFile.exists()}, canExec=${execFile.canExecute()}, workDir=${workDir.absolutePath}"
                }
                throw IllegalStateException("Failed to start patch process. $diagnostics. If you see error=13 Permission denied, SELinux may block exec in app domain.", error)
            }

            val sb = StringBuilder()
            val pretty = if (!displayCommand.isNullOrBlank()) {
                "$ $displayCommand"
            } else {
                val name = File(command.first()).name.replace(Regex("^lib"), "").replace(Regex("\\.so$"), "")
                "$ $name ${command.drop(1).joinToString(" ")}"
            }
            onLine(pretty)
            sb.appendLine(pretty)

            process.inputStream.bufferedReader().use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val l = line.orEmpty()
                    onLine(l)
                    sb.appendLine(l)
                }
            }

            val exitCode = process.waitFor()
            val output = sb.toString()
            if (exitCode != 0) error("Exit $exitCode\n$output")
            output
        }
    }

    suspend fun runSlotPatch(
        lkmMode: Boolean,
        variant: KsuVariant,
        kmi: String,
        moduleOverride: String?,
        allowShell: Boolean,
        enableAdbd: Boolean,
        onLine: (String) -> Unit,
        onPhase: (OtaPhase) -> Unit,
        onSlots: (String, String) -> Unit,
    ): Result<Unit> = runCatching {
        onPhase(OtaPhase.CHECKING_ROOT)
        if (!RootShell.isRooted()) {
            val granted = try {
                RootShell.run("true"); true
            } catch (_: Throwable) { false }
            if (!granted) {
                onPhase(OtaPhase.NO_ROOT)
                onLine("Root access denied. Please grant root permission to this app.")
                error("root access denied")
            }
        }
        onLine("Root access granted.")
        val variantName = if (variant == KsuVariant.KSUN) "KernelSU-Next" else "KernelSU"
        onLine("Target variant: $variantName")

        if (!lkmMode) {
            onPhase(OtaPhase.CHECKING_OTA_PROP)
            onLine("$ getprop ota.other.vbmeta_digest")
            val otaProp = RootShell.getProp("ota.other.vbmeta_digest")
            if (!otaProp.isNullOrBlank()) onLine(otaProp)
            if (otaProp.isNullOrBlank()) {
                onPhase(OtaPhase.NO_OTA_PENDING)
                onLine("No OTA update is pending (ota.other.vbmeta_digest is empty).")
                onLine("Apply an OTA update first, then come back here before rebooting.")
                error("no ota pending")
            }
            onLine("OTA detected. vbmeta_digest = $otaProp")
        }

        onPhase(OtaPhase.READING_SLOT)
        onLine("$ getprop ro.boot.slot_suffix")
        val currentSlot = RootShell.getProp("ro.boot.slot_suffix") ?: error("ro.boot.slot_suffix returned empty")
        onLine(currentSlot)
        val nextSlot = if (lkmMode) currentSlot else (if (currentSlot == "_a") "_b" else "_a")
        onSlots(currentSlot, nextSlot)
        val targetSlot = nextSlot
        onLine("Current slot: $currentSlot  →  target slot: $targetSlot")

        onPhase(OtaPhase.PATCHING)
        val ksud = try {
            prepareKsud(variant)
        } catch (e: Throwable) {
            onLine("Binary preparation failed: ${e.message}")
            throw e
        }
        val module = resolveModule(variant, moduleOverride).getOrElse {
            onLine("No kernel module found. Please select one manually or ensure your internet connection is active to auto-download.")
            throw it
        }.second

        // pick init_boot vs boot from the device not the kmi
        val targetPartition = try {
            val hasInitBoot = RootShell.run("[ -e /dev/block/by-name/init_boot$targetSlot ] && echo yes || echo no").trim()
            if (hasInitBoot == "yes") "init_boot" else "boot"
        } catch (_: Throwable) {
            "boot"
        }
        onLine("Target partition: $targetPartition$targetSlot")

        val ksudArgs = buildList {
            add("boot-patch")
            add("--flash")
            if (!lkmMode) add("--ota")
            add("--partition"); add(targetPartition)
            add("--kmi"); add(kmi)
            add("--module"); add(module)
            if (allowShell) add("--allow-shell")
            if (enableAdbd) add("--enable-adbd")
        }
        val ksudLine = (listOf(ksud.absolutePath) + ksudArgs).joinToString(" ") { shellQuote(it) }
        val rootCommand = listOf("su", "-c", ksudLine)
        val displayCommand = "ksud " + ksudArgs.joinToString(" ").replace(module, File(module).name)

        onLine(
            if (lkmMode) "Installing to current slot ($currentSlot)..."
            else "Patching and flashing inactive slot ($targetSlot)..."
        )
        runCommand(rootCommand, workDir(), displayCommand) { onLine(it) }.getOrThrow()
        Unit
    }

    suspend fun runFilePatch(
        variant: KsuVariant,
        kmi: String,
        path: String?,
        outPath: String?,
        moduleOverride: String?,
        allowShell: Boolean,
        enableAdbd: Boolean,
        onLine: (String) -> Unit,
    ): Result<Unit> = runCatching {
        val source = path?.takeUnless { it.isBlank() } ?: error("Pass a boot image or rom zip path")
        val wd = workDir()

        val sourceFile = File(source).canonicalFile
        if (!sourceFile.exists()) error("$source does not exist")
        val uri = FileProvider.getUriForFile(
            app,
            "${BuildConfig.APPLICATION_ID}.fileprovider",
            sourceFile,
        )

        val bootArg = if (RomZipExtractor.isLikelyZip(app, uri)) {
            onLine("Extracting boot image from ${sourceFile.name}")
            val extracted = RomZipExtractor.extractBootImage(app, uri, wd, hasInitBoot()) { onLine(it) }
            onLine("Extracted ${extracted.partitionName}.img")
            extracted.file.absolutePath
        } else {
            val name = sourceFile.name
            val looksLikeBoot = name.contains("boot", ignoreCase = true) && !name.contains("init", ignoreCase = true)
            if (looksLikeBoot && hasInitBoot()) {
                error("This device uses init_boot, patch the init_boot image instead of boot.img")
            }
            val target = File(wd, name.ifBlank { "boot.img" })
            app.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Unable to open $source" }
                target.outputStream().use { output -> input.copyTo(output) }
            }
            target.absolutePath
        }

        val ksud = prepareKsud(variant)
        val module = resolveModule(variant, moduleOverride).getOrElse {
            onLine("No kernel module found. Please select one manually or ensure your internet connection is active to auto-download.")
            throw it
        }.second

        val ksudArgs = buildList {
            add("boot-patch")
            add("-b"); add(bootArg)
            add("--kmi"); add(kmi)
            add("--module"); add(module)
            add("-o"); add(wd.absolutePath)
            if (allowShell) add("--allow-shell")
            if (enableAdbd) add("--enable-adbd")
        }
        val displayCommand = "ksud " + ksudArgs.joinToString(" ").replace(module, File(module).name)
        onLine("Patching ${sourceFile.name}")
        runCommand(listOf(ksud.absolutePath) + ksudArgs, wd, displayCommand) { onLine(it) }.getOrThrow()

        val patched = findPatchedImage(wd) ?: error("Patched image not found after ksud finished")
        val dest = if (!outPath.isNullOrBlank()) {
            val target = File(outPath).let { if (it.isDirectory) File(it, patched.name) else it }
            target.parentFile?.mkdirs()
            patched.inputStream().use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            target.absolutePath
        } else {
            exportPatchedImage(patched).getOrThrow()
        }
        onLine("Patched image written to $dest")
        Unit
    }

    fun exportPatchedImage(sourceFile: File): Result<String> = runCatching {
        val fileName = sourceFile.name
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = app.contentResolver
            var uri: Uri? = null
            try {
                uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: error("Failed to create destination in Downloads")
                resolver.openOutputStream(uri).use { out ->
                    requireNotNull(out) { "Failed to open Downloads output stream" }
                    sourceFile.inputStream().use { input -> input.copyTo(out) }
                }
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                uri.toString()
            } catch (error: Throwable) {
                uri?.let { resolver.delete(it, null, null) }
                throw error
            }
        } else {
            val downloads = app.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: File(app.filesDir, "exports")
            if (!downloads.exists()) downloads.mkdirs()
            val target = File(downloads, fileName)
            sourceFile.copyTo(target, overwrite = true)
            target.absolutePath
        }
    }

    suspend fun hasInitBoot(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val props = ProcessBuilder("getprop").start().inputStream.bufferedReader().use { it.readText() }
            if (props.contains("init_boot", ignoreCase = true)) return@runCatching true
            val release = android.system.Os.uname().release
            val parts = release.substringBefore("-").split(".")
            val major = parts.getOrNull(0)?.toIntOrNull() ?: 0
            val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
            // >= 5.15 kernels have init_boot
            major > 5 || (major == 5 && minor >= 15)
        }.getOrDefault(false)
    }

    suspend fun runFlashKernel(
        imagePath: String,
        onLine: (String) -> Unit,
        onShell: (String) -> Unit = onLine,
        onPhase: (OtaPhase) -> Unit,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            onPhase(OtaPhase.CHECKING_ROOT)
            if (!RootShell.isRooted()) {
                try { RootShell.run("true") } catch (_: Throwable) {
                    onPhase(OtaPhase.NO_ROOT)
                    onLine("Root access denied.")
                    error("root access denied")
                }
            }
            onLine("Root access granted.")

            onPhase(OtaPhase.READING_SLOT)
            val currentSlot = RootShell.getProp("ro.boot.slot_suffix") ?: ""
            onLine("Current slot: $currentSlot")

            val src = File(imagePath).canonicalFile
            if (!src.exists()) error("${src.absolutePath} does not exist")

            onPhase(OtaPhase.PATCHING)

            val bb = resolveBundledBinary("libbusybox.so")
            bb.setExecutable(true, false)
            val bbPath = bb.absolutePath

            val ts = System.currentTimeMillis()
            val tmpDir = File(app.cacheDir, "ak3_$ts")
            tmpDir.mkdirs()
            val zipCopy = File(tmpDir, "anykernel.zip")
            src.inputStream().use { it.copyTo(zipCopy.outputStream()) }

            val hasInstaller = runCatching {
                java.util.zip.ZipFile(zipCopy).use { z ->
                    z.getEntry("META-INF/com/google/android/update-binary") != null
                }
            }.getOrDefault(false)

            if (!hasInstaller) {
                tmpDir.deleteRecursively()
                error("Invalid AnyKernel3 zip: update-binary not found")
            }

            val cmd = buildString {
                append("$bbPath unzip -p -o '").append(zipCopy.absolutePath)
                append("' 'META-INF/com/google/android/update-binary' > '")
                append(tmpDir.absolutePath).append("/update-binary' 2>/dev/null\n")
                append("$bbPath chmod 755 '").append(tmpDir.absolutePath).append("/update-binary'\n")
                append("cd '").append(tmpDir.absolutePath).append("'\n")
                append("AKHOME='").append(tmpDir.absolutePath).append("/tmp' ")
                append("$bbPath ash ./update-binary 3 1 '").append(zipCopy.absolutePath).append("'")
            }
            onLine("$ busybox ash update-binary ...")
            RootShell.runStreaming(cmd, onShell)

            runCatching { RootShell.run("rm -rf '${tmpDir.absolutePath}'") }

            onPhase(OtaPhase.DONE)
            Unit
        }
    }

    suspend fun backupBoot(
        onLine: (String) -> Unit,
        onPhase: (OtaPhase) -> Unit,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            onPhase(OtaPhase.CHECKING_ROOT)
            if (!RootShell.isRooted()) {
                try { RootShell.run("true") } catch (_: Throwable) {
                    onPhase(OtaPhase.NO_ROOT)
                    onLine("Root access denied.")
                    error("root access denied")
                }
            }
            onLine("Root access granted.")

            onPhase(OtaPhase.READING_SLOT)
            val currentSlot = RootShell.getProp("ro.boot.slot_suffix") ?: ""
            onLine("Current slot: $currentSlot")

            val targetPartition = try {
                val hasInitBoot = RootShell.run("[ -e /dev/block/by-name/init_boot$currentSlot ] && echo yes || echo no").trim()
                if (hasInitBoot == "yes") "init_boot" else "boot"
            } catch (_: Throwable) { "boot" }

            val srcDev = "/dev/block/by-name/$targetPartition$currentSlot"
            onLine("Source: $srcDev")

            val isBlock = RootShell.run("[ -b '$srcDev' ] && echo yes || echo no").trim()
            if (isBlock != "yes") error("$srcDev is not a block device")

            onPhase(OtaPhase.PATCHING)
            onLine("Backing up $targetPartition to backup file...")

            val out = RootShell.run("dd if='$srcDev' of='${File(app.cacheDir, "boot_backup_tmp.img").absolutePath}' bs=1M")
            onLine(out)

            val tmpFile = File(app.cacheDir, "boot_backup_tmp.img")
            val exportResult = exportPatchedImage(tmpFile).getOrThrow()
            tmpFile.delete()

            onLine("Backup saved to $exportResult")
            onPhase(OtaPhase.DONE)
            exportResult
        }
    }
}
