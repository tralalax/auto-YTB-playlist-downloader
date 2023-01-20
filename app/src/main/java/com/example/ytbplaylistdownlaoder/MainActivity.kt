package com.example.ytbplaylistdownlaoder

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.webkit.URLUtil
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.codekidlabs.storagechooser.StorageChooser
import java.io.File
import java.util.concurrent.TimeUnit

// repo URL
const val githubLink = "https://github.com/tralalax/auto-YTB-playlist-downloader"

@Suppress("DEPRECATION")
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // UI stuff
        val boutonSavePlaylistUrl: Button = findViewById(R.id.btnSavePlaylistUrl)
        val boutonManualSync: Button = findViewById(R.id.btnManualSync)
        val boutonChooseDownloadPath: Button = findViewById(R.id.btnChooseDownloadPath)
        val boutonGithubLink: ImageButton = findViewById(R.id.btnGithubLink)
        val urlUserInput: EditText = findViewById(R.id.etPlaylistUrl)
        val downloadPathSet: TextView = findViewById(R.id.tvDownloadPath)
        val switchToggle: Switch = findViewById(R.id.switchToggle)

        // key paire storage for app settings
        val sharedPref = getSharedPreferences("settingsStorage", MODE_PRIVATE)
        val editor = sharedPref.edit()

        // get playlist URL for the editText Hint
        val setPlaylistUrl: String? = sharedPref.getString("PlaylistURL", null)
        if (setPlaylistUrl != null) {
            urlUserInput.hint = setPlaylistUrl
        }

        // get download path for the text view
        var setDownloadPath: String? = sharedPref.getString("DownloadPath", null)
        if (setDownloadPath != null) {
            downloadPathSet.text = "$setDownloadPath"
        }

        // check app perms
        checkPerm()

        // launch the Periodic Work Request for the background downloader
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .setRequiresStorageNotLow(true)
            .build()
        val downloaderWorkRequest = PeriodicWorkRequestBuilder<PlaylistDownloader>(24, TimeUnit.HOURS)
            .addTag("downloaderCall")
            .setConstraints(constraints)
            .build()

        val switchIsEnable: Boolean = sharedPref.getBoolean("toggleSwitchState",false)
        if (switchIsEnable) {
            WorkManager
                .getInstance(this)
                .enqueueUniquePeriodicWork("PeriodicDownloaderCall",
                    ExistingPeriodicWorkPolicy.REPLACE,
                    downloaderWorkRequest)
        }

        // log created new worker
        //val infoworker = WorkManager.getInstance(this).getWorkInfosByTag("downloaderCall")
        //Log.d("INFOWORKER", "$infoworker")

        // save playlist url
        boutonSavePlaylistUrl.setOnClickListener {
            val playlistUrlInputValue: String = urlUserInput.text.toString()

            if (playlistUrlInputValue.trim() == "") {
                //prevent empty string saving
                Toast.makeText(this, R.string.errorPlaylistUrlSave, Toast.LENGTH_LONG).show()
            }else if (URLUtil.isValidUrl(playlistUrlInputValue)) {
                // input is a valid URL
                if (playlistUrlInputValue.contains("youtube")) {
                    // only get the playlist ID
                    var playlistID = playlistUrlInputValue.split("list=")
                    playlistID = listOf(playlistID[1])

                    // do an extra split to remove anything after the playlist ID
                    if (playlistID.contains("&")) {
                        playlistID = playlistUrlInputValue.split("&")
                        playlistID = listOf(playlistID[1])
                    }

                    // save the playlist ID
                    val playlistIdFinal = playlistID.first()
                    Log.d("MERDESANSNOM","$playlistIdFinal")
                    editor.putString("PlaylistURL", playlistIdFinal)
                    editor.apply()
                    Toast.makeText(this, R.string.playlistUrlSaved, Toast.LENGTH_LONG).show()
                }else {
                    Toast.makeText(this, R.string.errorNonYTBplaylistURL, Toast.LENGTH_LONG).show()
                }
            }else {
                // prevent invalid URL saving
                Toast.makeText(this, R.string.errorInvalidPlaylistUrl, Toast.LENGTH_LONG).show()
            }
        }

        // launch the download folder picker
        boutonChooseDownloadPath.setOnClickListener {
            val chooser = StorageChooser.Builder()
                .withActivity(this)
                .withFragmentManager(fragmentManager) //deprecated !
                .withMemoryBar(true)
                .allowCustomPath(true)
                .setType(StorageChooser.DIRECTORY_CHOOSER)
                .build()

            // get result path from the folder picker
            chooser.setOnSelectListener { path ->
                val f = File(path)
                if (checkInputDownloadPath(path) && f.canWrite()) {
                    // save download path
                    editor.putString("DownloadPath",path)
                    editor.apply()
                    Toast.makeText(this, R.string.downloadPathSaved, Toast.LENGTH_LONG).show()
                    // set text view download path value
                    setDownloadPath = sharedPref.getString("DownloadPath", null)
                    downloadPathSet.text = "$setDownloadPath"
                }else {
                    Toast.makeText(this, R.string.generalErrorMessage, Toast.LENGTH_LONG).show()
                }
            }
            // show the folder picker to the user
            chooser.show()
        }

        // manual downloader launch
        boutonManualSync.setOnClickListener {
            if (setPlaylistUrl != null && setDownloadPath != null) {
                mainDownloader(this)
            }else {
                Toast.makeText(this, R.string.errorNotSelectedURLorPath, Toast.LENGTH_LONG).show()
            }
        }

        // toggle switch
        switchToggle.isChecked = sharedPref.getBoolean("toggleSwitchState",false)
        switchToggle.setOnCheckedChangeListener { _, isChecked ->
            // save the state of the switch
            if (isChecked) {
                if (setPlaylistUrl != null && setDownloadPath != null) {
                    Toast.makeText(this, R.string.switchEnable, Toast.LENGTH_LONG).show()
                    editor.putBoolean("toggleSwitchState",true)
                }else {
                    Toast.makeText(this, R.string.errorNotSelectedURLorPath, Toast.LENGTH_LONG).show()
                    switchToggle.isChecked = false
                }
            }else {
                Toast.makeText(this, R.string.switchDisable, Toast.LENGTH_LONG).show()
                editor.putBoolean("toggleSwitchState",false)
                // cancel the periodic download request
                WorkManager
                    .getInstance(this)
                    .cancelAllWorkByTag("PeriodicDownloaderCall")
            }
            editor.apply()
        }

        // github button, open the repo link in a web browser
        boutonGithubLink.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = Uri.parse(githubLink)
            startActivity(intent)
        }

    }


    // check permission
    fun checkPerm() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            // write external storage perm
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 1)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            // read external storage perm
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 2)
        }
    }

    // check if edit text input for download path is correct
    @Suppress("NAME_SHADOWING")
    fun checkInputDownloadPath(pathInput: String): Boolean {
        val pathInput = File(pathInput)
        return pathInput.isDirectory
    }

/*
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        when (requestCode) {
            REQUEST_WRITE_EXTERNAL_STORAGE -> {
                // permission request is granted (happy)
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "Permission granted", Toast.LENGTH_SHORT).show()
                } else {
                    // permission request is denied (sad)
                    Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
*/

}
