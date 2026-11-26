package com.gizwits.smart.app.feature.control.data

import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RichTooltipBox
import androidx.compose.material3.RichTooltipState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.TextButton
import com.gizwits.smart.app.ui.component.SoftMessage
import com.gizwits.smart.app.ui.component.SoftCard
import com.gizwits.smart.app.ui.component.SoftTopBar
import com.gizwits.smart.sdk.core.capacity.CapacityStatus
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberRichTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gizwits.smart.app.R
import com.gizwits.smart.app.ui.component.SmartOutlinedTextField
import com.gizwits.smart.app.ui.component.SmartSlider
import com.gizwits.smart.app.ui.component.SmartSwitch
import com.gizwits.smart.app.ui.component.SystemUiController
import com.gizwits.smart.app.ui.component.TransparentTopAppBar
import com.gizwits.smart.sdk.common.DataPointStatusType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControlDataRoute(
    onBackClick: () -> Unit,
    viewModel: ControlDataViewModel = hiltViewModel()
) {
    val uiState: ControlDataUiState by viewModel.uiState.collectAsState()
    val connection by viewModel.status.collectAsState()
    val enabled by viewModel.enabled.collectAsState()
    Scaffold(
        topBar = {
            SoftTopBar(title = stringResource(id = R.string.device_control_data_title), onBack = onBackClick)
        }
    ) { contentPadding ->
        // 设置导航栏颜色
        val navigationBarColor: Color = MaterialTheme.colorScheme.background
        SystemUiController {
            setNavigationBarColor(navigationBarColor)
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
        ) {
            Column(Modifier.fillMaxSize()) {
                Column(Modifier.padding(horizontal = 22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("当前通道：${when(viewModel.mode) { ControlDataMode.Bluetooth -> "蓝牙"; ControlDataMode.Lan -> "局域网"; ControlDataMode.Mqtt -> "云端" }}")
                    SoftMessage(if (connection == CapacityStatus.ONLINE_CONTROLLABLE) viewModel.message else "当前通道不可控制，以下为最后收到的值。检查连接后重试。")
                    viewModel.lastReport?.let { time -> Text("最近设备回报：" + java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(time)), style = MaterialTheme.typography.labelSmall) }
                    TextButton(onClick = viewModel::read, enabled = enabled) { Text("读取最新状态", color = MaterialTheme.colorScheme.onSurface) }
                }
                ControlDataScreen(uiState = uiState, modifier = Modifier.weight(1f))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ControlDataScreen(
    uiState: ControlDataUiState,
    modifier: Modifier = Modifier
) {
    val dataPointStates: List<DataPointState<*>> = uiState.dataPointStates;
    Column(
        modifier = modifier
    ) {
        CompositionLocalProvider(
            LocalOverscrollConfiguration provides null
        ) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 22.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (dataPointStates.isEmpty()) item { SoftMessage("尚未取得数据点定义。请检查产品 PK、网络和当前设备连接。") }
                items(
                    items = dataPointStates,
                    key = { it.name }
                ) {
                    when (it) {
                        is BooleanDataPointState -> {
                            BooleanDataPointCard(
                                dataPointState = it,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        is NumberDataPointState -> {
                            NumberDataPointCard(
                                dataPointState = it,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        is EnumDataPointState -> {
                            EnumDataPointCard(
                                dataPointState = it,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        is BinaryDataPointState -> {
                            BinaryDataPointCard(
                                dataPointState = it,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DataPointCard(
    dataPointState: DataPointState<*>,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val scope: CoroutineScope = rememberCoroutineScope()
    val tooltipState: RichTooltipState = rememberRichTooltipState(isPersistent = true)
    SoftCard(modifier = modifier) {
        Column(
            modifier = Modifier,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = dataPointState.displayName.ifBlank { dataPointState.name },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(
                        id = when (dataPointState.dataStatusType) {
                            DataPointStatusType.WRITABLE -> R.string.data_point_status_writable
                            DataPointStatusType.READONLY -> R.string.data_point_status_readonly
                            DataPointStatusType.ALERT -> R.string.data_point_status_alert
                            DataPointStatusType.FAULT -> R.string.data_point_status_fault
                            DataPointStatusType.UNKNOWN -> R.string.data_point_status_unknown
                        }
                    ),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = Color.Gray
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                RichTooltipBox(
                    text = {
                        Text(
                            text = "${dataPointState.displayName}\n${dataPointState.description}",
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    },
                    tooltipState = tooltipState,
                    colors = TooltipDefaults.richTooltipColors(
                        containerColor = TooltipDefaults.plainTooltipContainerColor,
                        contentColor = TooltipDefaults.plainTooltipContentColor
                    )
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_tips_24),
                        contentDescription = null,
                        modifier = Modifier
                            .tooltipTrigger()
                            .shadow(
                                elevation = 0.dp,
                                shape = CircleShape,
                                clip = true
                            )
                            .clickable {
                                scope.launch { tooltipState.show() }
                            }
                            .scale(0.7f)
                    )
                }
            }
            Text(dataPointState.name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}

@Composable
private fun BooleanDataPointCard(
    dataPointState: BooleanDataPointState,
    modifier: Modifier = Modifier
) {
    val isEnabled: Boolean by dataPointState.isEnabled.collectAsState()
    val isWritable: Boolean = dataPointState.dataStatusType == DataPointStatusType.WRITABLE
    val isChecked: Boolean? by dataPointState.value.collectAsState(initial = null)
    DataPointCard(
        dataPointState = dataPointState,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = isChecked?.toString() ?: "尚未读取",
                modifier = Modifier.padding(end = 12.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            SmartSwitch(
                checked = isChecked ?: false,
                onCheckedChange = dataPointState::onValueChange,
                enabled = isChecked != null && isEnabled && isWritable,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                    uncheckedBorderColor = MaterialTheme.colorScheme.outline
                )
            )
        }
    }
}

@Composable
private fun NumberDataPointCard(
    dataPointState: NumberDataPointState,
    modifier: Modifier = Modifier
) {
    val isEnabled: Boolean by dataPointState.isEnabled.collectAsState()
    val isWritable: Boolean = dataPointState.dataStatusType == DataPointStatusType.WRITABLE
    val value: Float? by dataPointState.value.collectAsState(initial = null)
    var cacheValue: Float by remember { mutableFloatStateOf(0f) }
    val currentValue: Double by remember {
        derivedStateOf { dataPointState.fromValue(cacheValue).asDouble }
    }
    LaunchedEffect(value) {
        if (value != null) {
            cacheValue = value as Float
        }
    }
    DataPointCard(
        dataPointState = dataPointState,
        modifier = modifier
    ) {
        val dialColor = MaterialTheme.colorScheme.primary
        val trackColor = MaterialTheme.colorScheme.surfaceVariant
        Box(Modifier.size(150.dp).align(Alignment.CenterHorizontally), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(140.dp)) {
                val stroke = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                drawArc(trackColor, 135f, 270f, false, style = stroke)
                if (value != null) drawArc(dialColor, 135f, 270f * cacheValue.coerceIn(0f,1f), false, style = stroke)
            }
            Text(if (value == null) "—" else java.text.DecimalFormat("0.###").format(currentValue), style = MaterialTheme.typography.headlineLarge)
        }
        SmartSlider(
            value = cacheValue,
            onValueChange = { cacheValue = it },
            onValueChangeFinished = {
                dataPointState.onValueChange(cacheValue)
            },
            enabled = value != null && isEnabled && isWritable
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${dataPointState.minValue}",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.SemiBold
                )
            )
            Text(
                text = if (value == null) "—" else "${currentValue}",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.SemiBold
                )
            )
            Text(
                text = "${dataPointState.maxValue}",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.SemiBold
                )
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EnumDataPointCard(
    dataPointState: EnumDataPointState,
    modifier: Modifier = Modifier
) {
    val isEnabled: Boolean by dataPointState.isEnabled.collectAsState()
    val isWritable: Boolean = dataPointState.dataStatusType == DataPointStatusType.WRITABLE
    var isExpanded by remember { mutableStateOf(false) }
    val options: Array<String> = dataPointState.options
    val value: Int? by dataPointState.value.collectAsState(initial = null)
    DataPointCard(
        dataPointState = dataPointState,
        modifier = modifier
    ) {
        value?.also { index ->
            ExposedDropdownMenuBox(
                expanded = isExpanded,
                onExpandedChange = { isExpanded = !isExpanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = options.getOrElse(index) { "未知值：$index" },
                        modifier = if (isEnabled && isWritable) {
                            Modifier.menuAnchor()
                        } else {
                            Modifier
                        },
                        style = LocalTextStyle.current.copy(
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                    if (isEnabled && isWritable) {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = isExpanded)
                    }
                }
                ExposedDropdownMenu(
                    expanded = isExpanded,
                    onDismissRequest = { isExpanded = false }
                ) {
                    options.forEachIndexed { index, option ->
                        DropdownMenuItem(
                            text = {
                                Text(text = option)
                            },
                            onClick = {
                                dataPointState.onValueChange(index)
                                isExpanded = false
                            },
                            contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BinaryDataPointCard(
    dataPointState: BinaryDataPointState,
    modifier: Modifier = Modifier
) {
    val isEnabled: Boolean by dataPointState.isEnabled.collectAsState()
    val isWritable: Boolean = dataPointState.dataStatusType == DataPointStatusType.WRITABLE
    val value: String? by dataPointState.value.collectAsState(initial = null)
    var cacheValue: String by remember { mutableStateOf("") }
    val validHex = cacheValue.replace(" ", "").let { it.length % 2 == 0 && it.matches(Regex("[0-9a-fA-F]*")) }
    LaunchedEffect(value) {
        if (value != null) {
            cacheValue = value as String
        }
    }
    DataPointCard(
        dataPointState = dataPointState,
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SmartOutlinedTextField(
                value = cacheValue,
                onValueChange = { cacheValue = it },
                modifier = Modifier.weight(1f),
                enabled = isEnabled && isWritable,
                isError = !validHex,
                supportingText = { Text(if (validHex) "HEX · 每两个字符为一个字节" else "请输入成对的十六进制字符") }
            )
            IconButton(
                onClick = {
                    dataPointState.onValueChange(cacheValue.replace(" ", ""))
                },
                enabled = value != null && isEnabled && isWritable && validHex
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = null
                )
            }
        }
    }
}












