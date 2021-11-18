package com.hearxgroup.nativeusb

import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.*
import android.os.Binder
import android.os.IBinder
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.gson.Gson
import com.hearxgroup.nativeusb.models.USBDeviceDetail
import com.hearxgroup.nativeusb.utils.USBCommandUtil
import com.hearxgroup.nativeusb.utils.USBCommandUtil.buildI2CCommand
import timber.log.Timber
import java.io.UnsupportedEncodingException
import java.nio.ByteBuffer

class UsbService : Service() {
    companion object {
        const val DEFAULT_READ_BUFFER_SIZE = 16 * 1024

        const val ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION"
        const val ACTION_USB_PERMISSION_GRANTED = "com.felhr.usbservice.USB_PERMISSION_GRANTED"
        const val ACTION_USB_PERMISSION_NOT_GRANTED = "com.felhr.usbservice.USB_PERMISSION_NOT_GRANTED"
        const val ACTION_USB_DISCONNECTED = "com.felhr.usbservice.USB_DISCONNECTED"
        const val ACTION_USB_DEVICE_INFO = "com.felhr.usbservice.ACTION_USB_DEVICE_INFO"
        const val ACTION_USB_NOT_SUPPORTED = "com.felhr.usbservice.USB_NOT_SUPPORTED"
        const val ACTION_NO_USB = "com.felhr.usbservice.NO_USB"
        const val ACTION_EXTRA_DEVICE_INFO = "ACTION_EXTRA_DEVICE_INFO"
        const val ACTION_USB_READY = "com.felhr.connectivityservices.USB_READY"

        const val DAC_INTERFACE_INDEX = 3
        const val DAC_VENDOR_ID = 4292
        const val DAC_PRODUCT_ID = 60097
        var SERVICE_CONNECTED = false
    }

    enum class COMM_TYPE {SYNC, ASYNC}

    data class USBEvent(
        val attached: Boolean = false,
        val detached: Boolean = false,
        val invalid: Boolean = false,
        val active: Boolean = false,
        val findingDevices: Boolean = false,
        val noDevicesAvailable: Boolean = false,
        val requiresPermission: Boolean = false,
        val wroteData: String = "",
        val receivedData: String = ""
    )

    inner class UsbBinder : Binder() {
        val service: UsbService
            get() = this@UsbService
    }

    private val usbEvent = MutableLiveData<USBEvent>()
    private val binder: IBinder = UsbBinder()
    private var context: Context? = null
    private var usbInterface: UsbInterface? = null
    private var inEndpoint: UsbEndpoint? = null
    private var outEndpoint: UsbEndpoint? = null
    private var usbReadRequest: UsbRequest? = null
    private var readBuffer: ByteBuffer? = null
    private var interfaceClaimed = false
    private var commType = COMM_TYPE.SYNC //todo make versatile depending on the DAC connected

    //PUBLIC VARS
    var usbDevice: UsbDevice? = null
    var usbManager: UsbManager? = null
    var usbConnection: UsbDeviceConnection? = null
    val exposedUsbEvent: LiveData<USBEvent>
        get() {
            return usbEvent
        } //used to access the value inside the UI controller

    //******************* OVERRIDE METHODS *******************//

    override fun onCreate() {
        Timber.d("onCreate() UsbService")
        this.context = this
        SERVICE_CONNECTED = true
        setupBroadcastReceiver()
        usbManager = getSystemService(USB_SERVICE) as UsbManager
        if (exposedUsbEvent.value?.active != true)
            findUSBDevice()
    }

    override fun onDestroy() {
        Timber.d("onDestroy() UsbService")
        super.onDestroy()
        removeInterface()
        usbDevice = null
        usbManager = null
        //kill running threads todo
        unregisterReceiver(usbReceiver)
        SERVICE_CONNECTED = false
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    //******************* PRIVATE METHODS *******************//

    private fun setupBroadcastReceiver() {
        Timber.d("setupBroadcastReceiver")
        val filter = IntentFilter()
        filter.addAction(ACTION_USB_PERMISSION)
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        registerReceiver(usbReceiver, filter)
    }

    private fun findUSBDevice() {
        Timber.d("findUSBDevice")
        usbEvent.postValue(USBEvent(findingDevices = true))
        // This snippet will try to open the first encountered usb device connected, excluding usb root hubs
        val usbDevices = usbManager?.deviceList
        usbDevice = null
        if (!usbDevices.isNullOrEmpty()) {
            Timber.d("findUSBDevice usbDevices.isNotEmpty()")
            for ((_, device) in usbDevices) {
                Timber.d("device.vendorId=${device.vendorId}")
                Timber.d("device.productId=${device.productId}")
                if (device.vendorId ==DAC_VENDOR_ID && device.productId == DAC_PRODUCT_ID) {
                    // There is a supported device connected - request permission to access it.
                    Timber.d("Supported device found")
                    usbDevice = device
                    usbEvent.postValue(USBEvent(requiresPermission = true))
                    break
                }
            }
            if (usbDevice == null) {
                // There are no USB devices connected (but usb host were listed). Send an intent to MainActivity.
                Timber.d("Supported device not found")
                usbEvent.postValue(USBEvent(invalid = true))
            }
        } else {
            Timber.d("findSerialPortDevice() usbManager returned empty device list.")
            // There is no USB devices connected. Send an intent to MainActivity
            usbEvent.postValue(USBEvent(noDevicesAvailable = true))
        }
    }

    private fun claimUSBDevice() {
        Timber.d("claimUSBDevice()")

        for(k in 0 until usbDevice!!.interfaceCount) {
            Timber.d("Interface $k = ${usbDevice?.getInterface(k)}\n\n")
        }

        usbDevice?.getInterface(DAC_INTERFACE_INDEX)?.also { intf ->
            usbInterface = intf
            for(endpIndex in 0 until usbInterface!!.endpointCount) {
                val endpoint = intf.getEndpoint(endpIndex)
                if(endpoint.direction== UsbConstants.USB_DIR_IN) {
                    Timber.d("inEndpoint addr =${endpoint?.address}")
                    Timber.d("inEndpoint type =${endpoint?.type}")
                    inEndpoint = endpoint
                }
                if(endpoint.direction== UsbConstants.USB_DIR_OUT) {
                    Timber.d("outEndpoint addr =${endpoint?.address}")
                    Timber.d("outEndpoint type =${endpoint?.type}")
                    outEndpoint = endpoint
                }
            }

            Timber.d("claimed interface=${usbInterface}")

            usbManager?.openDevice(usbDevice)?.apply {
                usbConnection = this

                if(!interfaceClaimed)
                    claimInterface()

                if(interfaceClaimed) {
                    usbConnection!!.setInterface(usbInterface)
                    Timber.d("Interface claimed")
                    usbEvent.postValue(USBEvent(active = true))
                    val intent = Intent(ACTION_USB_READY)
                    context!!.sendBroadcast(intent)
                } else {
                    val intent = Intent(ACTION_USB_NOT_SUPPORTED)
                    context!!.sendBroadcast(intent)
                }
            }
        }
    }

    /*
     * Different notifications from OS will be received here (USB attached, detached, permission responses...)
     * About BroadcastReceiver: http://developer.android.com/reference/android/content/BroadcastReceiver.html
     */
    val usbReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(arg0: Context, intent: Intent) {
            if (intent.action == ACTION_USB_PERMISSION) {
                synchronized(this) {
                    usbReadRequest = null
                    usbDevice = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        val bcIntent = Intent(ACTION_USB_PERMISSION_GRANTED)
                        arg0.sendBroadcast(bcIntent)
                        Timber.d("permission granted for $usbDevice")
                        intent.apply {
                            //call method to set up device communication
                            claimUSBDevice()
                        }
                    } else {
                        Timber.e("permission denied for device $usbDevice")
                        usbEvent.postValue(USBEvent(requiresPermission = true))
                        val bcIntent = Intent(ACTION_USB_PERMISSION_NOT_GRANTED)
                        arg0.sendBroadcast(bcIntent)
                    }
                }
            } else if (intent.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) {
                Timber.d("ACTION_USB_DEVICE_ATTACHED")
                usbEvent.postValue(USBEvent(attached = true))
                if (exposedUsbEvent.value?.active != true)
                    findUSBDevice() // A USB device has been attached. Try to open it as a Serial port*/
            } else if (intent.action == UsbManager.ACTION_USB_DEVICE_DETACHED) {
                Timber.d("ACTION_USB_DEVICE_DETACHED")
                usbEvent.postValue(USBEvent(detached = true))
                // Usb device was disconnected. send an intent to the Main Activity
                val bcIntent = Intent(ACTION_USB_DISCONNECTED)
                arg0.sendBroadcast(bcIntent)
                /*if (serialPortConnected) {
                    serialPort.close()
                }*/
            }
        }
    }

    private fun getReadBuffer(): ByteBuffer? {
        synchronized(this) { return readBuffer }
    }

    private fun onReceivedData(inArray: ByteArray?) {
        try {
            //Timber.d("onReceivedData()")
            if(inArray!=null) {
                val intArr = IntArray(inArray.size)
                for (k in inArray.indices) {
                    intArr[k] = inArray[k].toInt()
                }
                //val convertedData: String = USBCommandUtil.convertToString(intArr) ?: ""
                //Log.d(TAG, "convertedData=$convertedData")
                val data = String(inArray, java.nio.charset.StandardCharsets.UTF_8)
                usbEvent.postValue(USBEvent(receivedData = data, active = true))
            } // else no data
        } catch (e: UnsupportedEncodingException) {
            e.printStackTrace()
        }
    }

    //******************* PUBLIC METHODS *******************//

    /*
     *  Data received from serial port will be received here. Just populate onReceivedData with your code
     *  In this particular example. byte stream is converted to String and send to UI thread to
     *  be treated there.
     */

    fun reconnectDevice() {
        Timber.d("reconnectDevice()")
        usbManager = getSystemService(USB_SERVICE) as UsbManager
        findUSBDevice()
    }

    fun resetDevice() {
        Timber.d("resetDevice()")
        removeInterface()
        usbConnection?.close()
        usbDevice = null
        usbManager = null
    }

    fun claimInterface() {
        Timber.d("claimInterface()")
        interfaceClaimed = usbConnection?.claimInterface(usbInterface, false) ?: false
        Timber.d("interfaceClaimed=$interfaceClaimed")
    }

    fun removeInterface() : Boolean {
        Timber.d("removeInterface()")
        val releaseResult = usbConnection?.releaseInterface(usbInterface)
        Timber.d("releaseResult=$releaseResult")
        if(releaseResult==true)
            interfaceClaimed = false
        return releaseResult?:false
    }

    fun setupForRead() {
        Timber.d("setupForRead()")
        usbReadRequest = SafeUsbRequest()
        val readInitialized = usbReadRequest!!.initialize(usbConnection, inEndpoint)
        Timber.d("readInitialized=$readInitialized")

        readBuffer?.clear()
        readBuffer = ByteBuffer.allocate(DEFAULT_READ_BUFFER_SIZE)
    }

    fun readFromUSBEndpoint() : Boolean {
        Timber.d("readFromUSBEndpoint()")
        //todo adjust for sync/async approach

        if(commType==COMM_TYPE.SYNC) {
            // Queue a new request
            val readThread = object : Runnable {
                override fun run() {
                    if (inEndpoint != null && usbConnection != null) {
                        val readSize = DEFAULT_READ_BUFFER_SIZE
                        //Log.d(TAG, "readSize=$readSize")
                        val recordIn = ByteArray(readSize)
                        val receivedLength = usbConnection!!.bulkTransfer(inEndpoint, recordIn, recordIn.size, 2000)
                        //Log.d(TAG, "receivedLength=$receivedLength")
                        if (receivedLength > -1) {
                            onReceivedData(recordIn)
                        }
                    }
                }
            }

            readThread.run()
        }
        return true
    }

    fun writeCommandV1V2(reg10: Int, reg1: Int) {
        val command = intArrayOf(42 ,42 ,0 ,15 ,212 ,0 ,1 ,136 ,0 ,5 ,136 ,240 ,116 ,reg10 ,reg1 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0 ,0, 0)
        val buffer1 = buildI2CCommand(command)
        val blankInts = mutableListOf<Int>()
        repeat(command.count()) {
            blankInts.add(0)
        }
        val transfer1Result = usbConnection?.bulkTransfer(outEndpoint, buffer1, buffer1.size, 1500)//15, 1500)
        Timber.d("transfer1Result = $transfer1Result")
    }

    fun writeCommandV3(payloadList: List<String>, messageIdMSB: String, messageIdLSB: String) {
        Timber.d("writeCommand()")
        val messageList = mutableListOf(
            "2A", //Preamble MSB
            "2A", //Preamble LSB
            "00", //Length MSB
            "0" + (6 + payloadList.size).toString(16), //Length LSB Total message length (header + message payload).
            messageIdMSB, //Message ID MSB
            messageIdLSB //Message ID LSB
        )

        messageList.addAll(payloadList)

        val writeThread = object : Runnable {
            override fun run() {
                synchronized(this) {
                    val commandHex = USBCommandUtil.writeIOPMessage(hexArray = messageList.toTypedArray(), connection = usbConnection!!, outEndpoint = outEndpoint!!)
                    usbEvent.postValue(USBEvent(wroteData = commandHex ?: "", active = true))
                }
            }
        }

        writeThread.run()
        readFromUSBEndpoint()
    }

    fun getDataReceived(): ByteArray {
        synchronized(this) {
            val dst = ByteArray(readBuffer!!.position())
            readBuffer!!.position(0)
            readBuffer!![dst, 0, dst.size]
            return dst
        }
    }

    fun retrieveDACInformation() {
        val intent = Intent(ACTION_USB_DEVICE_INFO)
        val usbDeviceDetail = USBDeviceDetail(
            usbDevice?.productName?:"",
            usbDevice?.serialNumber?:""
        )

        Timber.d("usbDevice!!.productName=${usbDeviceDetail.productName}")
        Timber.d("usbDevice!!.serialNumber=${usbDeviceDetail.serialNumber}")

        intent.putExtra(ACTION_EXTRA_DEVICE_INFO, Gson().toJson(usbDeviceDetail))
        sendBroadcast(intent)
    }

    fun findSerialPortDevice() {
        Timber.d("findSerialPortDevice")
        // This snippet will try to open the first encountered usb_dac_v1 device connected, excluding usb_dac_v1 root hubs
        val usbDevices = usbManager?.deviceList
        if(usbDevices.isNullOrEmpty()) {
            Timber.d("findSerialPortDevice() usbManager returned empty device list.")
            // There is no USB devices connected. Send an intent to MainActivity
            val intent = Intent(ACTION_NO_USB)
            sendBroadcast(intent)
        } else {
            Timber.d("!usbDevices.isEmpty()")
            // first, dump the hashmap for diagnostic purposes
            for ((_, value) in usbDevices) {
                usbDevice = value
                requestUserPermission()
                break
            }
            if (usbDevice == null) {
                // There are no USB devices connected (but usb_dac_v1 host were listed). Send an intent to MainActivity.
                val intent = Intent(ACTION_NO_USB)
                sendBroadcast(intent)
            }
        }
    }

    private fun requestUserPermission() {
        Timber.d("requestUserPermission()")
        val mPendingIntent = PendingIntent.getBroadcast(this, 0, Intent(ACTION_USB_PERMISSION), 0)
        usbManager?.requestPermission(usbDevice, mPendingIntent)
    }
}