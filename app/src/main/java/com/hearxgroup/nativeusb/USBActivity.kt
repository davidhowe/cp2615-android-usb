package com.hearxgroup.nativeusb

import android.app.PendingIntent
import android.content.*
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer

abstract class USBActivity : AppCompatActivity() {

    private val TAG = USBActivity::class.java.simpleName

    private val ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION"

    enum class DacStatus {DISCONNECTED, CONNECTING, CONNECTED}

    protected var usbService: UsbService? = null
    protected var dacStatus = MutableLiveData<DacStatus>().apply { DacStatus.DISCONNECTED }
    protected var dacComm = MutableLiveData<String>().apply { "" }


    private val usbConnection = object : ServiceConnection {
        override fun onServiceConnected(arg0: ComponentName, arg1: IBinder) {
            Log.d(TAG, "onServiceConnected")
            usbService = (arg1 as UsbService.UsbBinder).service
            usbService!!.exposedUsbEvent.observe(this@USBActivity, usbStatusObserver)
            //usbService!!.setHandler(mHandler)
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            Log.d(TAG, "onServiceDisconnected")
            usbService = null
        }
    }

    private val usbStatusObserver = Observer<UsbService.USBEvent> {

            if(it.attached) {
                dacStatus.value = DacStatus.CONNECTING
                Log.d(TAG, "attached")
            } else if(it.detached) {
                dacStatus.value = DacStatus.DISCONNECTED
                Log.d(TAG, "detached")
            } else if(it.noDevicesAvailable) {
                dacStatus.value = DacStatus.DISCONNECTED
                Log.d(TAG, "noDevicesAvailable")
            } else if(it.findingDevices) {
                dacStatus.value = DacStatus.CONNECTING
                Log.d(TAG, "findingDevices")
            } else if (it.requiresPermission) {
                dacStatus.value = DacStatus.CONNECTING
                Log.d(TAG, "requiresPermission")
                val permissionIntent = PendingIntent.getBroadcast(this, 0, Intent(ACTION_USB_PERMISSION), 0)
                val filter = IntentFilter(ACTION_USB_PERMISSION)
                registerReceiver(usbService?.usbReceiver, filter)
                usbService?.usbManager?.requestPermission(usbService?.usbDevice, permissionIntent)
            } else if(it.invalid) {
                Log.d(TAG, "invalid")
            } else if(it.active) {
                Log.d(TAG, "active")
                dacStatus.value = DacStatus.CONNECTED
            } else if(it.receivedData.isNotEmpty()) {
                Log.d(TAG, "receivedData=${it.receivedData}")
                //todo print to console
            }

        if(!it.wroteData.isNullOrEmpty())
            dacComm.value = it.wroteData
    }

    public override fun onResume() {
        super.onResume()
        startService(
                UsbService::class.java,
                usbConnection,
                null
        ) // Start UsbService(if it was not started before) and Bind it
    }

    public override fun onPause() {
        super.onPause()
        unbindService(usbConnection)
    }

    private fun startService(
            service: Class<*>,
            serviceConnection: ServiceConnection,
            extras: Bundle?
    ) {
        if (!UsbService.SERVICE_CONNECTED) {
            val startService = Intent(this, service)
            if (extras != null && !extras.isEmpty) {
                val keys = extras.keySet()
                for (key in keys) {
                    val extra = extras.getString(key)
                    startService.putExtra(key, extra)
                }
            }
            startService(startService)
        }
        val bindingIntent = Intent(this, service)
        bindService(bindingIntent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

}