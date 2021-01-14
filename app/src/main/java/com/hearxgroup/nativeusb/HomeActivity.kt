package com.hearxgroup.nativeusb

import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.view.View
import android.widget.TextView
import androidx.lifecycle.Observer
import com.hearxgroup.nativeusb.utils.USBCommandUtil
import kotlinx.android.synthetic.main.activity_home.*

class HomeActivity : USBActivity() {

    private val TAG = HomeActivity::class.java.simpleName

    private var serial_output: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        setupUI()

        val activeObserver = Observer<Boolean> {
            rlyt_root.visibility = if(it) View.VISIBLE else View.GONE
        }

        dacActive.observe(this, activeObserver)
    }

    private fun setupUI() {
        tv_usb_output.movementMethod= ScrollingMovementMethod()
        this.serial_output = tv_usb_output

        btn_clear.setOnClickListener {
            tv_usb_output.text = ""
            edt_id_msb.setText("")
            edt_id_lsb.setText("")
            edt_payload_0.setText("")
            edt_payload_1.setText("")
            edt_payload_2.setText("")
            edt_payload_3.setText("")
            edt_payload_4.setText("")
            edt_payload_5.setText("")
            edt_payload_6.setText("")
        }

        btn_send.setOnClickListener {
            serial_output!!.text = ""
            sendSerialCommand()
        }
    }

    private fun sendSerialCommand() {

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

        usbService?.writeCommand(
                payloadList = payloadList,
                messageIdMSB = messageIdMSB,
                messageIdLSB = messageIdLSB
        )
    }
}