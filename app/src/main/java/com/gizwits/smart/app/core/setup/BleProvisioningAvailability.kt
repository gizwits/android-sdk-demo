package com.gizwits.smart.app.core.setup

// configured also represents a closed provisioning window in V5 advertisements.
internal fun canProvisionBleDevice(configured: Boolean?, online: Boolean): Boolean =
    online && configured == false
