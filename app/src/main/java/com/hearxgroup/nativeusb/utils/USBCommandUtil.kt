package com.hearxgroup.nativeusb.utils

import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.util.Log
import timber.log.Timber

object USBCommandUtil {

    val encoding = "0123456789ABCDEF".toCharArray()

    fun buildI2CCommand(command: IntArray): ByteArray {
        val commandBytes = ByteArray(command.size)

        //Log.i(TAG, "command.length="+command.length);
        for (k in commandBytes.indices) {
            commandBytes[k] = (command[k] and 0xFF).toByte()
        }

        //Log.i(TAG, "Command="+AltConverter.convertToString(command));
        return commandBytes
    }

    fun convertToString(arr: IntArray): String? {
        val encodedChars = CharArray(arr.size * 4 * 2)
        for (i in arr.indices) {
            val v = arr[i]
            val idx = i * 4 * 2
            for (j in 0..7) {
                encodedChars[idx + j] = encoding[v ushr (7 - j) * 4 and 0x0F]
            }
        }
        return String(encodedChars)
    }

    /*fun base16to64(hex: String?): String? {
        return Base64.getEncoder().encodeToString(BigInteger(hex, 16).toByteArray())
    }*/

    fun hexToInt(hex: String): Int {
        return hex.toInt(16)
    }

    /*fun hexToString(hex: String): String? {
        val sb = StringBuilder()
        val hexData = hex.toCharArray()
        var count = 0
        while (count < hexData.size - 1) {
            val firstDigit = Character.digit(hexData[count], 16)
            val lastDigit = Character.digit(hexData[count + 1], 16)
            val decimal = firstDigit * 16 + lastDigit
            sb.append(decimal.toChar())
            count += 2
        }
        return sb.toString()
    }*/

    fun writeIOPMessage(hexArray: Array<String>, connection: UsbDeviceConnection, outEndpoint: UsbEndpoint) : String? {
        Timber.d("writeIOPMessage")
        //Log.i(TAG, "hexArray=$hexArray")
        var requestedHex = ""
        val command = IntArray(64){0}
        for (k in hexArray.indices) {
            requestedHex += hexArray[k]
            command[k] = hexToInt(hexArray[k])
        }
        Timber.d("requestedHex=$requestedHex")
        Timber.d("Command Hex =" + convertToString(command))
        val commandHex = convertToString(command)
        val buffer1 = buildI2CCommand(command)!!
        //Log.i(TAG, "buffer1=$buffer1")
        //Log.i(TAG, "buffer1 size=${buffer1.size}")

        val syncWriteResult1 = connection.bulkTransfer(
                outEndpoint,
                buffer1,
                buffer1.size, //length in bytes
                1000)
        Timber.d("syncWriteResult1 = $syncWriteResult1")
        return commandHex
    }

    private fun hexToString(hex: String) : String {
        val output: java.lang.StringBuilder = java.lang.StringBuilder()
        var i = 0
        while (i < hex.length) {
            val str: String = hex.substring(i, i + 2)
            output.append(str.toInt(16).toChar())
            i += 2
        }
        return output.toString()
    }

    fun attCalcPGA2311(intendedAttenuation: Int) : String {
        Timber.d("attCalcPGA2311")
        val resultInt = ((((intendedAttenuation - 31.5)/(-0.5))-255)*-1).toInt()
        val resultHex = Integer.toHexString(resultInt)
        Timber.d("resultInt=$resultInt")
        Timber.d("resultHex=$resultHex")
        return resultHex
    }

    fun attCalcPT2259(intendedAttenuation: Int) : String {
        //todo
        Timber.d("attCalcPT2259")
        return ""
    }
}