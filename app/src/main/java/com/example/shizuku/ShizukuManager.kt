package com.example.shizuku

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

sealed interface ShizukuState {
    data object Checking : ShizukuState
    data object NotInstalled : ShizukuState
    data object NotRunning : ShizukuState
    data object PermissionRequired : ShizukuState
    data class Ready(val version: Int) : ShizukuState
}

data class CommandResult(
    val success: Boolean,
    val exitCode: Int,
    val output: String,
    val error: String
)

class ShizukuManager(private val context: Context) {

    private val TAG = "ShizukuManager"
    val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    val REQUEST_CODE_PERMISSION = 7101

    private val _state = MutableStateFlow<ShizukuState>(ShizukuState.Checking)
    val state: StateFlow<ShizukuState> = _state.asStateFlow()

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        checkStatus()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        _state.value = ShizukuState.NotRunning
    }

    private val permissionResultListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == REQUEST_CODE_PERMISSION) {
                if (grantResult == PackageManager.PERMISSION_GRANTED) {
                    onPermissionGranted()
                } else {
                    _state.value = ShizukuState.PermissionRequired
                }
            }
        }

    fun init() {
        try {
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
            Shizuku.addRequestPermissionResultListener(permissionResultListener)
        } catch (e: Exception) {
            Log.e(TAG, "Error registering Shizuku listeners", e)
        }
        checkStatus()
    }

    fun destroy() {
        try {
            Shizuku.removeBinderReceivedListener(binderReceivedListener)
            Shizuku.removeBinderDeadListener(binderDeadListener)
            Shizuku.removeRequestPermissionResultListener(permissionResultListener)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering Shizuku listeners", e)
        }
    }

    fun checkStatus() {
        if (!isShizukuInstalled()) {
            _state.value = ShizukuState.NotInstalled
            return
        }

        val isAlive = try {
            Shizuku.pingBinder()
        } catch (e: Throwable) {
            false
        }

        if (!isAlive) {
            _state.value = ShizukuState.NotRunning
            return
        }

        val hasPermission = try {
            if (Shizuku.isPreV11()) {
                false
            } else {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            }
        } catch (e: Throwable) {
            false
        }

        if (!hasPermission) {
            _state.value = ShizukuState.PermissionRequired
        } else {
            val version = try {
                Shizuku.getVersion()
            } catch (e: Throwable) {
                13
            }
            _state.value = ShizukuState.Ready(version)
            onPermissionGranted()
        }
    }

    private fun onPermissionGranted() {
        val version = try {
            Shizuku.getVersion()
        } catch (e: Throwable) {
            13
        }
        _state.value = ShizukuState.Ready(version)

        // Try granting WRITE_SECURE_SETTINGS to self for maximum responsiveness and reliability
        try {
            val ownPackage = context.packageName
            executeCommandAsync("pm grant $ownPackage android.permission.WRITE_SECURE_SETTINGS")
        } catch (e: Exception) {
            Log.w(TAG, "Could not grant WRITE_SECURE_SETTINGS via Shizuku", e)
        }
    }

    fun requestPermission() {
        try {
            if (Shizuku.pingBinder()) {
                Shizuku.requestPermission(REQUEST_CODE_PERMISSION)
            } else {
                _state.value = ShizukuState.NotRunning
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to request Shizuku permission", e)
            _state.value = ShizukuState.NotRunning
        }
    }

    fun isShizukuInstalled(): Boolean {
        return try {
            context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun getLaunchShizukuIntent(): Intent? {
        return context.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE)
    }

    private fun createShizukuProcess(args: Array<out String>): Process {
        val method = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java
        )
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return method.invoke(null, args, null, null) as Process
    }

    suspend fun executeCommand(vararg args: String): CommandResult = withContext(Dispatchers.IO) {
        if (!Shizuku.pingBinder()) {
            return@withContext CommandResult(
                success = false,
                exitCode = -1,
                output = "",
                error = "Shizuku is not running. Please start Shizuku first."
            )
        }

        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            return@withContext CommandResult(
                success = false,
                exitCode = -1,
                output = "",
                error = "Shizuku permission has not been granted."
            )
        }

        return@withContext try {
            val process = createShizukuProcess(args)
            val outputBuilder = StringBuilder()
            val errorBuilder = StringBuilder()

            val stdInReader = BufferedReader(InputStreamReader(process.inputStream))
            val stdErrReader = BufferedReader(InputStreamReader(process.errorStream))

            var line: String?
            while (stdInReader.readLine().also { line = it } != null) {
                outputBuilder.appendLine(line)
            }
            while (stdErrReader.readLine().also { line = it } != null) {
                errorBuilder.appendLine(line)
            }

            val completed = process.waitFor(10, TimeUnit.SECONDS)
            if (!completed) {
                process.destroyForcibly()
                CommandResult(
                    success = false,
                    exitCode = -1,
                    output = outputBuilder.toString().trim(),
                    error = "Command timed out after 10 seconds."
                )
            } else {
                val exitCode = process.exitValue()
                CommandResult(
                    success = exitCode == 0,
                    exitCode = exitCode,
                    output = outputBuilder.toString().trim(),
                    error = errorBuilder.toString().trim()
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to execute Shizuku command: ${args.joinToString(" ")}", e)
            CommandResult(
                success = false,
                exitCode = -1,
                output = "",
                error = e.localizedMessage ?: "Unknown execution error"
            )
        }
    }

    private fun executeCommandAsync(command: String) {
        Thread {
            try {
                val process = createShizukuProcess(arrayOf("sh", "-c", command))
                process.waitFor(5, TimeUnit.SECONDS)
            } catch (e: Exception) {
                Log.w(TAG, "executeCommandAsync error: ${e.message}")
            }
        }.start()
    }
}
