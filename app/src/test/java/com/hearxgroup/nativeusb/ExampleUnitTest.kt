package com.hearxgroup.nativeusb

import android.util.Log
import org.junit.Test

import org.junit.Assert.*

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun pga2311_att() {
        val att = -19
        val nValue = ((((att - 31.5)/(-0.5))-255)*-1).toInt()
        //Log.d("ExampleUnitTest", "nValue=$nValue")
        assertEquals(1, nValue)
    }
}