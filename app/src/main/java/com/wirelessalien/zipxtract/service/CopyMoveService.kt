/*
 *  Copyright (C) 2023  WirelessAlien <https://github.com/WirelessAlien>
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.wirelessalien.zipxtract.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.wirelessalien.zipxtract.R
import com.wirelessalien.zipxtract.constant.BroadcastConstants.COPY_MOVE_NOTIFICATION_CHANNEL_ID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class CopyMoveService : Service() {

    companion object {
        const val EXTRA_FILES_TO_COPY_MOVE = "extra_files_to_copy_move"
        const val EXTRA_DESTINATION_PATH = "extra_destination_path"
        const val EXTRA_IS_COPY_ACTION = "extra_is_copy_action"
        const val NOTIFICATION_ID = 2
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val filesToCopyMove = intent?.getStringArrayListExtra(EXTRA_FILES_TO_COPY_MOVE)?.map { File(it) } ?: return START_NOT_STICKY
        val destinationPath = intent.getStringExtra(EXTRA_DESTINATION_PATH) ?: return START_NOT_STICKY
        val isCopyAction = intent.getBooleanExtra(EXTRA_IS_COPY_ACTION, true)

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification(0, filesToCopyMove.size, isCopyAction))

        CoroutineScope(Dispatchers.IO).launch {
            copyMoveFiles(filesToCopyMove, destinationPath, isCopyAction)
        }

        return START_STICKY
    }

    private fun countTotalFiles(files: List<File>): Int {
        var count = 0
        for (file in files) {
            if (file.isDirectory) {
                count += countTotalFiles(file.listFiles()?.toList() ?: emptyList())
            } else {
                count++
            }
        }
        return count
    }

    private fun copyMoveFiles(files: List<File>, destinationPath: String, isCopyAction: Boolean) {
        val totalFilesCount = countTotalFiles(files)
        var processedFilesCount = 0

        fun copyMoveFile(file: File, destination: File) {
            if (file.isDirectory) {
                file.listFiles()?.forEach { copyMoveFile(it, File(destination, it.name)) }
            } else {
                if (isCopyAction) {
                    file.copyTo(destination, overwrite = true)
                } else {
                    file.moveTo(destination, overwrite = true)
                }
                processedFilesCount++
                updateNotification(processedFilesCount, totalFilesCount, isCopyAction)
            }
        }

        for (file in files) {
            val destinationFile = File(destinationPath, file.name)
            if (file.absolutePath == destinationFile.absolutePath) {
                continue // Skip if the source and destination paths are the same
            }
            copyMoveFile(file, destinationFile)
        }

        stopForegroundService()
        stopSelf()
    }

    private fun File.moveTo(destination: File, overwrite: Boolean = false) {
        if (overwrite && destination.exists()) {
            destination.deleteRecursively()
        }
        this.copyRecursively(destination, overwrite = true)
        this.deleteRecursively()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                COPY_MOVE_NOTIFICATION_CHANNEL_ID,
                getString(R.string.copy_move_files_notification_name),
                NotificationManager.IMPORTANCE_LOW
            )
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(progress: Int, total: Int, isCopyAction: Boolean): Notification {
        val title = if (isCopyAction) {
            getString(R.string.copying_files)
        } else {
            getString(R.string.moving_files)
        }
        val contentText = if (isCopyAction) {
            getString(R.string.copying_files_progress, progress, total)
        } else {
            getString(R.string.moving_files_progress, progress, total)
        }

        return NotificationCompat.Builder(this, COPY_MOVE_NOTIFICATION_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_notification_icon)
            .setProgress(total, progress, false)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(progress: Int, total: Int, isCopyAction: Boolean) {
        val notification = createNotification(progress, total, isCopyAction)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun stopForegroundService() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.cancel(Archive7zService.NOTIFICATION_ID)
    }
}