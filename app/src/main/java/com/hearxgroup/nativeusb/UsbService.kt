package com.hearxgroup.nativeusb

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.*
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.hearxgroup.nativeusb.utils.USBCommandUtil
import io.reactivex.Single
import java.io.IOException
import java.io.UnsupportedEncodingException
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit


class UsbService : Service() {

    private val TAG = UsbService::class.java.simpleName

    companion object {
        const val DEFAULT_READ_BUFFER_SIZE = 16 * 1024
        const val ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION"
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
    private var commType = COMM_TYPE.SYNC //todo make versatile depending on the DAC connected

    //PUBLIC VARS
    var usbDevice: UsbDevice? = null
    var usbManager: UsbManager? = null
    var usbConnection: UsbDeviceConnection? = null
    val exposedUsbEvent: LiveData<USBEvent>
        get() {
            return usbEvent
        } //used to access the value inside the UI controller

    //todo usb callback

    //******************* OVERRIDE METHODS *******************//

    override fun onCreate() {
        Log.d(TAG, "onCreate()")
        this.context = this
        SERVICE_CONNECTED = true
        setupBroadcastReceiver()
        usbManager = getSystemService(USB_SERVICE) as UsbManager
        if (exposedUsbEvent.value?.active != true)
            findUSBDevice()
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy()")
        super.onDestroy()
        usbConnection?.releaseInterface(usbInterface)
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
        val filter = IntentFilter()
        //filter.addAction(ACTION_USB_PERMISSION)
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        registerReceiver(usbReceiver, filter)
    }

    private fun findUSBDevice() {
        Log.d(TAG, "findUSBDevice")
        usbEvent.value = USBEvent(findingDevices = true)
        // This snippet will try to open the first encountered usb device connected, excluding usb root hubs
        val usbDevices = usbManager!!.deviceList
        usbDevice = null
        if (usbDevices.isNotEmpty()) {
            // first, dump the hashmap for diagnostic purposes
            /*for ((_, value) in usbDevices) {
                 val device = value
                Log.d(TAG, String.format("USBDevice.HashMap (vid:pid) (%X:%X)-%b class:%X:%X name:%s",
                        device.getVendorId(), device.getProductId(),
                        true,  //UsbSerialDevice.isSupported(device),
                        device.getDeviceClass(), device.getDeviceSubclass(),
                        device.getDeviceName()))
            }*/

            for ((_, device) in usbDevices) {
                if (device.vendorId ==DAC_VENDOR_ID && device.productId == DAC_PRODUCT_ID) {
                    // There is a supported device connected - request permission to access it.
                    usbDevice = device
                    usbEvent.value = USBEvent(requiresPermission = true)
                    break
                }
            }
            if (usbDevice == null) {
                // There are no USB devices connected (but usb host were listed). Send an intent to MainActivity.
                usbEvent.value = USBEvent(invalid = true)
            }
        } else {
            Log.d(TAG, "findSerialPortDevice() usbManager returned empty device list.")
            // There is no USB devices connected. Send an intent to MainActivity
            usbEvent.value = USBEvent(noDevicesAvailable = true)
        }
    }

    private fun claimUSBDevice() {
        Log.d(TAG, "claimUSBDevice()")

        for(k in 0 until usbDevice!!.interfaceCount) {
            Log.d(TAG, "Interface $k = ${usbDevice?.getInterface(k)}\n\n")
        }

        usbDevice?.getInterface(DAC_INTERFACE_INDEX)?.also { intf ->
            usbInterface = intf
            for(endpIndex in 0 until usbInterface!!.endpointCount) {
                val endpoint = intf.getEndpoint(endpIndex)
                if(endpoint.direction== UsbConstants.USB_DIR_IN) {
                    Log.d(TAG, "inEndpoint add =${endpoint?.address}")
                    Log.d(TAG, "inEndpoint type =${endpoint?.type}")
                    inEndpoint = endpoint
                }
                if(endpoint.direction== UsbConstants.USB_DIR_OUT) {
                    Log.d(TAG, "outEndpoint add =${endpoint?.address}")
                    Log.d(TAG, "outEndpoint type =${endpoint?.type}")
                    outEndpoint = endpoint
                }
            }

            Log.d(TAG, "inEndpoint=$inEndpoint")
            Log.d(TAG, "outEndpoint=$outEndpoint")

            Log.d(TAG, "claimed interface=${usbInterface}")

             usbManager?.openDevice(usbDevice)?.apply {
                usbConnection = this
                 val claimResult = claimInterface()
                 if(claimResult) {
                    usbConnection!!.setInterface(usbInterface)
                    Log.d(TAG, "Interface claimed")
                    usbEvent.value = USBEvent(active = true)
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
                Log.d(TAG, "ACTION_USB_PERMISSION")
                synchronized(this) {
                    usbReadRequest = null
                    usbDevice = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        Log.d(TAG, "permission granted for $usbDevice")
                        intent.apply {
                            //call method to set up device communication
                            claimUSBDevice()
                        }
                    } else {
                        Log.d(TAG, "permission denied for device $usbDevice")
                        usbEvent.value = USBEvent(requiresPermission = true)
                    }
                }
            } else if (intent.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) {
                Log.d(TAG, "ACTION_USB_DEVICE_ATTACHED")
                usbEvent.value = USBEvent(attached = true)
                if (exposedUsbEvent.value?.active != true)
                    findUSBDevice() // A USB device has been attached. Try to open it as a Serial port*/
            } else if (intent.action == UsbManager.ACTION_USB_DEVICE_DETACHED) {
                Log.d(TAG, "ACTION_USB_DEVICE_DETACHED")
                usbEvent.value = USBEvent(detached = true)
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
            Log.d(TAG, "onReceivedData()")
            if(inArray!=null) {
                Log.d(TAG, "data present")
                val intArr = IntArray(inArray.size)
                for (k in inArray.indices) {
                    intArr[k] = inArray[k].toInt()
                    //Log.d(TAG, "byteEntry="+(int)byteEntry);
                }
                val convertedData: String = USBCommandUtil.convertToString(intArr) ?: ""
                Log.d(TAG, "convertedData=$convertedData")
                val data = String(inArray, java.nio.charset.StandardCharsets.UTF_8)
                Log.d(TAG, "data=$data")
                usbEvent.value = USBEvent(receivedData = data, active = true)
            } else {
                Log.d(TAG, "no data")
            }
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
        Log.d(TAG, "reconnectDevice()")
        usbManager = getSystemService(USB_SERVICE) as UsbManager
        findUSBDevice()
    }

    fun resetDevice() {
        Log.d(TAG, "resetDevice()")
        usbConnection?.releaseInterface(usbInterface)
        usbConnection?.close()
        usbDevice = null
        usbManager = null
    }

    fun claimInterface() : Boolean {
        Log.d(TAG, "claimInterface()")
        val claimResult = usbConnection!!.claimInterface(usbInterface, false)
        Log.d(TAG, "claimResult=$claimResult")
        val setResult = usbConnection!!.setInterface(usbInterface)
        Log.d(TAG, "setResult=$setResult")
        return claimResult
    }

    fun removeInterface() : Boolean {
        Log.d(TAG, "removeInterface()")
        val releaseResult = usbConnection!!.releaseInterface(usbInterface)
        Log.d(TAG, "releaseResult=$releaseResult")
        return releaseResult
    }

    fun setupForRead() {
        Log.d(TAG, "setupForRead()")
        usbReadRequest = SafeUsbRequest()
        val readInitialized = usbReadRequest!!.initialize(usbConnection, inEndpoint)
        Log.d(TAG, "readInitialized=$readInitialized")

        readBuffer?.clear()
        readBuffer = ByteBuffer.allocate(DEFAULT_READ_BUFFER_SIZE)
    }

    fun readFromUSBEndpoint() : Boolean {
        Log.d(TAG, "readFromUSBEndpoint()")
        //todo adjust for sync/async approach

        if(commType==COMM_TYPE.SYNC) {
            // Queue a new request
            val readThread = object : Runnable {
                override fun run() {
                        if (inEndpoint != null && usbConnection != null) {
                           val readSize = DEFAULT_READ_BUFFER_SIZE
                            Log.d(TAG, "readSize=$readSize")
                            val recordIn = ByteArray(readSize)
                            val receivedLength = usbConnection!!.bulkTransfer(inEndpoint, recordIn, recordIn.size, 2000)
                            Log.d(TAG, "receivedLength=$receivedLength")
                            if (receivedLength > -1) {
                                onReceivedData(recordIn)
                            }

                            true
                            /*val request = UsbRequest()
                            try {
                                request.initialize(usbConnection, inEndpoint)
                                val byteArr = ByteArray(DEFAULT_READ_BUFFER_SIZE)
                                val buf = ByteBuffer.wrap(byteArr)
                                if (!request.queue(buf, DEFAULT_READ_BUFFER_SIZE)) {
                                    throw IOException("Error queueing request.")
                                }
                                val response: UsbRequest = usbConnection!!.requestWait()
                                        ?: throw IOException("Null response")
                                val nread = buf.position()
                                if (nread > 0) {
                                    Log.d(TAG, "Read some data!")
                                } else {
                                    Log.d(TAG, "Read no data :(")
                                }
                            } finally {
                                request.close()
                            }*/
                        }
                }
            }

            readThread.run()
        }
        return true
    }

    fun writeCommand(payloadList: List<String>, messageIdMSB: String, messageIdLSB: String) {
        Log.d(TAG, "writeCommand()")
        Log.d(TAG, "payloadList=$payloadList")
        Log.d(TAG, "messageIdMSB=$messageIdMSB")
        Log.d(TAG, "messageIdLSB=$messageIdLSB")
        val messageList = mutableListOf(
                "2A", //Preamble MSB
                "2A", //Preamble LSB
                "00", //Length MSB
                "0" + (6 + payloadList.size).toString(16), //Length LSB Total message length (header + message payload).
                messageIdMSB, //Message ID MSB
                messageIdLSB //Message ID LSB
        )

        messageList.addAll(payloadList)

        /*messageList.add(
                //Payload Size
                payloadList.map { USBCommandUtil.hexToInt(it) }.sum().toString(16)
        )*/

        val writeThread = object : Runnable {
            override fun run() {
                synchronized(this) {
                    val permission = usbManager?.hasPermission(usbDevice) ?: false
                    Log.d(TAG, "permission=$permission")
                    val commandHex = USBCommandUtil.writeIOPMessage(hexArray = messageList.toTypedArray(), connection = usbConnection!!, outEndpoint = outEndpoint!!)
                    usbEvent.value = USBEvent(wroteData = commandHex?:"", active = true)
                }
            }
        }

        writeThread.run()

        readFromUSBEndpoint()

        /*Single.timer(1000, TimeUnit.MILLISECONDS)
                .subscribe { _->
                    setupForRead()
                    Single.timer(1000, TimeUnit.MILLISECONDS)
                            .subscribe { _ ->
                                readFromUSBEndpoint()
                            }
                }*/
    }

    fun getDataReceived(): ByteArray? {
        synchronized(this) {
            val dst = ByteArray(readBuffer!!.position())
            readBuffer!!.position(0)
            readBuffer!![dst, 0, dst.size]
            return dst
        }
    }
}