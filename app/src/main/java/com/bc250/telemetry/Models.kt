package com.bc250.telemetry

import org.json.JSONObject

/** Mirrors one VRM rail (cpu/gpu) from bc250_telemetry.cpp's PMBus reads. */
data class RailTelemetry(
    val valid: Boolean = false,
    val vin: Double = 0.0,
    val vout: Double = 0.0,
    val iout: Double = 0.0,
    val pout: Double = 0.0,
    val temp: Double = 0.0,
    val ioutWarning: Boolean = false,
    val ioutFault: Boolean = false,
    val tempWarning: Boolean = false,
    val tempFault: Boolean = false,
) {
    companion object {
        fun parse(obj: JSONObject?): RailTelemetry {
            obj ?: return RailTelemetry()
            return RailTelemetry(
                valid = obj.optBoolean("valid", false),
                vin = obj.optDouble("vin", 0.0),
                vout = obj.optDouble("vout", 0.0),
                iout = obj.optDouble("iout", 0.0),
                pout = obj.optDouble("pout", 0.0),
                temp = obj.optDouble("temp", 0.0),
                ioutWarning = obj.optBoolean("iout_warning", false),
                ioutFault = obj.optBoolean("iout_fault", false),
                tempWarning = obj.optBoolean("temp_warning", false),
                tempFault = obj.optBoolean("temp_fault", false),
            )
        }
    }
}

data class MemoryTelemetry(
    val valid: Boolean = false,
    val status: String = "unavailable",
    val chipsC: List<Int?> = emptyList(),
    val averageC: Double? = null,
    val hotspotC: Int? = null,
    val hotspotChip: Int? = null,
    val saturated: Boolean = false,
) {
    companion object {
        fun parse(obj: JSONObject?): MemoryTelemetry {
            obj ?: return MemoryTelemetry()
            val chips = mutableListOf<Int?>()
            obj.optJSONArray("chips_c")?.let { arr ->
                for (i in 0 until arr.length()) {
                    chips += if (arr.isNull(i)) null else arr.optInt(i)
                }
            }
            return MemoryTelemetry(
                valid = obj.optBoolean("valid", false),
                status = obj.optString("status", "unavailable"),
                chipsC = chips,
                averageC = if (obj.isNull("average_c")) null else obj.optDouble("average_c"),
                hotspotC = if (obj.isNull("hotspot_c")) null else obj.optInt("hotspot_c"),
                hotspotChip = if (obj.isNull("hotspot_chip")) null else obj.optInt("hotspot_chip"),
                saturated = obj.optBoolean("saturated", false),
            )
        }
    }
}

/** Full `/api/telemetry` snapshot, refreshed by the daemon roughly every 700 ms. */
data class Telemetry(
    val cpu: RailTelemetry = RailTelemetry(),
    val gpu: RailTelemetry = RailTelemetry(),
    val totalPower: Double = 0.0,
    val totalPowerValid: Boolean = false,

    val cpuTempC: Double = -1.0,
    val cpuFreqMhz: Long = -1,
    val gpuTempC: Double = -1.0,
    val gpuSclkMhz: Double = -1.0,
    val gpuPptW: Double = -1.0,
    val nvmeTempC: Double = -1.0,
    val nctT14C: Double = -1.0,
    val nctT15C: Double = -1.0,

    val fanRpm: Long = -1,
    val fanPwmPct: Double = -1.0,

    val memory: MemoryTelemetry = MemoryTelemetry(),
) {
    companion object {
        fun parse(json: String): Telemetry? {
            return try {
                val root = JSONObject(json)
                if (root.has("error")) return null
                val hw = root.optJSONObject("hardware")
                val sw = root.optJSONObject("software")
                val cooling = root.optJSONObject("cooling")
                val mem = root.optJSONObject("memory")
                Telemetry(
                    cpu = RailTelemetry.parse(hw?.optJSONObject("cpu")),
                    gpu = RailTelemetry.parse(hw?.optJSONObject("gpu")),
                    totalPower = hw?.optDouble("total_power", 0.0) ?: 0.0,
                    totalPowerValid = hw?.optBoolean("total_power_valid", false) ?: false,
                    cpuTempC = sw?.optDouble("cpu_temp_c", -1.0) ?: -1.0,
                    cpuFreqMhz = sw?.optLong("cpu_freq_mhz", -1) ?: -1,
                    gpuTempC = sw?.optDouble("gpu_temp_c", -1.0) ?: -1.0,
                    gpuSclkMhz = sw?.optDouble("gpu_sclk_mhz", -1.0) ?: -1.0,
                    gpuPptW = sw?.optDouble("gpu_ppt_w", -1.0) ?: -1.0,
                    nvmeTempC = sw?.optDouble("nvme_temp_c", -1.0) ?: -1.0,
                    nctT14C = sw?.optDouble("nct_t14_c", -1.0) ?: -1.0,
                    nctT15C = sw?.optDouble("nct_t15_c", -1.0) ?: -1.0,
                    fanRpm = cooling?.optLong("fan_rpm", -1) ?: -1,
                    fanPwmPct = cooling?.optDouble("fan_pwm_pct", -1.0) ?: -1.0,
                    memory = MemoryTelemetry.parse(mem),
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}
