package org.akuatech.ksupatcher.viewmodel

import android.content.ActivityNotFoundException
import android.app.Application
import android.content.ClipData
import android.content.Intent
import org.akuatech.ksupatcher.BuildConfig
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.documentfile.provider.DocumentFile
import androidx.core.content.FileProvider
import org.akuatech.ksupatcher.data.AppUpdateInfo
import org.akuatech.ksupatcher.data.SettingsRepository
import org.akuatech.ksupatcher.data.UpdateConfig
import org.akuatech.ksupatcher.network.DownloadRepository
import org.akuatech.ksupatcher.network.GitHubReleaseRepository
import org.akuatech.ksupatcher.root.RootShell
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import android.net.Uri
import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import java.security.MessageDigest
import java.util.Locale
import java.time.Instant

enum class KsuVariant { KSU, KSUN }
enum class InstallMethod { PATCH, LKM }
enum class RootStatus { GRANTED, NOT_GRANTED, UNKNOWN }

enum class OtaPhase {
    IDLE,
    CHECKING_ROOT,
    NO_ROOT,
    CHECKING_OTA_PROP,
    NO_OTA_PENDING,
    READING_SLOT,
    DUMPING_BOOT,
    PATCHING,
    FLASHING,
    DONE,
    ERROR
}

data class OtaState(
    val phase: OtaPhase = OtaPhase.IDLE,
    val log: String = "",
    val currentSlot: String? = null,       // a or b
    val nextSlot: String? = null,          // the opposite slot
    val rebootRequired: Boolean = false,
    val isLkmMode: Boolean = false         // if true den update lkm on current slot instead of ota
)

data class UiState(
    val isCheckingVersion: Boolean = false,
    val isUpdatingApp: Boolean = false,
    val appUpdateProgress: Int = 0,
    val appUpdateStatus: String? = null,
    val appUpdateError: String? = null,
    val appUpdateInfo: AppUpdateInfo? = null,
    val versionError: String? = null,
    val lastVersionCheck: String? = null,
    val patchState: PatchState = PatchState(),
    val otaState: OtaState = OtaState(),
    val rootStatus: RootStatus = RootStatus.UNKNOWN,
    val isCheckingRoot: Boolean = false,
    val themeMode: String = "auto"
)

data class PatchState(
    val variant: KsuVariant = KsuVariant.KSU,
    val method: InstallMethod = InstallMethod.PATCH,
    val bootImageName: String? = null,
    val bootImagePath: String? = null,
    val moduleName: String? = null,
    val modulePath: String? = null,
    val isPatching: Boolean = false,
    val status: String? = null,
    val lastOutput: String? = null,
    val outputPath: String? = null,
    val rebootRequired: Boolean = false,
    val kmi: String = "android12-5.10"
)

class MainViewModel(
    application: Application,
    private val downloadRepository: DownloadRepository,
    private val settingsRepository: SettingsRepository,
    private val releaseRepository: GitHubReleaseRepository
) : AndroidViewModel(application) {

    constructor(application: Application) : this(
        application = application,
        downloadRepository = DownloadRepository(httpClient),
        settingsRepository = SettingsRepository(application),
        releaseRepository = GitHubReleaseRepository(httpClient)
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    init {
        viewModelScope.launch {
            settingsRepository.rootStatusFlow.collect { statusStr ->
                val status = try { RootStatus.valueOf(statusStr) } catch (_: Exception) { RootStatus.UNKNOWN }
                _state.update { it.copy(rootStatus = status) }
            }
        }
        viewModelScope.launch {
            settingsRepository.kmiFlow.collect { kmi ->
                _state.update { it.copy(patchState = it.patchState.copy(kmi = kmi)) }
            }
        }
        viewModelScope.launch {
            settingsRepository.themeModeFlow.collect { mode ->
                _state.update { it.copy(themeMode = mode) }
            }
        }
        viewModelScope.launch {
            settingsRepository.lastVersionCheckFlow.collect { timestamp ->
                _state.update { it.copy(lastVersionCheck = timestamp) }
            }
        }
        refreshRootStatus()
        refreshVersion(isAutoCheck = true)
    }

    fun refreshRootStatus() {
        _state.update { it.copy(isCheckingRoot = true) }
        viewModelScope.launch(Dispatchers.IO) {
            val isRooted = RootShell.isRooted()
            val status = if (isRooted) RootStatus.GRANTED else RootStatus.NOT_GRANTED
            settingsRepository.setRootStatus(status.name)
            _state.update { it.copy(isCheckingRoot = false) }
        }
    }

    fun refreshVersion(isAutoCheck: Boolean = false) {
        _state.update { it.copy(isCheckingVersion = true, versionError = null, appUpdateError = null) }
        viewModelScope.launch {
            val currentBuildHash = BuildConfig.VERSION_NAME.trim()
            val result = releaseRepository.fetchAppUpdateInfo(
                owner = UpdateConfig.appOwner,
                repo = UpdateConfig.appRepo,
                currentBuildHash = currentBuildHash
            )
            val timestamp = Instant.now().toString()
            settingsRepository.setLastVersionCheck(timestamp)
            _state.update { current ->
                val error = if (isAutoCheck) null else result.exceptionOrNull()?.message
                current.copy(
                    isCheckingVersion = false,
                    appUpdateInfo = result.getOrNull(),
                    versionError = error,
                    lastVersionCheck = timestamp
                )
            }
        }
    }

    fun installAppUpdate() {
        val updateInfo = _state.value.appUpdateInfo
        if (updateInfo == null || !updateInfo.isUpdateAvailable) {
            _state.update {
                it.copy(appUpdateError = "No update available")
            }
            return
        }

        val apkUrl = updateInfo.apkDownloadUrl
        val checksumUrl = updateInfo.checksumDownloadUrl
        val apkName = updateInfo.apkAssetName

        if (apkUrl.isNullOrBlank() || checksumUrl.isNullOrBlank() || apkName.isNullOrBlank()) {
            _state.update {
                it.copy(appUpdateError = "Release assets are incomplete")
            }
            return
        }

        viewModelScope.launch {
            _state.update {
                it.copy(
                    isUpdatingApp = true,
                    appUpdateProgress = 0,
                    appUpdateStatus = "Downloading update...",
                    appUpdateError = null
                )
            }

            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val updateDir = getAppUpdateDir()
                    validateSafeFileName(apkName)
                    val apkFile = File(updateDir, apkName)
                    val checksumFile = File(updateDir, "$apkName.sha256")

                    downloadRepository.download(apkUrl, apkFile) { progress ->
                        _state.update {
                            it.copy(
                                appUpdateProgress = progress,
                                appUpdateStatus = "Downloading update..."
                            )
                        }
                    }.getOrThrow()

                    _state.update {
                        it.copy(appUpdateStatus = "Downloading checksum...")
                    }
                    val checksumText = downloadRepository.downloadText(checksumUrl).getOrThrow()
                    checksumFile.writeText(checksumText)

                    _state.update {
                        it.copy(appUpdateStatus = "Verifying integrity...")
                    }
                    val expectedSha256 = parseChecksum(checksumText, apkName)
                    val actualSha256 = computeSha256(apkFile)
                    if (!actualSha256.equals(expectedSha256, ignoreCase = true)) {
                        apkFile.delete()
                        checksumFile.delete()
                        error("Integrity check failed")
                    }

                    launchPackageInstaller(apkFile)
                }
            }

            _state.update {
                it.copy(
                    isUpdatingApp = false,
                    appUpdateProgress = if (result.isSuccess) 100 else 0,
                    appUpdateStatus = if (result.isSuccess) "Integrity verified. Installer opened." else null,
                    appUpdateError = result.exceptionOrNull()?.message
                )
            }
        }
    }

    fun updateKmi(value: String) {
        viewModelScope.launch {
            settingsRepository.setKmi(value)
        }
    }

    fun setThemeMode(mode: String) {
        viewModelScope.launch {
            settingsRepository.setThemeMode(mode)
        }
    }

    fun selectVariant(variant: KsuVariant) {
        _state.update { it.copy(patchState = it.patchState.copy(variant = variant)) }
    }

    fun selectMethod(method: InstallMethod) {
        _state.update { it.copy(patchState = it.patchState.copy(method = method)) }
    }

    fun importBootImage(uri: Uri) {
        viewModelScope.launch {
            val result = copyUriToWorkDir(uri, "boot.img")
            _state.update {
                val patch = it.patchState
                it.copy(
                    patchState = patch.copy(
                        bootImageName = result.getOrNull()?.second,
                        bootImagePath = result.getOrNull()?.first,
                        status = result.exceptionOrNull()?.message
                    )
                )
            }
        }
    }

    fun importModule(uri: Uri) {
        viewModelScope.launch {
            val result = copyUriToWorkDir(uri, "kernelsu.ko")
            _state.update {
                val patch = it.patchState
                it.copy(
                    patchState = patch.copy(
                        moduleName = result.getOrNull()?.second,
                        modulePath = result.getOrNull()?.first,
                        status = result.exceptionOrNull()?.message
                    )
                )
            }
        }
    }


    fun runPatch() {
        val workDir = getWorkDir()
        val boot = _state.value.patchState.bootImagePath
        val kmi = _state.value.patchState.kmi

        if (boot.isNullOrBlank()) {
            _state.update {
                it.copy(patchState = it.patchState.copy(status = "Please select boot.img"))
            }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(patchState = it.patchState.copy(isPatching = true, status = "Downloading binaries...")) }
            val prepare = ensureBinaries()
            if (prepare.isFailure) {
                _state.update {
                    it.copy(
                        patchState = it.patchState.copy(
                            isPatching = false,
                            status = prepare.exceptionOrNull()?.message ?: "Failed to prepare binaries"
                        )
                    )
                }
                return@launch
            }

            val ksud = resolveBundledBinaryForVariant(_state.value.patchState.variant)
            val magiskboot = resolveBundledBinary("libmagiskboot.so")

            _state.update {
                it.copy(
                    patchState = it.patchState.copy(
                        status = "Patching boot image..."
                    )
                )
            }

            val module = _state.value.patchState.modulePath
            if (module.isNullOrBlank()) {
                _state.update {
                    it.copy(
                        patchState = it.patchState.copy(
                            isPatching = false,
                            status = "Failed to download kernel module"
                        )
                    )
                }
                return@launch
            }

            val command = listOf(
                ksud.absolutePath,
                "boot-patch",
                "-b",
                boot,
                "--kmi",
                kmi,
                "--magiskboot",
                magiskboot.absolutePath,
                "--module",
                module,
                "-o",
                workDir.absolutePath
            )

            val result = executeCommandStreaming(command, workDir, _state.value.patchState.lastOutput)
            val patchedFile = if (result.isSuccess) findPatchedImage(workDir) else null
            val saveResult = if (result.isSuccess && patchedFile != null) {
                exportPatchedImage(patchedFile)
            } else {
                Result.failure(IllegalStateException("Patched image not found in work dir"))
            }

            val statusText = if (result.isSuccess) {
                if (saveResult.isSuccess) {
                    "Patch completed and exported"
                } else {
                    "Patch completed (export failed)"
                }
            } else {
                "Patch failed"
            }

            val finalOutput = buildString {
                append(result.getOrNull() ?: result.exceptionOrNull()?.message.orEmpty())
                if (result.isSuccess) {
                    append("\n\n")
                    if (saveResult.isSuccess) {
                        append("Exported to: ${saveResult.getOrNull()}")
                    } else {
                        append("Export failed: ${saveResult.exceptionOrNull()?.message}")
                    }
                }
            }

            _state.update {
                it.copy(
                    patchState = it.patchState.copy(
                        isPatching = false,
                        status = statusText,
                        lastOutput = finalOutput,
                        outputPath = saveResult.getOrNull() ?: patchedFile?.absolutePath
                    )
                )
            }
        }
    }

    private fun findPatchedImage(workDir: File): File? {
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

    private fun exportPatchedImage(sourceFile: File): Result<String> = runCatching {
        val context = getApplication<Application>()
        val fileName = sourceFile.name

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }

            val resolver = context.contentResolver
            var uri: Uri? = null
            try {
                uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: error("Failed to create destination in Downloads")

                resolver.openOutputStream(uri).use { out ->
                    requireNotNull(out) { "Failed to open Downloads output stream" }
                    sourceFile.inputStream().use { input ->
                        input.copyTo(out)
                    }
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
            val downloads = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: File(context.filesDir, "exports")
            if (!downloads.exists()) {
                downloads.mkdirs()
            }
            val target = File(downloads, fileName)
            runCatching { sourceFile.copyTo(target, overwrite = true) }
                .onFailure { target.delete() }
                .getOrThrow()
            target.absolutePath
        }
    }

    private fun getAppUpdateDir(): File {
        val dir = File(getApplication<Application>().cacheDir, "updates")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    private fun getWorkDir(): File {
        val dir = File(getApplication<Application>().codeCacheDir, "work")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    private suspend fun ensureBinaries(): Result<Unit> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val context = getApplication<Application>()
                val workDir = getWorkDir()

                val legacyWorkDir = File(context.filesDir, "work")
                File(legacyWorkDir, "ksud").delete()
                File(legacyWorkDir, "magiskboot").delete()

                val ksud = resolveBundledBinaryForVariant(_state.value.patchState.variant)
                val magiskboot = resolveBundledBinary("libmagiskboot.so")
                ksud.setExecutable(true, false)
                magiskboot.setExecutable(true, false)

                if (!ksud.canExecute() || !magiskboot.canExecute()) {
                    error(
                        "Bundled binaries are not executable. " +
                            "ksud=${ksud.absolutePath} canExec=${ksud.canExecute()}, " +
                            "magiskboot=${magiskboot.absolutePath} canExec=${magiskboot.canExecute()}"
                    )
                }

                if (_state.value.patchState.modulePath.isNullOrBlank()) {
                    val tag = releaseRepository.fetchLatestTag(UpdateConfig.ksuLkmOwner, UpdateConfig.ksuLkmRepo).getOrThrow()
                    val asset = when (_state.value.patchState.variant) {
                        KsuVariant.KSU -> UpdateConfig.ksuModuleAsset
                        KsuVariant.KSUN -> UpdateConfig.ksunModuleAsset
                    }
                    val moduleFile = File(workDir, asset)
                    val url = "https://github.com/${UpdateConfig.ksuLkmOwner}/${UpdateConfig.ksuLkmRepo}/releases/download/${tag}/${asset}"
                    downloadRepository.download(url, moduleFile) { }.getOrThrow()
                    _state.update {
                        it.copy(
                            patchState = it.patchState.copy(
                                moduleName = asset,
                                modulePath = moduleFile.absolutePath
                            )
                        )
                    }
                }

                Unit
            }
        }
    }

    private fun resolveBundledBinary(fileName: String): File {
        val context = getApplication<Application>()
        val nativeLibDir = File(context.applicationInfo.nativeLibraryDir)
        val file = File(nativeLibDir, fileName)
        if (!file.exists()) {
            val available = nativeLibDir.listFiles()?.joinToString(",") { it.name } ?: "none"
            error("Bundled binary not found: ${file.absolutePath}. Available: $available")
        }
        return file
    }

    private fun resolveBundledBinaryForVariant(variant: KsuVariant): File {
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

    private fun parseChecksum(content: String, apkName: String): String {
        val line = content
            .lineSequence()
            .map(String::trim)
            .firstOrNull { it.isNotEmpty() }
            ?: error("Checksum file is empty")

        val parts = line.split(Regex("\\s+"), limit = 2)
        val hash = parts.firstOrNull()?.trim().orEmpty()
        if (hash.length != 64) {
            error("Checksum file is invalid")
        }

        if (parts.size == 2) {
            val fileName = parts[1].removePrefix("*").trim()
            if (fileName.isNotEmpty() && fileName != apkName) {
                error("Checksum file does not match the APK")
            }
        }

        return hash
    }

    private fun validateSafeFileName(fileName: String) {
        if (fileName != File(fileName).name || fileName.contains('/') || fileName.contains('\\')) {
            error("Release file name is invalid")
        }
    }

    private fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun launchPackageInstaller(apkFile: File) {
        val context = getApplication<Application>()
        val apkUri = FileProvider.getUriForFile(
            context,
            "${BuildConfig.APPLICATION_ID}.fileprovider",
            apkFile
        )
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            clipData = ClipData.newRawUri(apkFile.name, apkUri)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            context.startActivity(installIntent)
        } catch (_: ActivityNotFoundException) {
            error("No package installer available")
        }
    }

    private suspend fun executeCommandStreaming(
        command: List<String>, 
        workDir: File, 
        initialLog: String? = null
    ): Result<String> {
        return withContext(Dispatchers.IO) {
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

                val reader = process.inputStream.bufferedReader()
                val sb = StringBuilder()
                if (!initialLog.isNullOrBlank()) {
                    appendTrimmed(sb, initialLog)
                    appendTrimmed(sb, "\n\n")
                }
                
                val binaryName = File(command.first()).name
                val simplifiedBinary = binaryName.replace(Regex("^lib"), "").replace(Regex("\\.so$"), "")
                val prettyCommand = "$ $simplifiedBinary ${command.drop(1).joinToString(" ")}"
                appendTrimmed(sb, prettyCommand)
                appendTrimmed(sb, "\n")
                publishStreamingLog(sb.toString(), updatePatch = true)

                var pendingLines = 0
                var lastEmitAt = SystemClock.elapsedRealtime()

                try {
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        appendTrimmed(sb, line.orEmpty())
                        appendTrimmed(sb, "\n")
                        pendingLines += 1

                        val now = SystemClock.elapsedRealtime()
                        if (pendingLines >= STREAM_LOG_EMIT_LINES || now - lastEmitAt >= STREAM_LOG_EMIT_INTERVAL_MS) {
                            publishStreamingLog(sb.toString(), updatePatch = true)
                            pendingLines = 0
                            lastEmitAt = now
                        }
                    }
                } finally {
                    reader.close()
                }

                val exitCode = process.waitFor()
                val output = sb.toString()
                publishStreamingLog(output, updatePatch = true)
                if (exitCode != 0) {
                    error("Exit $exitCode\n$output")
                }
                output
            }
        }
    }

    private fun appendTrimmed(builder: StringBuilder, text: String) {
        builder.append(text)
        val overflow = builder.length - MAX_LOG_CHARS
        if (overflow > 0) {
            builder.delete(0, overflow)
        }
    }

    private fun publishStreamingLog(log: String, updatePatch: Boolean) {
        _state.update { state ->
            val trimmed = if (log.length > MAX_LOG_CHARS) log.takeLast(MAX_LOG_CHARS) else log
            val patch = if (updatePatch) state.patchState.copy(lastOutput = trimmed) else state.patchState
            val ota = if (state.otaState.phase != OtaPhase.IDLE && !state.otaState.isLkmMode) {
                state.otaState.copy(log = trimmed)
            } else {
                state.otaState
            }
            state.copy(patchState = patch, otaState = ota)
        }
    }

    private suspend fun copyUriToWorkDir(uri: Uri, defaultName: String): Result<Pair<String, String>> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val context = getApplication<Application>()
                val name = DocumentFile.fromSingleUri(context, uri)?.name ?: defaultName
                val target = File(getWorkDir(), defaultName)
                context.contentResolver.openInputStream(uri).use { input ->
                    requireNotNull(input) { "Unable to open selected file" }
                    target.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                target.absolutePath to name
            }
        }
    }

    fun resetOta() {
        _state.update { it.copy(otaState = OtaState()) }
    }

    fun resetInstall() {
        _state.update {
            it.copy(
                patchState = it.patchState.copy(
                    lastOutput = null,
                    status = null,
                    bootImageName = null,
                    bootImagePath = null,
                    moduleName = null,
                    modulePath = null,
                    isPatching = false,
                    rebootRequired = false
                )
            )
        }
    }

    fun runOtaPatch() {
        viewModelScope.launch { executeOtaFlow(lkmMode = false) }
    }

    fun runLkmUpdate() {
        viewModelScope.launch { executeOtaFlow(lkmMode = true) }
    }

    private suspend fun executeOtaFlow(lkmMode: Boolean) {
        fun appendLog(msg: String) {
            _state.update { state ->
                val sb = StringBuilder()
                val existing = if (lkmMode) state.patchState.lastOutput else state.otaState.log
                if (!existing.isNullOrBlank()) {
                    appendTrimmed(sb, existing)
                    appendTrimmed(sb, "\n")
                }
                appendTrimmed(sb, msg)
                val output = sb.toString()

                if (lkmMode) {
                    state.copy(patchState = state.patchState.copy(lastOutput = output))
                } else {
                    state.copy(otaState = state.otaState.copy(log = output))
                }
            }
        }
        fun setPhase(p: OtaPhase) {
            _state.update { state ->
                if (lkmMode) {
                    // In LKM mode, we don't set OTA phase to keep OTA screen idle
                    state
                } else {
                    state.copy(otaState = state.otaState.copy(phase = p))
                }
            }
        }

        try {
            _state.update { state ->
                if (lkmMode) {
                    state.copy(
                        patchState = state.patchState.copy(
                            isPatching = true,
                            status = "Preparing LKM update..."
                        )
                    )
                } else {
                    state.copy(
                        otaState = OtaState(phase = OtaPhase.CHECKING_ROOT, isLkmMode = false)
                    )
                }
            }

            if (!RootShell.isRooted()) {
                val granted = withContext(Dispatchers.IO) {
                    try {
                        RootShell.run("true")
                        true
                    } catch (_: Throwable) { false }
                }
                if (!granted) {
                    setPhase(OtaPhase.NO_ROOT)
                    if (lkmMode) {
                        _state.update { it.copy(patchState = it.patchState.copy(status = "Root access denied")) }
                        appendLog("Root access denied. Please grant root permission to this app.")
                    }
                    return
                }
            }
            appendLog("Root access granted.")
            val variantName = if (_state.value.patchState.variant == KsuVariant.KSUN) "KernelSU-Next" else "KernelSU"
            appendLog("Target variant: $variantName")
            if (lkmMode) {
                _state.update { it.copy(patchState = it.patchState.copy(status = "Root access granted ($variantName)")) }
            }

            if (!lkmMode) {
                setPhase(OtaPhase.CHECKING_OTA_PROP)
                appendLog("$ getprop ota.other.vbmeta_digest")
                val otaProp = try {
                    RootShell.getProp("ota.other.vbmeta_digest")
                } catch (e: Throwable) {
                    setPhase(OtaPhase.ERROR)
                    appendLog("Error reading prop: ${e.message}")
                    return
                }
                if (!otaProp.isNullOrBlank()) {
                    appendLog(otaProp)
                }
                if (otaProp.isNullOrBlank()) {
                    setPhase(OtaPhase.NO_OTA_PENDING)
                    appendLog("No OTA update is pending (ota.other.vbmeta_digest is empty).")
                    appendLog("Apply an OTA update first, then come back here before rebooting.")
                    return
                }
                appendLog("OTA detected. vbmeta_digest = $otaProp")
            }

            setPhase(OtaPhase.READING_SLOT)
            appendLog("$ getprop ro.boot.slot_suffix")
            val currentSlot = try {
                RootShell.getProp("ro.boot.slot_suffix") ?: error("ro.boot.slot_suffix returned empty")
            } catch (e: Throwable) {
                setPhase(OtaPhase.ERROR)
                if (lkmMode) _state.update { it.copy(patchState = it.patchState.copy(status = "Failed to read slot")) }
                appendLog("Failed to read slot: ${e.message}")
                return
            }
            appendLog(currentSlot)
            val nextSlot = if (lkmMode) currentSlot else (if (currentSlot == "_a") "_b" else "_a")
            _state.update {
                it.copy(otaState = it.otaState.copy(currentSlot = currentSlot, nextSlot = nextSlot))
            }
            val targetSlot = nextSlot  // slot whose boot partition we touch
            appendLog("Current slot: $currentSlot  →  target slot: $targetSlot")

            setPhase(OtaPhase.DUMPING_BOOT)
            val workDir = getWorkDir()
            val dumpedImg = File(workDir, "next-boot.img")

            // prefer init_boot over boot if it exists
            val initBootDevice = "/dev/block/by-name/init_boot$targetSlot"
            val bootDevice = "/dev/block/by-name/boot$targetSlot"
            val blockDevice = try {
                val hasInitBoot = RootShell.run("[ -e $initBootDevice ] && echo yes || echo no").trim()
                if (hasInitBoot == "yes") {
                    appendLog("init_boot partition detected.")
                    initBootDevice
                } else {
                    bootDevice
                }
            } catch (e: Throwable) {
                appendLog("Fallback: ${e.message}")
                bootDevice
            }

            appendLog("$ dd if=$blockDevice of=${dumpedImg.absolutePath} bs=4096")
            try {
                RootShell.run("dd if=$blockDevice of=${dumpedImg.absolutePath} bs=4096")
            } catch (e: Throwable) {
                setPhase(OtaPhase.ERROR)
                if (lkmMode) _state.update { it.copy(patchState = it.patchState.copy(status = "Dump failed")) }
                appendLog("DD (dump) failed: ${e.message}")
                return
            }
            appendLog("Boot image dumped (${dumpedImg.length() / 1024} KB).")

            setPhase(OtaPhase.PATCHING)
            val binaryPrepare = ensureBinaries()
            if (binaryPrepare.isFailure) {
                setPhase(OtaPhase.ERROR)
                if (lkmMode) _state.update { it.copy(patchState = it.patchState.copy(status = "Binary prep failed")) }
                appendLog("Binary preparation failed: ${binaryPrepare.exceptionOrNull()?.message}")
                return
            }
            val ksud = resolveBundledBinaryForVariant(_state.value.patchState.variant)
            val magiskboot = resolveBundledBinary("libmagiskboot.so")
            val module = _state.value.patchState.modulePath
            if (module.isNullOrBlank()) {
                setPhase(OtaPhase.ERROR)
                if (lkmMode) _state.update { it.copy(patchState = it.patchState.copy(status = "No module available")) }
                appendLog("No kernel module found. Please select one manually or ensure your internet connection is active to auto-download.")
                return
            }
            val patchCmd = listOf(
                ksud.absolutePath,
                "boot-patch",
                "-b", dumpedImg.absolutePath,
                "--kmi", _state.value.patchState.kmi,
                "--magiskboot", magiskboot.absolutePath,
                "--module", module,
                "-o", workDir.absolutePath
            )
            appendLog("Patching boot image...")
            val initialLog = if (lkmMode) _state.value.patchState.lastOutput else _state.value.otaState.log
            val patchResult = executeCommandStreaming(patchCmd, workDir, initialLog)
            if (patchResult.isFailure) {
                setPhase(OtaPhase.ERROR)
                if (lkmMode) _state.update { it.copy(patchState = it.patchState.copy(status = "Patch failed")) }
                appendLog("Patch failed: ${patchResult.exceptionOrNull()?.message}")
                return
            }
            val patchedImg = findPatchedImage(workDir)
            if (patchedImg == null) {
                setPhase(OtaPhase.ERROR)
                if (lkmMode) _state.update { it.copy(patchState = it.patchState.copy(status = "Image not found")) }
                appendLog("Patched image not found in work directory.")
                return
            }
            appendLog("Patched image: ${patchedImg.name} (${patchedImg.length() / 1024} KB)")

            setPhase(OtaPhase.FLASHING)
            appendLog("Flashing to partition: $blockDevice...")
            try {
                // harden block device access
                val roCheck = RootShell.run("blockdev --getro $blockDevice").trim()
                if (roCheck == "1") {
                    appendLog("$ blockdev --setrw $blockDevice")
                    RootShell.run("blockdev --setrw $blockDevice")
                }
                
                appendLog("$ dd if=${patchedImg.absolutePath} of=$blockDevice bs=4096")
                RootShell.run("dd if=${patchedImg.absolutePath} of=$blockDevice bs=4096")
            } catch (e: Throwable) {
                setPhase(OtaPhase.ERROR)
                if (lkmMode) _state.update { it.copy(patchState = it.patchState.copy(status = "Flash failed")) }
                appendLog("DD (flash) failed: ${e.message}")
                return
            }

            setPhase(OtaPhase.DONE)
            if (!lkmMode) {
                appendLog("$ bootctl set-active-boot-slot ${targetSlot.replace("_", "")}")
                try {
                    RootShell.run("bootctl set-active-boot-slot ${targetSlot.replace("_", "")}")
                } catch (e: Throwable) {
                    appendLog("Warning: Failed to switch slot automatically: ${e.message}")
                }
            }
            
            _state.update {
                if (lkmMode) {
                    it.copy(
                        patchState = it.patchState.copy(
                            status = "Installed successfully",
                            rebootRequired = true
                        )
                    )
                } else {
                    it.copy(
                        otaState = it.otaState.copy(rebootRequired = true),
                        patchState = it.patchState.copy(status = "Installed successfully")
                    )
                }
            }
            appendLog(
                if (lkmMode)
                    "LKM update complete. ✓  Safe to reboot."
                else
                    "OTA root patch complete. ✓  Please reboot to boot into the updated slot with root preserved."
            )
        } catch (e: Throwable) {
            setPhase(OtaPhase.ERROR)
            if (lkmMode) _state.update { it.copy(patchState = it.patchState.copy(status = "Unexpected error")) }
            appendLog("Unexpected error in flow: ${e.message}")
        } finally {
            if (lkmMode) {
                _state.update { 
                    it.copy(patchState = it.patchState.copy(isPatching = false))
                }
            }
        }
    }

    fun rebootNow() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { RootShell.run("svc power reboot") }
        }
    }

    private companion object {
        val httpClient = okhttp3.OkHttpClient()
        const val MAX_LOG_CHARS = 64_000
        const val STREAM_LOG_EMIT_LINES = 8
        const val STREAM_LOG_EMIT_INTERVAL_MS = 200L
    }
}
