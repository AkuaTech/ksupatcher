package com.ksupatcher.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.documentfile.provider.DocumentFile
import com.ksupatcher.data.DownloadState
import com.ksupatcher.data.SettingsRepository
import com.ksupatcher.data.UpdateConfig
import com.ksupatcher.data.VersionInfo
import com.ksupatcher.jni.NativeBridge
import com.ksupatcher.network.DownloadRepository
import com.ksupatcher.network.GitHubReleaseRepository
import com.ksupatcher.network.UpdateRepository
import com.ksupatcher.root.RootShell
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
import android.provider.MediaStore
import java.util.Locale

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
    val versionInfo: VersionInfo? = null,
    val versionError: String? = null,
    val versionUrl: String = UpdateConfig.versionJsonUrl,
    val ksuDownload: DownloadState = DownloadState(),
    val ksunDownload: DownloadState = DownloadState(),
    val magiskbootDownload: DownloadState = DownloadState(),
    val nativeStatus: String? = null,
    val patchState: PatchState = PatchState(),
    val latestReleaseTag: String? = null,
    val updateManifest: com.ksupatcher.data.UpdateManifest? = null,
    val manifestError: String? = null,
    val otaState: OtaState = OtaState(),
    val rootStatus: RootStatus = RootStatus.UNKNOWN
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
    val lastCommand: String? = null,
    val lastOutput: String? = null,
    val outputPath: String? = null
)

class MainViewModel(
    application: Application,
    private val updateRepository: UpdateRepository,
    private val downloadRepository: DownloadRepository,
    private val settingsRepository: SettingsRepository,
    private val releaseRepository: GitHubReleaseRepository
) : AndroidViewModel(application) {

    constructor(application: Application) : this(
        application = application,
        updateRepository = UpdateRepository(),
        downloadRepository = DownloadRepository(),
        settingsRepository = SettingsRepository(application),
        releaseRepository = GitHubReleaseRepository()
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    init {
        viewModelScope.launch {
            settingsRepository.versionUrlFlow.collect { url ->
                _state.update { it.copy(versionUrl = url) }
            }
        }
        viewModelScope.launch {
            settingsRepository.rootStatusFlow.collect { statusStr ->
                val status = try { RootStatus.valueOf(statusStr) } catch (_: Exception) { RootStatus.UNKNOWN }
                _state.update { it.copy(rootStatus = status) }
            }
        }
        refreshRootStatus()
    }

    fun refreshRootStatus() {
        viewModelScope.launch(Dispatchers.IO) {
            val isRooted = RootShell.isRooted()
            val status = if (isRooted) RootStatus.GRANTED else RootStatus.NOT_GRANTED
            settingsRepository.setRootStatus(status.name)
        }
    }

    fun refreshVersion() {
        _state.update { it.copy(isCheckingVersion = true, versionError = null) }
        viewModelScope.launch {
            val result = updateRepository.fetchVersionInfo(_state.value.versionUrl)
            _state.update { current ->
                current.copy(
                    isCheckingVersion = false,
                    versionInfo = result.getOrNull(),
                    versionError = result.exceptionOrNull()?.message
                )
            }
        }
    }

    fun checkLatestReleaseUpdate() {
        _state.update { it.copy(isCheckingVersion = true, manifestError = null) }
        viewModelScope.launch {
            val result = updateRepository.fetchUpdateManifestFromLatestRelease(UpdateConfig.ksuLkmOwner, UpdateConfig.ksuLkmRepo)
            _state.update { current ->
                if (result.isSuccess) {
                    val manifest = result.getOrNull()!!
                    current.copy(
                        isCheckingVersion = false,
                        latestReleaseTag = manifest.tag,
                        updateManifest = manifest,
                        manifestError = null
                    )
                } else {
                    current.copy(
                        isCheckingVersion = false,
                        manifestError = result.exceptionOrNull()?.message
                    )
                }
            }
        }
    }

    fun updateVersionUrl(value: String) {
        _state.update { it.copy(versionUrl = value) }
    }

    fun saveVersionUrl() {
        val url = _state.value.versionUrl.trim()
        viewModelScope.launch {
            settingsRepository.setVersionUrl(url)
        }
    }

    fun downloadKsud(variant: KsuVariant) {
        val url = when (variant) {
            KsuVariant.KSU -> UpdateConfig.ksuBinaryUrl
            KsuVariant.KSUN -> UpdateConfig.ksunBinaryUrl
        }
        val name = when (variant) {
            KsuVariant.KSU -> "ksud-ksu"
            KsuVariant.KSUN -> "ksud-ksun"
        }
        val target = File(getDownloadDir(), name)
        setDownloadState(variant, DownloadState(isDownloading = true, progress = 0))
        viewModelScope.launch {
            val result = downloadRepository.download(url, target) { progress ->
                setDownloadState(variant, _state.value.valueForVariant(variant).copy(progress = progress))
            }
            setDownloadState(
                variant,
                _state.value.valueForVariant(variant).copy(
                    isDownloading = false,
                    filePath = result.getOrNull()?.absolutePath,
                    error = result.exceptionOrNull()?.message
                )
            )
        }
    }

    fun downloadMagiskboot() {
        val target = File(getDownloadDir(), "libmagiskboot.so")
        _state.update { it.copy(magiskbootDownload = DownloadState(isDownloading = true, progress = 0)) }
        viewModelScope.launch {
            val result = downloadRepository.download(UpdateConfig.magiskbootBinaryUrl, target) { progress ->
                _state.update { it.copy(magiskbootDownload = it.magiskbootDownload.copy(progress = progress)) }
            }
            _state.update {
                it.copy(
                    magiskbootDownload = it.magiskbootDownload.copy(
                        isDownloading = false,
                        filePath = result.getOrNull()?.absolutePath,
                        error = result.exceptionOrNull()?.message
                    )
                )
            }
        }
    }

    fun checkNativeLibraries() {
        val result = NativeBridge.tryLoad()
        _state.update {
            it.copy(
                nativeStatus = result.fold(
                    onSuccess = { "Native libraries loaded" },
                    onFailure = { error -> "Native load failed: ${error.message}" }
                )
            )
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

    fun buildPatchCommand() {
        val workDir = getWorkDir()
        val ksud = File(workDir, "ksud").absolutePath
        val magiskboot = File(workDir, "magiskboot").absolutePath
        val boot = _state.value.patchState.bootImagePath
        val module = _state.value.patchState.modulePath
        val kmi = "android12-5.10"
        if (boot.isNullOrBlank() || module.isNullOrBlank()) {
            _state.update {
                it.copy(patchState = it.patchState.copy(status = "Select boot.img and kernelsu.ko"))
            }
            return
        }
        val command = "$ksud boot-patch -b $boot --kmi $kmi --magiskboot $magiskboot --module $module --out ${workDir.absolutePath}"
        _state.update {
            it.copy(
                patchState = it.patchState.copy(
                    lastCommand = command,
                    status = "Ready to run with shipped binaries"
                )
            )
        }
    }

    fun prepareBinaries() {
        viewModelScope.launch {
            _state.update { it.copy(patchState = it.patchState.copy(isPatching = true, status = "Downloading binaries...")) }
            val result = ensureBinaries()
            _state.update {
                it.copy(
                    patchState = it.patchState.copy(
                        isPatching = false,
                        status = result.fold(
                            onSuccess = { "Binaries ready" },
                            onFailure = { error -> error.message ?: "Failed to prepare binaries" }
                        )
                    )
                )
            }
        }
    }

    fun runPatch() {
        val workDir = getWorkDir()
        val boot = _state.value.patchState.bootImagePath
        val kmi = "android12-5.10"

        if (boot.isNullOrBlank()) {
            _state.update {
                it.copy(patchState = it.patchState.copy(status = "Please select boot.img"))
            }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(patchState = it.patchState.copy(isPatching = true, status = "Downloading binaries...", lastOutput = null)) }
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
                        status = "Patching boot image...",
                        lastCommand = "ksud boot-patch"
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

            val result = executeCommandStreaming(command, workDir)
            val patchedFile = if (result.isSuccess) findPatchedImage(workDir) else null
            val saveResult = if (result.isSuccess && patchedFile != null) {
                savePatchedImageToDownloads(patchedFile)
            } else {
                Result.failure(IllegalStateException("Patched image not found in work dir"))
            }

            val statusText = if (result.isSuccess) {
                if (saveResult.isSuccess) {
                    "Patch completed and saved to Downloads"
                } else {
                    "Patch completed (export to Downloads failed)"
                }
            } else {
                "Patch failed"
            }

            val mergedOutput = buildString {
                append(result.getOrNull() ?: result.exceptionOrNull()?.message.orEmpty())
                if (result.isSuccess) {
                    append("\n\n")
                    if (saveResult.isSuccess) {
                        append("Saved to Downloads: ${saveResult.getOrNull()}")
                    } else {
                        append("Failed to save to Downloads: ${saveResult.exceptionOrNull()?.message}")
                    }
                }
            }

            _state.update {
                it.copy(
                    patchState = it.patchState.copy(
                        isPatching = false,
                        status = statusText,
                        lastOutput = mergedOutput,
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

    private fun savePatchedImageToDownloads(sourceFile: File): Result<String> = runCatching {
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
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
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
        } else {
            val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloads.exists()) downloads.mkdirs()
            val target = File(downloads, fileName)
            sourceFile.copyTo(target, overwrite = true)
            target.absolutePath
        }
    }

    private fun setDownloadState(variant: KsuVariant, state: DownloadState) {
        _state.update {
            when (variant) {
                KsuVariant.KSU -> it.copy(ksuDownload = state)
                KsuVariant.KSUN -> it.copy(ksunDownload = state)
            }
        }
    }

    private fun getDownloadDir(): File {
        val dir = File(getApplication<Application>().filesDir, "downloads")
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

    private suspend fun executeCommandStreaming(command: List<String>, workDir: File): Result<String> {
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

                try {
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        sb.append(line).append('\n')
                        val currentFull = sb.toString()
                        val currentLine = line + "\n"
                        _state.update { state ->
                            val patch = state.patchState.copy(lastOutput = currentFull)
                            val ota = if (state.otaState.phase != OtaPhase.IDLE && !state.otaState.isLkmMode) {
                                state.otaState.copy(log = state.otaState.log + currentLine)
                            } else {
                                state.otaState
                            }
                            state.copy(patchState = patch, otaState = ota)
                        }
                    }
                } finally {
                    reader.close()
                }

                val exitCode = process.waitFor()
                val output = sb.toString()
                if (exitCode != 0) {
                    error("Exit $exitCode\n$output")
                }
                output
            }
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
                    lastCommand = null,
                    lastOutput = null,
                    status = null,
                    bootImageName = null,
                    bootImagePath = null,
                    moduleName = null,
                    modulePath = null,
                    isPatching = false
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
                if (lkmMode) {
                    val newOutput = (state.patchState.lastOutput ?: "") + "\n" + msg
                    state.copy(patchState = state.patchState.copy(lastOutput = newOutput))
                } else {
                    state.copy(otaState = state.otaState.copy(log = state.otaState.log + "\n" + msg))
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
                            lastCommand = "ksud install",
                            lastOutput = null,
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
                appendLog("Checking for pending OTA (getprop ota.other.vbmeta_digest)...")
                val otaProp = try {
                    RootShell.getProp("ota.other.vbmeta_digest")
                } catch (e: Throwable) {
                    setPhase(OtaPhase.ERROR)
                    appendLog("Error reading prop: ${e.message}")
                    return
                }
                if (otaProp.isNullOrBlank()) {
                    setPhase(OtaPhase.NO_OTA_PENDING)
                    appendLog("No OTA update is pending (ota.other.vbmeta_digest is empty).\nApply an OTA update first, then come back here before rebooting.")
                    return
                }
                appendLog("OTA detected. vbmeta_digest = $otaProp")
            }

            setPhase(OtaPhase.READING_SLOT)
            val currentSlot = try {
                RootShell.getProp("ro.boot.slot_suffix") ?: error("ro.boot.slot_suffix returned empty")
            } catch (e: Throwable) {
                setPhase(OtaPhase.ERROR)
                if (lkmMode) _state.update { it.copy(patchState = it.patchState.copy(status = "Failed to read slot")) }
                appendLog("Failed to read slot: ${e.message}")
                return
            }
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
                    appendLog("init_boot partition detected using init_boot instead of boot.")
                    initBootDevice
                } else {
                    bootDevice
                }
            } catch (e: Throwable) {
                appendLog("Could not detect init_boot, falling back to boot. (${e.message})")
                bootDevice
            }

            appendLog("Dumping: $blockDevice → ${dumpedImg.absolutePath}")
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
                "--kmi", "android12-5.10",
                "--magiskboot", magiskboot.absolutePath,
                "--module", module,
                "-o", workDir.absolutePath
            )
            appendLog("Patching boot image...")
            val patchResult = executeCommandStreaming(patchCmd, workDir)
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
            appendLog("Flashing: ${patchedImg.absolutePath} → $blockDevice")
            try {
                // make block device writable before flashing
                RootShell.run("blockdev --setrw $blockDevice")
                RootShell.run("dd if=${patchedImg.absolutePath} of=$blockDevice bs=4096")
            } catch (e: Throwable) {
                setPhase(OtaPhase.ERROR)
                if (lkmMode) _state.update { it.copy(patchState = it.patchState.copy(status = "Flash failed")) }
                appendLog("DD (flash) failed: ${e.message}")
                return
            }

            setPhase(OtaPhase.DONE)
            _state.update {
                it.copy(
                    otaState = it.otaState.copy(rebootRequired = true),
                    patchState = it.patchState.copy(status = "Installed successfully")
                )
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
}

private fun UiState.valueForVariant(variant: KsuVariant): DownloadState {
    return when (variant) {
        KsuVariant.KSU -> ksuDownload
        KsuVariant.KSUN -> ksunDownload
    }
}
