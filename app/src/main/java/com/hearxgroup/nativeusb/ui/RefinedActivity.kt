package com.hearxgroup.nativeusb.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.lifecycle.Observer
import com.hearxgroup.nativeusb.R
import com.hearxgroup.nativeusb.USBActivity
import com.hearxgroup.nativeusb.utils.USBCommandUtil
import kotlinx.android.synthetic.main.activity_raw.*
import kotlinx.android.synthetic.main.activity_refined.*
import kotlinx.android.synthetic.main.activity_refined.btn_play_start
import kotlinx.android.synthetic.main.activity_refined.btn_play_stop
import kotlinx.android.synthetic.main.activity_refined.tv_conn_status
import java.io.File

class RefinedActivity : USBActivity() {

    private val TAG = RefinedActivity::class.java.simpleName
    private enum class CHANNEL{LEFT, RIGHT}
    private var soundPool: SoundPool? = null
    private lateinit var audioManager: AudioManager
    private var sampleId: Int = -1
    private var streamId: Int = -1
    private var dacVersionV3 = true //v2:PT2259 v3:PGA2311
    private var channel = CHANNEL.LEFT


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_refined)

        setupUI()
       // setupAudioPlayer()

        val activeObserver = Observer<DacStatus> {
            tv_conn_status.text = "Connection Status: ${it.name}"
            btn_send_command.visibility = if(it==DacStatus.CONNECTED) View.VISIBLE else View.GONE
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
                        if(channel==CHANNEL.LEFT) 1.0f else 0.0f,
                        if(channel==CHANNEL.RIGHT) 1.0f else 0.0f,
                        1,
                        -1,
                        1.0f
                ) ?: -1
            }
        }

        loadToneFileForPlayback(spin_tone_freq.selectedItem as Int, spin_tone_intensity.selectedItem as Int)
    }

    private fun setupUI() {

        tv_conn_status.text = "Connection Status: Disconnected"

        btn_send_command.setOnClickListener { // Makes dual channel 40dB att
            //todo adjust for DAC version
            //todo adjust for channel selected
            val payload = if(!dacVersionV3) {
                //DAC V2 TODO
                emptyList<String>()
            } else {
                //DAC V3
                val attenuationLeft = spin_att_left.selectedItem as Int
                val attenuationRight = spin_att_right.selectedItem as Int
                val nLeft = (((attenuationLeft - 31.5)/(-0.5))-255)*-1
                Log.d(TAG, "nLeft=$nLeft")
                val nRight = (((attenuationRight - 31.5)/(-0.5))-255)*-1
                Log.d(TAG, "nRight=$nRight")
                val nLeftHex = USBCommandUtil.attCalcPGA2311(attenuationLeft)
                val nRightHex = USBCommandUtil.attCalcPGA2311(attenuationRight)
                Log.d(TAG, "nLeftHex=$nLeftHex")
                Log.d(TAG, "nRightHex=$nRightHex")

                listOf(
                        "01",
                        "50",
                        "00",
                        "03",
                        "01",
                        nRightHex,
                        nLeftHex
                )
            }

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

        switch_tone_channel.setOnCheckedChangeListener { buttonView, isChecked ->
            channel = if(isChecked) CHANNEL.RIGHT else CHANNEL.LEFT
            tv_tone_channel.text = "Intended Channel: ${if(isChecked) "R" else "L"}"
        }

        setupSpinners()
    }

    private fun setupSpinners() {
        val adapterAttLeft = ArrayAdapter(this, android.R.layout.simple_spinner_item, (31 downTo -95).toList())
        val adapterAttRight = ArrayAdapter(this, android.R.layout.simple_spinner_item, (31 downTo -95).toList())
        val adapterFileFreq = ArrayAdapter(this, android.R.layout.simple_spinner_item, listOf(125, 250, 500, 750, 1000, 1500, 2000, 3000, 4000, 6000, 8000, 10000, 12500, 16000))
        val adapterFileIntensity = ArrayAdapter(this, android.R.layout.simple_spinner_item, (90 downTo -10).toList())

        spin_att_left.adapter = adapterAttLeft
        spin_att_right.adapter = adapterAttRight
        spin_tone_freq.adapter = adapterFileFreq
        spin_tone_intensity.adapter = adapterFileIntensity
    }

    private fun loadToneFileForPlayback(freq: Int, intensity: Int) : Int {
        Log.d(TAG, "loadFileForPlayback")
        Log.d(TAG, "freq=$freq  intensity=$intensity")
        audioManager.setStreamVolume(
                AudioManager.STREAM_MUSIC,
                audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC),
                0
        )

        val filePath = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DCIM
        ).toString()+ File.separator+"raw-dac/tone_f${freq}hz_pos_${intensity}.ogg"
        Log.d(TAG, "filePath=$filePath")
        return soundPool!!.load(filePath, 1)
    }

/*
    private var v2CommandClearAtt = listOf(
            "1", //opening bit
            "88", //PT2259 chip address
            "0", //not sure
            "5", //not sure
            "88", //PT2259 chip address
            "F0", //Clear register * required
            "74", //Mute code (Mute off)
            "E0", //10dB code (0dB att dual)
            "D0" //1dB code (0dB att dual)
    )

    private var v3CommandClearAtt = listOf(
            "1", //opening bit
            "50", //PGA2311 chip address
            "0", //not sure
            "5", //not sure
            "50", //PGA2311 chip address
            "F0", //Clear register * required
            "74", //Mute code (Mute off)
            "E0", //10dB code (0dB att dual)
            "D0" //1dB code (0dB att dual)
    )

    private var v2CommandRight50Att = listOf(
            "1", //opening bit
            "88", //PT2259 chip address
            "0", //not sure
            "5", //not sure
            "88", //PT2259 chip address
            "F0", //Clear register * required
            "76", //Mute code (Left mute)
            "65", //10dB code (50dB att right)
            "20" //1dB code (0dB att right)
    )

    private var v3CommandRight50Att = listOf(
            "01",
            "50",
            "00",
            "03",
            "01", //PGA2311 chip address
            "00",
            "98"
    )

    private var v2CommandLeft50Att = listOf(
            "1", //opening bit
            "88", //PT2259 chip address
            "0", //not sure
            "5", //not sure
            "88", //PT2259 chip address
            "F0", //Clear register * required
            "75", //Mute code (Right mute)
            "B5", //10dB code (50dB att left)
            "A0" //1dB code (0dB att left)
    )

    private var v3CommandLeft50Att = listOf(
            "01",
            "50",
            "00",
            "03",
            "01", //PGA2311 chip address
            "98",
            "00"
    )*/
}