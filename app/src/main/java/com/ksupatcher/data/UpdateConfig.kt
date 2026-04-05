package com.ksupatcher.data

object UpdateConfig {
    const val versionJsonUrl = "https://example.com/ksupatcher/version.json"

    const val ksuBinaryUrl =
        "https://github.com/tiann/KernelSU/releases/download/v3.1.0/ksud-aarch64-linux-android"

    const val ksunBinaryUrl =
        "https://github.com/KernelSU-Next/KernelSU-Next/releases/download/v3.0.1/ksud-aarch64-linux-android"

    const val magiskbootBinaryUrl = "https://example.com/ksupatcher/libmagiskboot.so"

    const val ksuOwner = "Tiann"
    const val ksuRepo = "KernelSU"
    const val ksunOwner = "KernelSU-Next"
    const val ksunRepo = "KernelSU-Next"
    const val ksudAsset = "ksud-aarch64-linux-android"

    const val magiskOwner = "cyberknight777"
    const val magiskRepo = "magisk_bins_ndk"
    const val magiskAsset = "magiskboot"

    const val ksuLkmOwner = "cyberknight777"
    const val ksuLkmRepo = "ksu-lkm"
    const val ksuModuleAsset = "kernelsu.ko"
    const val ksunModuleAsset = "kernelsu_next.ko"

    val supportedKmis = listOf(
        "android12-5.10",
        "android13-5.10",
        "android13-5.15",
        "android14-5.15",
        "android14-6.1",
        "android15-6.6",
        "android16-6.12"
    )
}
