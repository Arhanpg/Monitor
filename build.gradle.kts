// Top-level build file
plugins {
    // 1. We hardcode versions here to prevent "libs" errors
    id("com.android.application") version "8.1.3" apply false
    id("com.android.library") version "8.1.3" apply false

    // 2. This matches your Kotlin version (2.0.21)
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false

    // 3. FIX: This is the new plugin required for Kotlin 2.0 + Compose
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false

    // 4. FIX: This allows the app module to find Google Services
    id("com.google.gms.google-services") version "4.4.2" apply false
}