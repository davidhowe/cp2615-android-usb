package com.hearxgroup.nativeusb.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.lifecycle.Observer
import com.hearxgroup.nativeusb.R
import com.hearxgroup.nativeusb.USBActivity
import kotlinx.android.synthetic.main.activity_refined.*
import java.io.File

class RefinedActivity : USBActivity() {

    private val TAG = RefinedActivity::class.java.simpleName
    private var soundPool: SoundPool? = null
    private lateinit var audioManager: AudioManager
    private var sampleId: Int = -1
    private var streamId: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_refined)

        setupUI()
        setupAudioPlayer()

        val activeObserver = Observer<Boolean> {
            rlyt_root.visibility = if(it) View.VISIBLE else View.GONE
        }

        dacActive.observe(this, activeObserver)
    }

    private fun setupAudioPlayer() {
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()

        soundPool = SoundPool.Builder()
                .setAudioAttributes(attributes)
                .setMaxStreams(10)
                .build()

        soundPool!!.setOnLoadCompleteListener { soundPool, sampleId, status ->
            Log.d("", "sampleId=$sampleId")
            Log.d("", "sampleId=$sampleId")
            this.sampleId = sampleId
            if(streamId==-1) {
                streamId = soundPool?.play(
                        sampleId,
                        1.0f,
                        1.0f,
                        1,
                        -1,
                        1.0f
                ) ?: -1
            }
        }

        loadToneFileForPlayback()
    }

    private fun setupUI() {

        btn_reconnect_device.setOnClickListener {
            usbService?.reconnectDevice()
        }

        btn_reset_device.setOnClickListener {
            usbService?.resetDevice()
        }

        btn_claim_interface.setOnClickListener {
            usbService?.claimInterface()
        }

        btn_remove_interface.setOnClickListener {
            usbService?.removeInterface()
        }

        btn_clear_register.setOnClickListener { // Makes dual channel 0dB att
            val payload = listOf("1", "88", "0", "5", "88", "F0", "74", "E0", "D0")
            val payloadSize = listOf(payload.size.toString())
            val payloadList = payload+payloadSize
            usbService?.writeCommand(
                    payloadList = payloadList,
                    messageIdMSB = "D4",
                    messageIdLSB = "00"
            )
        }

        btn_attn_minus.setOnClickListener { // Makes dual channel 40dB att
            val payload = listOf("1", "88", "0", "5", "88", "F0", "74", "E5", "D0")
            val payloadSize = listOf(payload.size.toString())
            val payloadList = payload+payloadSize
            usbService?.writeCommand(
                    payloadList = payloadList,
                    messageIdMSB = "D4",
                    messageIdLSB = "00"
            )
        }

        btn_play_start.setOnClickListener {
            setupAudioPlayer()
        }

        btn_play_stop.setOnClickListener {
            soundPool?.stop(streamId)
            soundPool?.release()
            streamId = -1
        }
    }

    private fun loadToneFileForPlayback() : Int {
        Log.d(TAG, "loadFileForPlayback")
        audioManager.setStreamVolume(
                AudioManager.STREAM_MUSIC,
                audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC),
                0
        )

        val filePath = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DCIM
        ).toString()+ File.separator+"HearXTones/1608797934399/l5-audio-hda300/dac-v2/puretone/tone_f2000hz_pos_70.ogg"
        Log.d(TAG, "filePath=$filePath")
        return soundPool!!.load(filePath, 1)
    }
}