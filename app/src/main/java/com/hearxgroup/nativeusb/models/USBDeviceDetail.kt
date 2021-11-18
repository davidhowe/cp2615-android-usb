/*
 * Copyright © 2018 - 2021 hearX IP (Pty) Ltd.
 * Copyright subsists in this work and it is copyright protected under the Berne Convention.  No part of this work may be reproduced, published, performed, broadcasted, adapted or transmitted in any form or by any means, electronic or mechanical, including photocopying, recording or by any information storage and retrieval system, without permission in writing from the copyright owner
 * hearX Group (Pty) Ltd.
 * info@hearxgroup.com
 */
package com.hearxgroup.nativeusb.models

/**
 * Created by David Howe
 * hearX Group (Pty) Ltd.
 */
data class USBDeviceDetail (
        var productName : String?,
        var serialNumber : String?
) {

    fun getVersion() : Int {
        return when (productName) {
            "HX_HT_USB_V1" -> 1
            "HX_HT_USB_V2" -> 2
            "HX_HT_USB_V3" -> 3
            else -> 1
        }
    }
}