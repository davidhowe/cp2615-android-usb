package com.hearxgroup.nativeusb.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.View
import android.widget.Toast
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
    private var dacVersionV3 = false //v2:PT2259 v3:PGA2311
    private var channel = CHANNEL.LEFT

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
            "00",
            "03",
            "01",
            //"50", //PGA2311 chip address
            //"01",
            "98",
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
            "1", //opening bit
            "50", //PGA2311 chip address
            "0", //not sure
            "5", //not sure
            "50", //PGA2311 chip address
            "F0", //Clear register * required
            "75", //Mute code (Right mute)
            "B5", //10dB code (50dB att left)
            "A0" //1dB code (0dB att left)
    )


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
            val payload = if(!dacVersionV3) {
                v2CommandClearAtt
            } else {
                v3CommandClearAtt
            }
            val payloadSize = listOf(payload.size.toString())
            val payloadList = payload+payloadSize
            usbService?.writeCommand(
                    payloadList = payloadList,
                    messageIdMSB = "D4",
                    messageIdLSB = "00"
            )
        }

        btn_attn_minus.setOnClickListener { // Makes dual channel 40dB att
            if(dacVersionV3) {
                /*val payload = emptyList<String>() //todo DAC v3
                val payloadSize = listOf(payload.size.toString())
                val payloadList = payload + payloadSize
                usbService?.writeCommand(
                        payloadList = payloadList,
                        messageIdMSB = "D4",
                        messageIdLSB = "00"
                )*/
                Toast.makeText(this, "TODO", Toast.LENGTH_LONG).show()

            } else {
                Toast.makeText(this, "Not relevant for v2 DAC", Toast.LENGTH_LONG).show()
            }
        }

        btn_attn_plus.setOnClickListener { // Makes dual channel 40dB att
            //todo adjust for DAC version
            //todo adjust for channel selected
            val payload = if(!dacVersionV3) {
                if(channel==CHANNEL.LEFT)
                    v2CommandLeft50Att
                else
                    v2CommandRight50Att
            } else {
                if(channel==CHANNEL.LEFT)
                    v3CommandLeft50Att
                else
                    v3CommandRight50Att
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

        sw_dac_version.setOnCheckedChangeListener { buttonView, isChecked ->
            dacVersionV3 = isChecked
            tv_dac_version.text = "DAC Version: ${if(dacVersionV3) "V3" else "V2"}"
        }

        sw_channel.setOnCheckedChangeListener { buttonView, isChecked ->
            channel = if(isChecked)
                CHANNEL.RIGHT
            else
                CHANNEL.LEFT
            tv_channel.text = "Channel: ${channel.name}"
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