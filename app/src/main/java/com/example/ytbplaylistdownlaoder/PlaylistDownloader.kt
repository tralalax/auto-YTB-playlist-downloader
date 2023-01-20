package com.example.ytbplaylistdownlaoder

import android.content.Context
import android.os.PowerManager
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDLRequest
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.Executors


class PlaylistDownloader(appContext: Context, workerParams: WorkerParameters): Worker(appContext, workerParams) {
    val appContext = appContext

    // call when Periodic Work Request trigger
    override fun doWork(): Result {

        mainDownloader(appContext)

        return Result.success()
    }
}

// Youtube API key
val YTBapiKey = "AIzaSyBXIEaoElA5td04LgZQgPB-c8GPbGPl81Y"
// base video URL
var baseVideoUrl = "https://www.youtube.com/watch?v="
// empty list for the video to download
val videoToDl = mutableSetOf<String>()
// empty set of string to store the http request
val requestResponseIdDump = mutableSetOf<String>()
// max number of video to dl
val dlLimit = 50


fun mainDownloader(appContext: Context) {

    Log.d("DEBUG", "HELLO FROM MAIN DOWNLOADER")

    // acquire a Wake Lock to keep the CPU running
    val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as PowerManager
    val wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MyApp:WakeLock")
    wakeLock.acquire(10 * 60 * 1000L /*10 minutes*/)

    // get key paire storage for app settings
    val sharedPref = appContext.getSharedPreferences("settingsStorage", AppCompatActivity.MODE_PRIVATE)
    val editor = sharedPref.edit()
    // get http request client
    val httpClient = OkHttpClient()
    // get executor to do the http request on a new thread
    val executor = Executors.newSingleThreadExecutor()
    // get YTB downloader
    try {
        YoutubeDL.getInstance().init(appContext)
    } catch (e: YoutubeDLException) {
        Toast.makeText(appContext, "YoutubeDL-ERROR : $e", Toast.LENGTH_LONG).show()
    }

    // Playlist URL
    val playlistID: String? = sharedPref.getString("PlaylistURL", null)
    // download path
    val setDownloadPath: String? = sharedPref.getString("DownloadPath", null)
    // get the previous request result
    val alreadyStoredDataRequestResponse = sharedPref.getStringSet("requestResponseIdDump", null)

    // API URL request
    val YTBAPIURLrequest = "https://youtube.googleapis.com/youtube/v3/playlistItems?part=contentDetails"+
            "&maxResults=$dlLimit"+
            "&playlistId=$playlistID"+
            "&key=$YTBapiKey"


    // execute the OkHttp request and list comparison on a new thread
    executor.execute {
        // build the OkHttp request
        val finalRequest = Request.Builder()
            .url(YTBAPIURLrequest)
            .build()

        // execute the request and get result
        val requestResponse = httpClient.newCall(finalRequest).execute()
        val jsonRequestResponseItems = JSONObject(requestResponse.body!!.string()).getJSONArray("items")

        for (i in 0 until jsonRequestResponseItems.length()) {
            // store all of the ID from the YTB API response in requestResponseIdDump
            val videoId = jsonRequestResponseItems.getJSONObject(i).getJSONObject("contentDetails").getString("videoId")
            requestResponseIdDump.add(videoId)
        }

        // check if we already stored value from a previous request
        if (alreadyStoredDataRequestResponse != null) {
            // requestResponseIdDump is not empty -> compare the http request result to it
            for (i in requestResponseIdDump) {
                if (!alreadyStoredDataRequestResponse.contains(i)) {
                    // add the video ID i in the videoToDl list
                    videoToDl.add(i)
                }
            }
            // add the ID to download to the previous request set
            requestResponseIdDump.addAll(videoToDl)
            editor.putStringSet("requestResponseIdDump",requestResponseIdDump)
            editor.apply()
        }else {
            // requestResponseIdDump is empty, store the set
            editor.putStringSet("requestResponseIdDump",requestResponseIdDump)
            editor.apply()
            // add all the ID to the videoDL list
            for (i in 0 until jsonRequestResponseItems.length()) {
                val videoId = jsonRequestResponseItems.getJSONObject(i).getJSONObject("contentDetails").getString("videoId")
                videoToDl.add(videoId)
            }
        }
    }

    // FOR TESTING
    //videoToDl.add("tr8prrrUH_A")
    //Log.d("DEBUG", "$videoToDl")

    // start downloading
    if (setDownloadPath != null && videoToDl.isNotEmpty()) {
        for (i in videoToDl) {
            // form the url of a video and make the request to download it
            val urlToDownload: String = baseVideoUrl + i

            Log.d("DEBUG", "$urlToDownload")
            Log.d("DEBUG", "$setDownloadPath")

            // build the download request
            val request = YoutubeDLRequest(urlToDownload)
            request.addOption("-f", "bestaudio[ext=m4a]")
            request.addOption("-o", "$setDownloadPath/%(title)s.%(ext)s")

            // download the video
            try {
                YoutubeDL.getInstance()
                    .execute(request, "MyProcessDL") { progress, _, _ ->
                        Log.d("YoutubeDL", "prog : $progress")
                    }
            } catch (e: Exception) {
                Log.e("YoutubeDL-ERROR", "error on execute request : $e")
            }
        }
        videoToDl.clear()
    }

    wakeLock.release()
}

