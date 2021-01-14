package com.hearxgroup.nativeusb

import android.hardware.usb.UsbRequest
import java.lang.reflect.Field
import java.nio.ByteBuffer

class SafeUsbRequest : UsbRequest() {
    override fun queue(buffer: ByteBuffer, length: Int): Boolean {
        val usbRequestBuffer: Field
        val usbRequestLength: Field
        try {
            usbRequestBuffer = UsbRequest::class.java.getDeclaredField(usbRqBufferField)
            usbRequestLength = UsbRequest::class.java.getDeclaredField(usbRqLengthField)
            usbRequestBuffer.isAccessible = true
            usbRequestLength.isAccessible = true
            usbRequestBuffer[this] = buffer
            usbRequestLength[this] = length
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
        return super.queue(buffer, length)
    }

    companion object {
        const val usbRqBufferField = "mBuffer"
        const val usbRqLengthField = "mLength"
    }
}
