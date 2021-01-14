/*
 * Copyright © 2018 - 2020 hearX IP (Pty) Ltd.
 * Copyright subsists in this work and it is copyright protected under the Berne Convention.  No part of this work may be reproduced, published, performed, broadcasted, adapted or transmitted in any form or by any means, electronic or mechanical, including photocopying, recording or by any information storage and retrieval system, without permission in writing from the copyright owner
 * hearX Group (Pty) Ltd.
 * info@hearxgroup.com
 */

package com.hearxgroup.nativeusb

import android.app.Activity
import android.os.Bundle

/**
 * Purpose for this class is consume open event required by USB plugin
 * Activity will do nothing but close immediately
 */
class USBBlankActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_usbblank)
        //TODO Check for last known open activity
        finish()
    }
}
