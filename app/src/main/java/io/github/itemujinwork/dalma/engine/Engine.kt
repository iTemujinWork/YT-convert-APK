package io.github.itemujinwork.dalma.engine

import android.content.Context
import android.system.Os
import android.util.Log
import io.github.itemujinwork.dalma.core.EngineVersion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.zip.ZipFile as ArchiveFile
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipFile
import kotlin.concurrent.thread

/**
 * The download engine: yt-dlp running on the Python that youtubedl-android packages as
 * native libraries, with its QuickJS and FFmpeg next to it. We drive the binaries
 * ourselves so that first-run unpacking can show progress, the yt-dlp copy can be
 * replaced by a verified newer one, and a cancelled download kills ffmpeg too.
 */
class Engine(context: Context) {
    private val app = context.applicationContext
    private val nativeDir = File(app.applicationInfo.nativeLibraryDir)
    private val root = File(app.noBackupFilesDir, "engine")
    private val pythonDir = File(root, "python")
    private val ffmpegDir = File(root, "ffmpeg")
    private val binDir = File(root, "bin")
    private val cacheDir = File(root, "cache")
    private val ytDlp = File(root, "yt-dlp")
    private val ytDlpUpdated = File(root, "yt-dlp.updated")
    private val versionCache = File(root, "yt-dlp.version")

    /** Downloads land here, then move to the Music folder. */
    val workDir = File(root, "work")

    private val python = File(nativeDir, "libpython.so")
    private val pythonZip = File(nativeDir, "libpython.zip.so")
    private val ffmpegZip = File(nativeDir, "libffmpeg.zip.so")
    private val quickJs = File(nativeDir, "libqjs.so")

    class RunResult(val exitCode: Int, val stderr: String)

    /** Thrown from a line callback to stop the run with a reason of our own. */
    class Abort(val reason: Any) : RuntimeException("aborted")

    /** True when nothing needs unpacking before the next run. */
    val isUnpacked: Boolean
        get() = isUnpacked(pythonDir, pythonZip) && isUnpacked(ffmpegDir, ffmpegZip)

    /** Unpacks Python and FFmpeg after install or update and reports 0..1 along the way. */
    suspend fun prepare(onProgress: (Float) -> Unit = {}) = withContext(Dispatchers.IO) {
        listOf(root, binDir, cacheDir, workDir).forEach { it.mkdirs() }
        val jobs = listOf(pythonZip to pythonDir, ffmpegZip to ffmpegDir).filterNot { (zip, dir) -> isUnpacked(dir, zip) }
        val total = jobs.sumOf { it.first.length() }.coerceAtLeast(1)
        var done = 0L
        for ((zip, dir) in jobs) {
            dir.deleteRecursively()
            dir.mkdirs()
            try {
                unzip(zip, dir) { bytes -> onProgress(((done + bytes).toFloat() / total).coerceIn(0f, 1f)) }
            } catch (e: Exception) {
                dir.deleteRecursively()
                throw IOException("could not unpack ${zip.name}", e)
            }
            stampFile(dir).writeText(stamp(zip))
            done += zip.length()
        }
        ensureLinks()
        ensureYtDlp()
        onProgress(1f)
    }

    private fun isUnpacked(dir: File, zip: File): Boolean =
        dir.isDirectory && stampFile(dir).takeIf { it.isFile }?.readText() == stamp(zip)

    private fun stampFile(dir: File) = File(dir, ".stamp")

    /** The native library changes with every app update, and so does this stamp. */
    private fun stamp(zip: File): String {
        val updated = try {
            app.packageManager.getPackageInfo(app.packageName, 0).lastUpdateTime
        } catch (_: Exception) {
            0L
        }
        return "${zip.length()}:$updated"
    }

    /**
     * The zips carry Unix symlinks (libz.so.1 -> libz.so.1.3.1, libavcodec.so.61 -> ...), and
     * ffmpeg and Python's C modules are linked against those names. java.util.zip cannot see
     * them, so this goes through commons-compress and recreates every link as a real symlink.
     */
    private fun unzip(zip: File, target: File, onBytes: (Long) -> Unit) {
        val targetPath = target.canonicalPath + File.separator
        var done = 0L
        ArchiveFile(zip).use { archive ->
            for (entry in archive.entries) {
                val out = File(target, entry.name)
                if (!out.canonicalPath.startsWith(targetPath)) throw IOException("bad zip entry ${entry.name}")
                when {
                    entry.isDirectory -> out.mkdirs()
                    entry.isUnixSymlink -> {
                        out.parentFile?.mkdirs()
                        val link = archive.getInputStream(entry).use { it.bufferedReader().readText() }.trim()
                        if (link.isEmpty() || link.startsWith("/") || link.split('/').contains("..")) {
                            throw IOException("bad link ${entry.name} -> $link")
                        }
                        out.delete()
                        Os.symlink(link, out.absolutePath)
                    }
                    else -> {
                        out.parentFile?.mkdirs()
                        archive.getInputStream(entry).use { input -> out.outputStream().use { input.copyTo(it) } }
                    }
                }
                done += entry.compressedSize.coerceAtLeast(0L)
                onBytes(done)
            }
        }
    }

    /** yt-dlp looks for ffmpeg and ffprobe by name in one folder. */
    private fun ensureLinks() {
        link(File(nativeDir, "libffmpeg.so"), File(binDir, "ffmpeg"))
        link(File(nativeDir, "libffprobe.so"), File(binDir, "ffprobe"))
    }

    private fun link(target: File, link: File) {
        if (!target.isFile) return
        try {
            link.delete()
            Os.symlink(target.absolutePath, link.absolutePath)
        } catch (e: Exception) {
            Log.w(TAG, "cannot link ${link.name}", e)
        }
    }

    private fun ensureYtDlp() {
        if (ytDlp.isFile && ytDlp.length() > 0) return
        copyShipped()
    }

    private fun copyShipped() {
        val tmp = File(root, "yt-dlp.tmp")
        app.resources.openRawResource(com.yausername.youtubedl_android.R.raw.ytdlp).use { input ->
            tmp.outputStream().use { input.copyTo(it) }
        }
        if (!tmp.renameTo(ytDlp)) throw IOException("cannot install yt-dlp")
        ytDlpUpdated.delete()
        versionCache.delete()
    }

    /** Puts a verified, newer yt-dlp in place. */
    fun installUpdate(file: File, version: String) {
        if (!file.renameTo(ytDlp)) {
            file.copyTo(ytDlp, overwrite = true)
            file.delete()
        }
        ytDlpUpdated.writeText(version)
        versionCache.writeText(version)
    }

    /** Back to the copy shipped inside the app, for when an update turns out broken. */
    fun resetToShipped() {
        copyShipped()
    }

    val isUpdated: Boolean get() = ytDlpUpdated.isFile

    /** The version of the yt-dlp in use, read from the zipapp (cheap) and cached. */
    fun version(): String? {
        versionCache.takeIf { it.isFile }?.readText()?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        val version = versionInZip(ytDlp) ?: return null
        runCatching { versionCache.writeText(version) }
        return version
    }

    fun versionInZip(file: File): String? = try {
        ZipFile(file).use { zip ->
            zip.getEntry("yt_dlp/version.py")?.let { entry ->
                val text = zip.getInputStream(entry).bufferedReader().readText()
                Regex("""__version__\s*=\s*['"]([^'"]+)['"]""").find(text)?.groupValues?.get(1)
            }
        }
    } catch (e: Exception) {
        Log.w(TAG, "cannot read version from ${file.name}", e)
        null
    }

    fun isNewerThanInstalled(candidate: String): Boolean = EngineVersion.isNewer(candidate, version())

    private val environment: Map<String, String> by lazy {
        mapOf(
            "LD_LIBRARY_PATH" to "${File(pythonDir, "usr/lib")}:${File(ffmpegDir, "usr/lib")}",
            "SSL_CERT_FILE" to File(pythonDir, "usr/etc/tls/cert.pem").absolutePath,
            "PYTHONHOME" to File(pythonDir, "usr").absolutePath,
            "HOME" to root.absolutePath,
            "TMPDIR" to app.cacheDir.absolutePath,
            "PATH" to listOf(System.getenv("PATH") ?: "/system/bin", binDir.absolutePath, nativeDir.absolutePath).joinToString(":"),
            "PYTHONUNBUFFERED" to "1",
            "PYTHONDONTWRITEBYTECODE" to "1",
            "PYTHONUTF8" to "1",
        )
    }

    private val standardArgs: List<String>
        get() = listOf(
            "--ffmpeg-location", binDir.absolutePath,
            "--js-runtimes", "quickjs:${quickJs.absolutePath}",
            "--cache-dir", cacheDir.absolutePath,
        )

    /**
     * Runs yt-dlp with [args]; every stdout line goes to [onLine] as it arrives. Cancelling
     * the coroutine, or throwing [Abort] from [onLine], kills yt-dlp and its children.
     */
    suspend fun run(args: List<String>, onLine: (String) -> Unit): RunResult = withContext(Dispatchers.IO) {
        if (!python.canExecute()) throw IOException("python is missing from ${nativeDir.path}")
        // The shell prints its own pid, then becomes python; that pid lets us kill the tree.
        val command = listOf("/system/bin/sh", "-c", "echo $$; exec \"$@\"", "dalma", python.absolutePath, ytDlp.absolutePath) +
            standardArgs + args
        val process = ProcessBuilder(command).directory(workDir).also { it.environment().putAll(environment) }.start()
        val pid = AtomicInteger(-1)
        val stderr = StringBuilder()
        val errReader = thread(name = "yt-dlp-stderr", isDaemon = true) {
            try {
                process.errorStream.bufferedReader().forEachLine { line ->
                    if (stderr.length < 64_000) stderr.append(line).append('\n')
                }
            } catch (_: IOException) {
            }
        }
        coroutineScope {
            val killer = launch {
                try {
                    awaitCancellation()
                } finally {
                    withContext(NonCancellable) {
                        ProcessTree.kill(pid.get())
                        process.destroyForcibly()
                    }
                }
            }
            try {
                process.inputStream.bufferedReader().use { reader ->
                    var first = true
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (first) {
                            first = false
                            val shellPid = line.trim().toIntOrNull()
                            if (shellPid != null) {
                                pid.set(shellPid)
                                continue
                            }
                        }
                        ensureActive()
                        onLine(line)
                    }
                }
                val code = process.waitFor()
                errReader.join(3_000)
                RunResult(code, stderr.toString())
            } finally {
                killer.cancel()
                if (process.isAlive) {
                    ProcessTree.kill(pid.get())
                    process.destroyForcibly()
                }
            }
        }
    }

    /** Leftovers of a cancelled or crashed run: keep .part files (they resume), drop the rest. */
    fun cleanWorkDir(keepPartials: Boolean = true) {
        workDir.listFiles()?.forEach { file ->
            val partial = file.name.endsWith(".part") || file.name.endsWith(".ytdl")
            if (!(keepPartials && partial) && file.name != "archive.txt") file.delete()
        }
    }

    companion object {
        private const val TAG = "DalmaEngine"
    }
}

internal fun Throwable.isCancellation(): Boolean = this is CancellationException
