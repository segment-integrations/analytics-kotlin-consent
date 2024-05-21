package com.segment.analytics.destinations.mydestination.testapp

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import com.google.android.material.snackbar.Snackbar
import java.text.SimpleDateFormat
import java.util.Date

class MainActivity : AppCompatActivity() {

    companion object {
        val TAG = "main-activity"
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val button = findViewById<Button>(R.id.sendTrackEventButton)
        button.setOnClickListener {v ->
            val sdf = SimpleDateFormat("M/dd/yyyy hh:mm:ss.SSS")
            val currentDate = sdf.format(Date())
            MainApplication.analytics.track("Test Event ${currentDate}")

            if (v != null) {
                Snackbar.make(v, "Track Event created.", Snackbar.LENGTH_LONG).show()
            }
        }
    }
}