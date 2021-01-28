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
    private enum class CHANNEL{LEFT, RIGHT}
    private var soundPool: SoundPool? = null
    private lateinit var audioManager: AudioManager
    private var sampleId: Int = -1
    private var streamId: Int = -1
    private var dacVersionV3 = false
    private var channel = CHANNEL.LEFT

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_refined)

        setupUI()
        setupAudioPlayer()

        val activeObserver = Observer<DacStatus> {
            if(it==DacStatus.CONNECTED) {
                rlyt_root.visibility =  View.VISIBLE
            } else {
                 View.GONE
            }
            tv_conn_status.text = "Connection Status: ${it.name}"
        }

        dacStatus.observe(this, activeObserver)
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

        /*btn_claim_interface.setOnClickListener {
            usbService?.claimInterface()
        }

        btn_remove_interface.setOnClickListener {
            usbService?.removeInterface()
        }*/

        tv_conn_status.text = "Connection Status: Disconnected"

        btn_clear_register.setOnClickListener { // Makes dual channel 0dB att
            //todo adjust for DAC version
            //todo adjust for channel selected
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
            //todo adjust for DAC version
            //todo adjust for channel selected
            val payload = listOf("1", "88", "0", "5", "88", "F0", "74", "E5", "D0")
            val payloadSize = listOf(payload.size.toString())
            val payloadList = payload+payloadSize
            usbService?.writeCommand(
                    payloadList = payloadList,
                    messageIdMSB = "D4",
                    messageIdLSB = "00"
            )
        }

        btn_attn_plus.setOnClickListener { // Makes dual channel 40dB att
            //todo adjust for DAC version
            //todo adjust for channel selected
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

        sw_dac_version.setOnCheckedChangeListener { buttonView, isChecked ->
            dacVersionV3 = isChecked
            tv_conn_status.text = "DAC Version: ${if(dacVersionV3) "V3" else "V2"}"
        }

        sw_channel.setOnCheckedChangeListener { buttonView, isChecked ->
            channel = if(isChecked)
                CHANNEL.RIGHT
            else
                CHANNEL.LEFT
            tv_conn_status.text = "Channel: ${channel.name}"
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