package com.lightness.boostapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File
import kotlin.math.pow

class MainActivity : AppCompatActivity() {

    private lateinit var audioBooster: AudioBooster
    private lateinit var autoRecordBtn: Button
    private lateinit var fileBtn: Button
    private lateinit var boostText: TextView
    private lateinit var statusText: TextView

    private val PERMISSION_REQUEST_CODE = 100
    private val FILE_PICK_REQUEST_CODE = 200
    private var isRecording = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        audioBooster = AudioBooster(getSystemService(AudioManager::class.java) as AudioManager)

        autoRecordBtn = findViewById(R.id.autoRecordButton)
        fileBtn = findViewById(R.id.fileButton)
        boostText = findViewById(R.id.boostText)
        statusText = findViewById(R.id.statusText)

        val boostSlider = findViewById<SeekBar>(R.id.boostSlider)

        if (!hasPermissions()) {
            requestPermissions()
        }

        boostSlider.max = 100
        boostSlider.progress = 30 // default ~5x

        boostSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val logScale = (progress / 100.0) * 4.0
                val boostValue = 10.0.pow(logScale).toFloat()

                audioBooster.boostLevel = boostValue

                val displayValue = String.format("%.0f", boostValue)
                boostText.text = "🔊 Boost: ${displayValue}x"

                when {
                    boostValue <= 10f -> boostText.setTextColor(getColor(R.color.boost_low))
                    boostValue <= 100f -> boostText.setTextColor(getColor(R.color.boost_medium))
                    boostValue <= 1000f -> boostText.setTextColor(getColor(R.color.boost_high))
                    else -> boostText.setTextColor(getColor(R.color.boost_extreme))
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        autoRecordBtn.setOnClickListener {
            if (!isRecording) {
                startAutoRecord()
            }
        }

        fileBtn.setOnClickListener {
            pickAudioFile()
        }
    }

    private fun hasPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ),
            PERMISSION_REQUEST_CODE
        )
    }

    private fun startAutoRecord() {
        isRecording = true
        autoRecordBtn.isEnabled = false
        autoRecordBtn.text = "🎤 RECORDING..."
        autoRecordBtn.setBackgroundColor(getColor(android.R.color.holo_red_dark))
        statusText.text = "Listening..."

        audioBooster.startAutoRecord(durationMs = 2000) {
            isRecording = false
            autoRecordBtn.isEnabled = true
            autoRecordBtn.text = "🎤 AUTO RECORD"
            autoRecordBtn.setBackgroundColor(getColor(R.color.red_primary))
            statusText.text = "✅ Playback complete!"
        }

        Toast.makeText(this, "Speak now for 2 seconds", Toast.LENGTH_SHORT).show()
    }

    private fun pickAudioFile() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "audio/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        startActivityForResult(intent, FILE_PICK_REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == FILE_PICK_REQUEST_CODE && resultCode == RESULT_OK) {
            val uri = data?.data
            if (uri != null) {
                playSelectedFile(uri)
            }
        }
    }

    private fun playSelectedFile(uri: Uri) {
        fileBtn.isEnabled = false
        fileBtn.text = "🎵 PLAYING..."
        fileBtn.setBackgroundColor(getColor(android.R.color.holo_red_dark))
        statusText.text = "Boosting file..."

        val filePath = getRealPathFromURI(uri)
        if (filePath != null) {
            audioBooster.playAudioFileWithBoost(filePath) {
                fileBtn.isEnabled = true
                fileBtn.text = "📁 ADD FILE"
                fileBtn.setBackgroundColor(getColor(R.color.red_primary))
                statusText.text = "✅ File playback done!"
            }
        } else {
            fileBtn.isEnabled = true
            fileBtn.text = "📁 ADD FILE"
            fileBtn.setBackgroundColor(getColor(R.color.red_primary))
            Toast.makeText(this, "Failed to read file", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getRealPathFromURI(uri: Uri): String? {
        return try {
            val inputStream = contentResolver.openInputStream(uri)
            val tempFile = File.createTempFile("audio_", ".wav", cacheDir)
            inputStream?.copyTo(tempFile.outputStream())
            tempFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE && grantResults.isNotEmpty()) {
            if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Mic permission required", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
