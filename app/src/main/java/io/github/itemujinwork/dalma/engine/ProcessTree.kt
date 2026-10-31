package io.github.itemujinwork.dalma.engine

import android.system.Os
import android.system.OsConstants
import java.io.File

/**
 * yt-dlp starts ffmpeg as a child. When a download is cancelled, both must go, or an
 * orphaned ffmpeg keeps the CPU busy. Android hides other apps' processes in /proc,
 * so everything we can see is ours.
 */
internal object ProcessTree {
    /** Every process descending from [pid], children before parents. */
    fun descendants(pid: Int): List<Int> {
        val children = HashMap<Int, MutableList<Int>>()
        val entries = File("/proc").listFiles() ?: return emptyList()
        for (dir in entries) {
            val child = dir.name.toIntOrNull() ?: continue
            val stat = try {
                File(dir, "stat").readText()
            } catch (_: Exception) {
                continue
            }
            // "pid (comm) state ppid ..." and comm itself may contain spaces or parentheses.
            val fields = stat.substringAfterLast(')').trim().split(' ')
            val ppid = fields.getOrNull(1)?.toIntOrNull() ?: continue
            children.getOrPut(ppid) { mutableListOf() }.add(child)
        }
        val result = mutableListOf<Int>()
        fun walk(parent: Int) {
            children[parent]?.forEach { walk(it); result.add(it) }
        }
        walk(pid)
        return result
    }

    fun kill(pid: Int) {
        if (pid <= 0) return
        for (child in descendants(pid)) quietly { Os.kill(child, OsConstants.SIGKILL) }
        quietly { Os.kill(pid, OsConstants.SIGKILL) }
    }

    private inline fun quietly(block: () -> Unit) {
        try {
            block()
        } catch (_: Exception) {
        }
    }
}
