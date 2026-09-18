package com.bc250.telemetry

import android.os.Bundle
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---- Palette matching the web dashboard's dark glass-panel look ----
private val BgColor = Color(0xFF0B0F19)
private val CardColor = Color(0xFF141A2E)
private val CardBorder = Color(0xFF2A3350)
private val TextMain = Color(0xFFF1F5F9)
private val TextMuted = Color(0xFF8B93A7)
private val AccentCpu = Color(0xFF38BDF8)
private val AccentGpu = Color(0xFFFB923C)
private val StatusGood = Color(0xFF4ADE80)
private val StatusWarning = Color(0xFFFACC15)
private val StatusSerious = Color(0xFFFB923C)
private val StatusCritical = Color(0xFFF87171)
private const val DISPLAY_VARIANT_KEY = "display_variant"

enum class DisplayVariant {
    V1,
    V2,
}

class MainActivity : ComponentActivity() {
    private val viewModel: TelemetryViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var displayVariant by remember { mutableStateOf(loadDisplayVariant()) }
            MaterialTheme(colorScheme = darkColorScheme(background = BgColor)) {
                Surface(color = BgColor, modifier = Modifier.fillMaxSize()) {
                    HudScreen(
                        viewModel = viewModel,
                        displayVariant = displayVariant,
                        onDisplayVariantChange = { variant ->
                            displayVariant = variant
                            saveDisplayVariant(variant)
                        },
                    )
                }
            }
        }
    }

    private fun loadDisplayVariant(): DisplayVariant {
        return if (getPreferences(Context.MODE_PRIVATE).getString(DISPLAY_VARIANT_KEY, "V1") == "V2") {
            DisplayVariant.V2
        } else {
            DisplayVariant.V1
        }
    }

    private fun saveDisplayVariant(variant: DisplayVariant) {
        getPreferences(Context.MODE_PRIVATE)
            .edit()
            .putString(DISPLAY_VARIANT_KEY, variant.name)
            .apply()
    }
}

private fun statusColor(value: Double, thresholds: Triple<Double, Double, Double>): Color {
    val (warn, serious, critical) = thresholds
    return when {
        value >= critical -> StatusCritical
        value >= serious -> StatusSerious
        value >= warn -> StatusWarning
        else -> StatusGood
    }
}

private val DieThresh = Triple(70.0, 85.0, 95.0)
private val VrmThresh = Triple(60.0, 75.0, 90.0)
private val NvmeThresh = Triple(55.0, 65.0, 75.0)
private val BoardThresh = Triple(45.0, 55.0, 65.0)

@Composable
fun HudScreen(
    viewModel: TelemetryViewModel,
    displayVariant: DisplayVariant = DisplayVariant.V1,
    onDisplayVariantChange: (DisplayVariant) -> Unit = {},
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val telemetry by viewModel.telemetry.collectAsState()

    Column(Modifier.fillMaxSize()) {
        DisplayVariantSwitch(displayVariant, onDisplayVariantChange)
        Box(Modifier.fillMaxWidth().weight(1f)) {
            when (val state = connectionState) {
                is ConnectionState.Searching -> SearchingScreen()
                is ConnectionState.NotFound -> NotFoundScreen(onRetry = viewModel::retry)
                is ConnectionState.Connected -> {
                    val data = telemetry
                    if (data == null) {
                        SearchingScreen(message = "Подключено к ${state.host}, ожидание данных…")
                    } else if (displayVariant == DisplayVariant.V2) {
                        TelemetryScreenV2(host = state.host, data = data)
                    } else {
                        TelemetryScreen(host = state.host, data = data)
                    }
                }
            }
        }
    }
}

@Composable
private fun DisplayVariantSwitch(
    selected: DisplayVariant,
    onChange: (DisplayVariant) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Вид", color = TextMuted, fontSize = 12.sp)
        Spacer(Modifier.size(8.dp))
        DisplayVariant.entries.forEach { variant ->
            Button(
                onClick = { onChange(variant) },
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                modifier = Modifier.height(36.dp),
            ) {
                Text(
                    variant.name,
                    fontWeight = if (variant == selected) FontWeight.Bold else FontWeight.Normal,
                )
            }
            if (variant != DisplayVariant.entries.last()) Spacer(Modifier.size(6.dp))
        }
    }
}

@Composable
private fun SearchingScreen(message: String = "Поиск BC-250 в сети Wi-Fi…") {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = AccentCpu)
            Spacer(Modifier.height(16.dp))
            Text(message, color = TextMain, fontSize = 16.sp)
        }
    }
}

@Composable
private fun NotFoundScreen(onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
            Text("BC-250 не найден", color = TextMain, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                "Убедитесь, что телефон подключён к той же Wi-Fi сети, что и BC-250, и что сервис bc250-web.service запущен (порт 8090).",
                color = TextMuted,
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onRetry) { Text("Повторить поиск") }
        }
    }
}

@Composable
private fun TelemetryScreen(host: String, data: Telemetry) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { Header(host, data) }
        item { CpuCard(data) }
        item { GpuCard(data) }
        item { OtherSensorsCard(data) }
        if (data.memory.valid) {
            item { MemoryCard(data.memory) }
        }
    }
}

@Composable
private fun TelemetryScreenV2(host: String, data: Telemetry) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            HudCard {
                Column(Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("BC-250", color = TextMain, fontSize = 20.sp, fontWeight = FontWeight.Black)
                        Text("V2 · BOARD", color = AccentCpu, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(host, color = TextMuted, fontSize = 12.sp)
                    Spacer(Modifier.height(12.dp))
                    BoardDiagram(data)
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        LabeledValue("VRM SUM", "%.1f W".format(data.totalPower), TextMain)
                        if (data.gpuPptW >= 0) LabeledValue("APU PPT", "%.1f W".format(data.gpuPptW), TextMain)
                    }
                }
            }
        }
        item { V2SummaryCard("CPU CORE", AccentCpu, "${data.cpuFreqMhz} MHz", data.cpuTempC, data.cpu) }
        item { V2SummaryCard("GPU CORE", AccentGpu, "%.0f MHz".format(data.gpuSclkMhz), data.gpuTempC, data.gpu) }
        item {
            HudCard {
                Column(Modifier.padding(16.dp)) {
                    CardTitle("SYSTEM", TextMain)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        LabeledValue("FAN", "${data.fanRpm} RPM", TextMain)
                        LabeledValue("PWM", "%.0f%%".format(data.fanPwmPct), TextMain)
                        if (data.nvmeTempC >= 0) LabeledValue("NVMe", "%.1f°C".format(data.nvmeTempC), statusColor(data.nvmeTempC, NvmeThresh))
                    }
                }
            }
        }
    }
}

@Composable
private fun BoardDiagram(data: Telemetry) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(170.dp)
            .background(Color(0xFF101A2C), RoundedCornerShape(12.dp))
            .border(1.dp, CardBorder, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(18.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BoardZone("CPU", "${data.cpuTempC.toInt()}°C", AccentCpu, data.cpu.pout)
            Box(Modifier.size(48.dp).border(2.dp, AccentCpu, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                Text("BC\n250", color = TextMain, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            BoardZone("GPU", "${data.gpuTempC.toInt()}°C", AccentGpu, data.gpu.pout)
        }
    }
}

@Composable
private fun BoardZone(title: String, temperature: String, color: Color, power: Double) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(62.dp).background(color.copy(alpha = 0.18f), RoundedCornerShape(10.dp)).border(1.dp, color, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(temperature, color = TextMain, fontSize = 16.sp, fontWeight = FontWeight.Black)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text("%.1f W".format(power), color = TextMuted, fontSize = 11.sp)
    }
}

@Composable
private fun V2SummaryCard(title: String, accent: Color, frequency: String, temperature: Double, rail: RailTelemetry) {
    HudCard {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                CardTitle(title, accent)
                LabeledValue(frequency, "%.1f°C".format(temperature), statusColor(temperature, DieThresh))
            }
            VrmTelemetryTable(rail, vinMax = 13.0, voutMax = if (title == "CPU CORE") 1.2 else 0.9, ioutMax = if (title == "CPU CORE") 55.0 else 150.0, poutMax = if (title == "CPU CORE") 60.0 else 130.0)
        }
    }
}

@Composable
private fun Header(host: String, data: Telemetry) {
    HudCard {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    buildString { append("BC-250 "); },
                    color = TextMain,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                )
                Text("HUD", color = AccentCpu, fontSize = 20.sp, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.height(4.dp))
            Text(host, color = TextMuted, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                LabeledValue("VRM SUM (CPU+GPU)", "%.1f W".format(data.totalPower), TextMain)
                if (data.gpuPptW >= 0) {
                    LabeledValue("SYSTEM PPT", "%.1f W".format(data.gpuPptW), TextMain)
                }
            }
        }
    }
}

@Composable
private fun LabeledValue(label: String, value: String, color: Color) {
    Column {
        Text(label, color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text(value, color = color, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun HudCard(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardColor, RoundedCornerShape(16.dp))
            .border(1.dp, CardBorder, RoundedCornerShape(16.dp)),
    ) {
        content()
    }
}

@Composable
private fun CpuCard(data: Telemetry) {
    HudCard {
        Column(Modifier.padding(16.dp)) {
            CardTitle("CPU CORE", AccentCpu)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                LabeledValue("CLOCK", "${data.cpuFreqMhz} MHz", TextMain)
                LabeledValue(
                    "DIE TEMP",
                    "%.1f°C".format(data.cpuTempC),
                    statusColor(data.cpuTempC, DieThresh),
                )
            }
            Spacer(Modifier.height(12.dp))
            VrmTelemetryTable(data.cpu, vinMax = 13.0, voutMax = 1.2, ioutMax = 55.0, poutMax = 60.0)
        }
    }
}

@Composable
private fun GpuCard(data: Telemetry) {
    HudCard {
        Column(Modifier.padding(16.dp)) {
            CardTitle("GPU CORE", AccentGpu)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                LabeledValue("SCLK", "%.0f MHz".format(data.gpuSclkMhz), TextMain)
                LabeledValue(
                    "DIE TEMP",
                    "%.1f°C".format(data.gpuTempC),
                    statusColor(data.gpuTempC, DieThresh),
                )
            }
            Spacer(Modifier.height(12.dp))
            VrmTelemetryTable(data.gpu, vinMax = 13.0, voutMax = 0.9, ioutMax = 150.0, poutMax = 130.0)
        }
    }
}

@Composable
private fun CardTitle(title: String, accent: Color) {
    Text(title, color = accent, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun VrmTelemetryTable(
    rail: RailTelemetry,
    vinMax: Double,
    voutMax: Double,
    ioutMax: Double,
    poutMax: Double,
) {
    Column {
        Text("VRM TELEMETRY", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        VrmRow("Voltage In", "%.2f V".format(rail.vin), rail.vin, vinMax, TextMain)
        VrmRow("Voltage Out", "%.3f V".format(rail.vout), rail.vout, voutMax, TextMain)
        VrmRow("Current", "%.1f A".format(rail.iout), rail.iout, ioutMax, TextMain)
        VrmRow("Power", "%.1f W".format(rail.pout), rail.pout, poutMax, TextMain)
        VrmRow(
            "VRM Temp",
            "%.0f °C".format(rail.temp),
            rail.temp,
            100.0,
            statusColor(rail.temp, VrmThresh),
        )
        if (!rail.valid) {
            Text("нет данных", color = TextMuted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun VrmRow(label: String, value: String, current: Double, max: Double, valueColor: Color) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = TextMuted, fontSize = 13.sp)
            Text(value, color = valueColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(4.dp))
        MiniBar(current, max, valueColor)
    }
}

@Composable
private fun MiniBar(value: Double, max: Double, color: Color) {
    val fraction = if (max > 0) (value / max).coerceIn(0.0, 1.0) else 0.0
    Box(
        Modifier
            .fillMaxWidth()
            .height(4.dp)
            .background(Color(0xFF232B45), RoundedCornerShape(2.dp)),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.toFloat())
                .height(4.dp)
                .background(color, RoundedCornerShape(2.dp)),
        )
    }
}

@Composable
private fun OtherSensorsCard(data: Telemetry) {
    HudCard {
        Column(Modifier.padding(16.dp)) {
            CardTitle("OTHER SENSORS", TextMain)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                LabeledValue("FAN", "${data.fanRpm} RPM", TextMain)
                LabeledValue("PWM", "%.0f%%".format(data.fanPwmPct), TextMain)
            }
            Spacer(Modifier.height(12.dp))
            Text("TELEMETRY", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            if (data.nvmeTempC >= 0) {
                VrmRow(
                    "NVMe Temp",
                    "%.1f °C".format(data.nvmeTempC),
                    data.nvmeTempC,
                    85.0,
                    statusColor(data.nvmeTempC, NvmeThresh),
                )
            }
            if (data.nctT14C >= 0 && data.nctT15C >= 0) {
                val avg = (data.nctT14C + data.nctT15C) / 2
                VrmRow(
                    "Thermistors 14/15",
                    "%.1f / %.1f °C".format(data.nctT14C, data.nctT15C),
                    avg,
                    100.0,
                    statusColor(avg, BoardThresh),
                )
            }
        }
    }
}

@Composable
private fun MemoryCard(memory: MemoryTelemetry) {
    HudCard {
        Column(Modifier.padding(16.dp)) {
            CardTitle("GDDR6 · MEMORY", AccentGpu)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                LabeledValue(
                    "HOTSPOT",
                    memory.hotspotC?.let { "${if (memory.saturated) "≥" else ""}$it°C" } ?: "—",
                    memory.hotspotC?.let { statusColor(it.toDouble(), VrmThresh) } ?: TextMain,
                )
                LabeledValue(
                    "AVERAGE",
                    memory.averageC?.let { "%.1f°C".format(it) } ?: "—",
                    TextMain,
                )
            }
            Spacer(Modifier.height(12.dp))
            Text("CHIPS", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            MemoryChipsGrid(memory)
        }
    }
}

@Composable
private fun MemoryChipsGrid(memory: MemoryTelemetry) {
    val designators = listOf("U27", "U29", "U31", "U33", "U43", "U41", "U39", "U37")
    Column {
        memory.chipsC.chunked(4).forEachIndexed { rowIndex, rowChips ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowChips.forEachIndexed { colIndex, chip ->
                    val idx = rowIndex * 4 + colIndex
                    val isHotspot = memory.hotspotChip == idx
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .background(
                                if (isHotspot) Color(0xFF3A2A1A) else Color(0xFF1A2138),
                                RoundedCornerShape(8.dp),
                            )
                            .border(
                                1.dp,
                                if (isHotspot) AccentGpu else CardBorder,
                                RoundedCornerShape(8.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                designators.getOrElse(idx) { "U?" },
                                color = TextMuted,
                                fontSize = 9.sp,
                            )
                            Text(
                                chip?.let { "$it°" } ?: "—",
                                color = chip?.let { statusColor(it.toDouble(), VrmThresh) } ?: TextMuted,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
