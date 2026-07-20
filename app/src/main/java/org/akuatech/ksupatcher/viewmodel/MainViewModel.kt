package org.akuatech.ksupatcher.viewmodel

import android.content.ActivityNotFoundException
import android.app.Application
import android.content.ClipData
import android.content.Intent
import android.provider.Settings
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
import org.akuatech.ksupatcher.util.RomZipExtractor
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
    PATCHING,
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
    val themeMode: String = "auto",
    val showDisclaimer: Boolean = true,
    val showInstallPermissionRationale: Boolean = false
)

data class PatchState(
    val variant: KsuVariant = KsuVariant.KSU,
    val method: InstallMethod = InstallMethod.PATCH,
    val bootImageName: String? = null,
    val bootImagePath: String? = null,
    val pendingRomZipUri: Uri? = null,
    val moduleName: String? = null,
    val modulePath: String? = null,
    val isPatching: Boolean = false,
    val status: String? = null,
    val lastOutput: String? = null,
    val outputPath: String? = null,
    val rebootRequired: Boolean = false,
    val kmi: String = "android12-5.10",
    val allowShell: Boolean = false,
    val enableAdbd: Boolean = false
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

    private val engine = KsuEngine(application, downloadRepository, releaseRepository)

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
        viewModelScope.launch {
            settingsRepository.disclaimerAcceptedFlow.collect { accepted ->
                _state.update { it.copy(showDisclaimer = !accepted) }
            }
        }
        refreshRootStatus()
        refreshVersion(isAutoCheck = true)
    }

    fun acceptDisclaimer() {
        viewModelScope.launch {
            settingsRepository.setDisclaimerAccepted()
        }
    }

    fun dismissInstallPermissionRationale() {
        _state.update { it.copy(showInstallPermissionRationale = false) }
    }

    fun openInstallPermissionSettings() {
        val context = getApplication<Application>()
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            data = Uri.parse("package:${BuildConfig.APPLICATION_ID}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        _state.update { it.copy(showInstallPermissionRationale = false) }
    }

    fun refreshRootStatus() {
        _state.update { it.copy(isCheckingRoot = true) }
        viewModelScope.launch {
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
        val context = getApplication<Application>()
        if (!context.packageManager.canRequestPackageInstalls()) {
            _state.update { it.copy(showInstallPermissionRationale = true) }
            return
        }
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

    fun toggleAllowShell(enabled: Boolean) {
        _state.update { it.copy(patchState = it.patchState.copy(allowShell = enabled)) }
    }

    fun toggleEnableAdbd(enabled: Boolean) {
        _state.update { it.copy(patchState = it.patchState.copy(enableAdbd = enabled)) }
    }

    fun importBootImage(uri: Uri) {
        viewModelScope.launch {
            val context = getApplication<Application>()
            val displayName = withContext(Dispatchers.IO) {
                DocumentFile.fromSingleUri(context, uri)?.name
            }
            val isZip = displayName?.endsWith(".zip", ignoreCase = true) == true ||
                withContext(Dispatchers.IO) { RomZipExtractor.isLikelyZip(context, uri) }

            if (isZip) {
                _state.update {
                    it.copy(
                        patchState = it.patchState.copy(
                            bootImageName = displayName ?: "rom.zip",
                            bootImagePath = null,
                            pendingRomZipUri = uri,
                            status = null
                        )
                    )
                }
                return@launch
            }

            val result = copyUriToWorkDir(uri, "boot.img")
            _state.update {
                val patch = it.patchState
                it.copy(
                    patchState = patch.copy(
                        bootImageName = result.getOrNull()?.second,
                        bootImagePath = result.getOrNull()?.first,
                        pendingRomZipUri = null,
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
        val initialBoot = _state.value.patchState.bootImagePath
        val kmi = _state.value.patchState.kmi
        val pendingZip = _state.value.patchState.pendingRomZipUri

        if (initialBoot.isNullOrBlank() && pendingZip == null) {
            _state.update {
                it.copy(patchState = it.patchState.copy(status = "Please select boot.img or rom.zip"))
            }
            return
        }

        viewModelScope.launch {
            _state.update {
                it.copy(
                    patchState = it.patchState.copy(
                        isPatching = true,
                        status = "Checking device requirements...",
                        lastOutput = null
                    )
                )
            }

            if (pendingZip != null) {
                val sourceLabel = _state.value.patchState.bootImageName ?: "rom.zip"
                val sb = StringBuilder()
                appendTrimmed(sb, "Reading $sourceLabel...\n")
                publishStreamingLog(sb.toString(), updatePatch = true)
                _state.update { it.copy(patchState = it.patchState.copy(status = "Extracting from $sourceLabel...")) }

                val preferInitBoot = hasInitBoot()
                val extractResult = withContext(Dispatchers.IO) {
                    runCatching {
                        RomZipExtractor.extractBootImage(
                            context = getApplication(),
                            uri = pendingZip,
                            workDir = workDir,
                            preferInitBoot = preferInitBoot,
                        ) { msg ->
                            appendTrimmed(sb, msg)
                            appendTrimmed(sb, "\n")
                            publishStreamingLog(sb.toString(), updatePatch = true)
                        }
                    }
                }
                val ok = extractResult.getOrNull()
                if (ok == null) {
                    appendTrimmed(sb, "Failed: ${extractResult.exceptionOrNull()?.message ?: "extraction failed"}\n")
                    publishStreamingLog(sb.toString(), updatePatch = true)
                    _state.update {
                        it.copy(
                            patchState = it.patchState.copy(
                                isPatching = false,
                                status = "Failed to extract from zip"
                            )
                        )
                    }
                    return@launch
                }
                appendTrimmed(sb, "Extracted ${ok.partitionName}.img")
                publishStreamingLog(sb.toString(), updatePatch = true)
                _state.update {
                    it.copy(
                        patchState = it.patchState.copy(
                            bootImageName = "${ok.partitionName}.img",
                            bootImagePath = ok.file.absolutePath,
                            pendingRomZipUri = null
                        )
                    )
                }
            }

            val boot = _state.value.patchState.bootImagePath
            val bootName = _state.value.patchState.bootImageName
            if (boot.isNullOrBlank()) {
                _state.update {
                    it.copy(patchState = it.patchState.copy(isPatching = false, status = "Boot image missing after extraction"))
                }
                return@launch
            }

            if (bootName != null && bootName.contains("boot", ignoreCase = true) && !bootName.contains("init", ignoreCase = true)) {
                if (hasInitBoot()) {
                    val msg = "Bail out: This device uses an init_boot partition. Patching a regular boot image will cause ksud to incorrectly create a ramdisk. Please patch init_boot.img instead."
                    publishStreamingLog(msg, updatePatch = true)
                    _state.update {
                        it.copy(
                            patchState = it.patchState.copy(
                                isPatching = false,
                                status = "Requires init_boot.img"
                            )
                        )
                    }
                    return@launch
                }
            }

            _state.update { it.copy(patchState = it.patchState.copy(status = "Downloading binaries...")) }
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

            val command = buildList {
                add(ksud.absolutePath)
                add("boot-patch")
                add("-b")
                add(boot)
                add("--kmi")
                add(kmi)
                add("--module")
                add(module)
                add("-o")
                add(workDir.absolutePath)
                if (_state.value.patchState.allowShell) add("--allow-shell")
                if (_state.value.patchState.enableAdbd) add("--enable-adbd")
            }

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

    private fun findPatchedImage(workDir: File): File? = engine.findPatchedImage(workDir)

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

    private fun getWorkDir(): File = engine.workDir()

    private suspend fun ensureBinaries(): Result<Unit> = runCatching {
        val variant = _state.value.patchState.variant
        engine.prepareKsud(variant)
        if (_state.value.patchState.modulePath.isNullOrBlank()) {
            val (name, path) = engine.resolveModule(variant, null).getOrThrow()
            _state.update {
                it.copy(
                    patchState = it.patchState.copy(
                        moduleName = name ?: it.patchState.moduleName,
                        modulePath = path
                    )
                )
            }
        }
        Unit
    }

    private fun resolveBundledBinaryForVariant(variant: KsuVariant): File = engine.resolveBinary(variant)

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
        val sb = StringBuilder()
        if (!initialLog.isNullOrBlank()) {
            appendTrimmed(sb, initialLog)
            appendTrimmed(sb, "\n\n")
        }
        var pendingLines = 0
        var lastEmitAt = SystemClock.elapsedRealtime()
        val result = engine.runCommand(command, workDir) { line ->
            appendTrimmed(sb, line)
            appendTrimmed(sb, "\n")
            pendingLines += 1
            val now = SystemClock.elapsedRealtime()
            if (pendingLines >= STREAM_LOG_EMIT_LINES || now - lastEmitAt >= STREAM_LOG_EMIT_INTERVAL_MS) {
                publishStreamingLog(sb.toString(), updatePatch = true)
                pendingLines = 0
                lastEmitAt = now
            }
        }
        publishStreamingLog(sb.toString(), updatePatch = true)
        return result.map { sb.toString() }
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
                    pendingRomZipUri = null,
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
        val logBuf = StringBuilder()
        val seed = if (lkmMode) _state.value.patchState.lastOutput else null
        if (!seed.isNullOrBlank()) {
            appendTrimmed(logBuf, seed)
            appendTrimmed(logBuf, "\n")
        }
        var pendingLines = 0
        var lastEmitAt = SystemClock.elapsedRealtime()
        fun publish() = publishStreamingLog(logBuf.toString(), updatePatch = lkmMode)
        fun appendLog(line: String) {
            appendTrimmed(logBuf, line)
            appendTrimmed(logBuf, "\n")
            pendingLines += 1
            val now = SystemClock.elapsedRealtime()
            if (pendingLines >= STREAM_LOG_EMIT_LINES || now - lastEmitAt >= STREAM_LOG_EMIT_INTERVAL_MS) {
                publish()
                pendingLines = 0
                lastEmitAt = now
            }
        }

        var lastPhase = OtaPhase.IDLE
        fun setPhase(p: OtaPhase) {
            lastPhase = p
            if (!lkmMode) _state.update { it.copy(otaState = it.otaState.copy(phase = p)) }
        }

        _state.update { state ->
            if (lkmMode) {
                state.copy(patchState = state.patchState.copy(isPatching = true, status = "Preparing LKM update..."))
            } else {
                state.copy(otaState = OtaState(phase = OtaPhase.CHECKING_ROOT, isLkmMode = false))
            }
        }

        val patch = _state.value.patchState
        val result = engine.runSlotPatch(
            lkmMode = lkmMode,
            variant = patch.variant,
            kmi = patch.kmi,
            moduleOverride = patch.modulePath,
            allowShell = patch.allowShell,
            enableAdbd = patch.enableAdbd,
            onLine = ::appendLog,
            onPhase = ::setPhase,
            onSlots = { current, next ->
                _state.update { it.copy(otaState = it.otaState.copy(currentSlot = current, nextSlot = next)) }
            },
        )
        publish()

        if (result.isSuccess) {
            if (!lkmMode) setPhase(OtaPhase.DONE)
            _state.update {
                if (lkmMode) {
                    it.copy(patchState = it.patchState.copy(status = "Installed successfully", rebootRequired = true))
                } else {
                    it.copy(
                        otaState = it.otaState.copy(rebootRequired = true),
                        patchState = it.patchState.copy(status = "Installed successfully")
                    )
                }
            }
            appendLog(
                if (lkmMode) "LKM update complete. ✓  Safe to reboot."
                else "OTA root patch complete. ✓  Please reboot to boot into the updated slot with root preserved."
            )
            publish()
        } else {
            val special = lastPhase == OtaPhase.NO_ROOT || lastPhase == OtaPhase.NO_OTA_PENDING
            if (!special) {
                if (!lkmMode) setPhase(OtaPhase.ERROR)
                appendLog("Install failed: ${result.exceptionOrNull()?.message}")
                publish()
            }
            if (lkmMode) {
                val status = if (lastPhase == OtaPhase.NO_ROOT) "Root access denied" else "Install failed"
                _state.update { it.copy(patchState = it.patchState.copy(status = status)) }
            }
        }

        if (lkmMode) {
            _state.update { it.copy(patchState = it.patchState.copy(isPatching = false)) }
        }
    }

    fun rebootNow() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { RootShell.run("svc power reboot") }
        }
    }

    private suspend fun hasInitBoot(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val getpropProc = ProcessBuilder("getprop").start()
                val props = getpropProc.inputStream.bufferedReader().use { it.readText() }
                if (props.contains("init_boot", ignoreCase = true)) { // ro.boot.init_boot_device or ro.boot.init_boot_cmdline
                    return@withContext true
                }

                val release = android.system.Os.uname().release
                val versionParts = release.substringBefore("-").split(".")
                val major = versionParts.getOrNull(0)?.toIntOrNull() ?: 0
                val minor = versionParts.getOrNull(1)?.toIntOrNull() ?: 0
                if (major > 5 || (major == 5 && minor >= 15)) {
                    return@withContext true // >= 5.15 kernels have init_boot
                }
            } catch (e: Throwable) {
                // Ignore
            }
            false
        }
    }

    private companion object {
        val httpClient = okhttp3.OkHttpClient()
        const val MAX_LOG_CHARS = 64_000
        const val STREAM_LOG_EMIT_LINES = 8
        const val STREAM_LOG_EMIT_INTERVAL_MS = 200L
    }
}
