package com.hearxgroup.nativeusb.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.Bundle
import android.os.Environment
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.widget.TextView
import androidx.lifecycle.Observer
import com.hearxgroup.nativeusb.R
import com.hearxgroup.nativeusb.USBActivity
import kotlinx.android.synthetic.main.activity_raw.*
import java.io.File

class RawActivity : USBActivity() {

    private val TAG = RawActivity::class.java.simpleName

    //private var serial_output: TextView? = null

    private var soundPool: SoundPool? = null
    private lateinit var audioManager: AudioManager
    private var sampleId: Int = -1
    private var streamId: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_raw)

        setupUI()
        //setupAudioPlayer()

        val activeObserver = Observer<DacStatus> {
            tv_conn_status.text = "Connection Status: ${it.name}"
        }

        dacStatus.observe(this, activeObserver)

        val activeObserver2 = Observer<String> {
            tv_sent.text = "Wrote: $it"
        }

        dacComm.observe(this, activeObserver2)
    }

    private fun setupUI() {
        //tv_usb_output.movementMethod= ScrollingMovementMethod()
        //this.serial_output = tv_usb_output

        btn_clear.setOnClickListener {
            //tv_usb_output.text = ""
            edt_id_msb.setText("")
            edt_id_lsb.setText("")
            edt_payload_0.setText("")
            edt_payload_1.setText("")
            edt_payload_2.setText("")
            edt_payload_3.setText("")
            edt_payload_4.setText("")
            edt_payload_5.setText("")
            edt_payload_6.setText("")
            edt_payload_7.setText("")
            edt_payload_8.setText("")
            edt_payload_9.setText("")
        }

        btn_send.setOnClickListener {
            //serial_output!!.text = ""
            sendSerialCommand()
        }

        tv_conn_status.text = "Connection Status: Disconnected"

        btn_play_start.setOnClickListener {
            setupAudioPlayer()
        }

        btn_play_stop.setOnClickListener {
            soundPool?.stop(streamId)
            soundPool?.release()
            streamId = -1
        }

        iv_tone_reset.setOnClickListener {
            loadToneFileForPlayback()
        }
    }

    private fun sendSerialCommand() {

        Log.d(TAG, "sendSerialCommand()")

        var messageIdMSB = edt_id_msb.text.toString().trim()
        var messageIdLSB = edt_id_lsb.text.toString().trim()

        if(messageIdMSB.isNullOrEmpty())
            messageIdMSB = "0"

        if(messageIdLSB.isNullOrEmpty())
            messageIdLSB = "0"

        val payloadList = mutableListOf<String>()

        if(edt_payload_0.text.toString().isNotEmpty())
            payloadList.add(edt_payload_0.text.toString())

        if(edt_payload_1.text.toString().isNotEmpty())
            payloadList.add(edt_payload_1.text.toString())

        if(edt_payload_2.text.toString().isNotEmpty())
            payloadList.add(edt_payload_2.text.toString())

        if(edt_payload_3.text.toString().isNotEmpty())
            payloadList.add(edt_payload_3.text.toString())

        if(edt_payload_4.text.toString().isNotEmpty())
            payloadList.add(edt_payload_4.text.toString())

        if(edt_payload_5.text.toString().isNotEmpty())
            payloadList.add(edt_payload_5.text.toString())

        if(edt_payload_6.text.toString().isNotEmpty())
            payloadList.add(edt_payload_6.text.toString())

        if(edt_payload_7.text.toString().isNotEmpty())
            payloadList.add(edt_payload_7.text.toString())

        if(edt_payload_8.text.toString().isNotEmpty())
            payloadList.add(edt_payload_8.text.toString())

        if(edt_payload_9.text.toString().isNotEmpty())
            payloadList.add(edt_payload_9.text.toString())

        usbService?.writeCommand(
                payloadList = payloadList,
                messageIdMSB = messageIdMSB,
                messageIdLSB = messageIdLSB
        )
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

    private fun loadToneFileForPlayback() : Int {
        Log.d(TAG, "loadFileForPlayback")
        audioManager.setStreamVolume(
                AudioManager.STREAM_MUSIC,
                audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC),
                0
        )

        val filePath = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DCIM
        ).toString()+ File.separator+"raw-dac/tone_f${edt_tone_freq.text}hz_pos_${edt_tone_int.text}.ogg"
        Log.d(TAG, "filePath=$filePath")
        return soundPool!!.load(filePath, 1)
    }
}