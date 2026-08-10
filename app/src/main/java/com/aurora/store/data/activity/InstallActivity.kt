package com.aurora.store.data.activity

import android.os.Bundle
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.IntentCompat
import com.aurora.Constants
import com.aurora.extensions.TAG
import com.aurora.store.data.installer.AppInstaller
import com.aurora.store.data.room.download.Download
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class InstallActivity : AppCompatActivity() {

    @Inject
    lateinit var appInstaller: AppInstaller

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val download =
            IntentCompat.getParcelableExtra(intent, Constants.PARCEL_DOWNLOAD, Download::class.java)

        if (download != null) {
            install(download)
        } else {
            Log.e(TAG, "InstallActivity triggered without a valid download, bailing out!")
            finish()
        }
    }

    private fun install(download: Download) {
        try {
            appInstaller.getPreferredInstaller(notifyOnFallback = true).install(download)
            finish()
        } catch (exception: Exception) {
            Log.e(TAG, "Failed to install ${download.packageName}", exception)
            finish()
        }
    }
}
