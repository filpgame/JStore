package com.aurora.store.data.room.download

import android.content.Context
import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.aurora.extensions.requiresGMS
import com.aurora.gplayapi.data.models.App
import com.aurora.gplayapi.data.models.PlayFile
import com.aurora.store.data.model.DownloadStatus
import com.aurora.store.data.room.suite.ExternalApk
import com.aurora.store.data.room.update.Update
import com.aurora.store.util.PathUtil
import java.util.Date
import kotlinx.parcelize.Parcelize

@Parcelize
@Entity(tableName = "download")
data class Download(
    @PrimaryKey val packageName: String,
    val versionCode: Long,
    val offerType: Int,
    val isInstalled: Boolean,
    val displayName: String,
    val iconURL: String,
    val size: Long,
    val id: Int,
    @ColumnInfo("downloadStatus")
    var status: DownloadStatus,
    var progress: Int,
    var speed: Long,
    var timeRemaining: Long,
    var totalFiles: Int,
    var downloadedFiles: Int,
    var fileList: List<PlayFile>,
    val sharedLibs: List<SharedLib>,
    val targetSdk: Int = 1,
    val downloadedAt: Long = 0,
    val requiresGMS: Boolean = false
) : Parcelable {
    val isFinished get() = status in DownloadStatus.finished
    val isRunning get() = status in DownloadStatus.running
    private val isSuccessful get() = status == DownloadStatus.COMPLETED

    /**
     * `true` while the download is queued, purchasing, downloading or verifying, i.e.
     * the pipeline is actively working on it. Unlike [isRunning] this also covers
     * [DownloadStatus.VERIFYING], which sits between downloading and completion.
     */
    val isActive get() = isRunning || status == DownloadStatus.VERIFYING

    companion object {
        fun fromApp(app: App): Download = Download(
            app.packageName,
            app.versionCode,
            app.offerType,
            app.isInstalled,
            app.displayName,
            app.iconArtwork.url,
            app.size,
            app.id,
            DownloadStatus.QUEUED,
            0,
            0L,
            0L,
            0,
            0,
            app.fileList.filterNot { it.url.isBlank() },
            app.dependencies.dependentLibraries.map { SharedLib.fromApp(it) },
            app.targetSdk,
            Date().time,
            app.requiresGMS()
        )

        fun fromUpdate(update: Update): Download = Download(
            update.packageName,
            update.versionCode,
            update.offerType,
            true,
            update.displayName,
            update.iconURL,
            update.size,
            update.id,
            DownloadStatus.QUEUED,
            0,
            0L,
            0L,
            0,
            0,
            update.fileList,
            update.sharedLibs,
            update.targetSdk,
            Date().time
        )

        fun fromExternalApk(externalApk: ExternalApk, isInstalled: Boolean): Download = Download(
            packageName = externalApk.packageName,
            versionCode = externalApk.versionCode,
            offerType = 0,
            isInstalled = isInstalled,
            displayName = externalApk.displayName,
            iconURL = externalApk.iconURL,
            size = externalApk.fileList.sumOf { it.size },
            id = 0,
            status = DownloadStatus.QUEUED,
            progress = 0,
            speed = 0L,
            timeRemaining = 0L,
            totalFiles = 1,
            downloadedFiles = 0,
            fileList = externalApk.fileList,
            sharedLibs = emptyList(),
            downloadedAt = Date().time
        )
    }

    fun canInstall(context: Context): Boolean {
        if (!isSuccessful) return false
        val dir = PathUtil.getAppDownloadDir(context, packageName, versionCode)
        // Require at least one actual APK on disk, not just that the directory exists —
        // an empty/partially-cleaned directory must not look installable.
        return dir.listFiles()?.any { it.name.endsWith(".apk") } == true
    }

    fun hasSameArtifactAs(other: Download): Boolean {
        if (packageName != other.packageName || versionCode != other.versionCode) return false
        if (other.fileList.isEmpty()) return true
        if (fileList.size != other.fileList.size) return false

        val currentFiles = fileList.sortedBy { it.name }
        val replacementFiles = other.fileList.sortedBy { it.name }
        return currentFiles.zip(replacementFiles).all { (file, otherFile) ->
            file.name == otherFile.name &&
                file.size == otherFile.size &&
                when {
                    otherFile.sha256.isNotBlank() ->
                        file.sha256.equals(otherFile.sha256, ignoreCase = true)
                    otherFile.sha1.isNotBlank() ->
                        file.sha1.equals(otherFile.sha1, ignoreCase = true)
                    else -> file.url == otherFile.url
                }
        }
    }
}
