package com.gizwits.smart.app.feature.main

import android.Manifest
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gizwits.smart.app.R
import com.gizwits.smart.app.ui.component.BottomDialog

@Composable
fun ScanBleDevicePermissionsDialog(
    onDismissRequest: () -> Unit,
    onPermissionsRequest: () -> Unit
) {
    BottomDialog(
        onDismissRequest = onDismissRequest,
        dismissOnClickOutside = false
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(id = R.string.scan_ble_device_permissions_tips),
                style = MaterialTheme.typography.titleSmall
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PermissionDescriptionBox(
                    icon = painterResource(id = R.drawable.ic_search_24),
                    permission = stringResource(id = R.string.scan_ble_device_permission),
                    description = stringResource(id = R.string.scan_ble_device_permission_description)
                )
            } else {
                PermissionDescriptionBox(
                    icon = painterResource(id = R.drawable.ic_local_24),
                    permission = stringResource(id = R.string.location_permission),
                    description = stringResource(id = R.string.location_permission_description)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onDismissRequest,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = stringResource(id = R.string.cancel))
                }
                Button(
                    onClick = onPermissionsRequest,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = stringResource(id = R.string.confirm))
                }
            }
        }
    }
}

@Composable
private fun PermissionDescriptionBox(
    icon: Painter,
    permission: String,
    description: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(painter = icon, contentDescription = null)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = permission,
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = Color.Gray
                )
            )
        }
    }
}








