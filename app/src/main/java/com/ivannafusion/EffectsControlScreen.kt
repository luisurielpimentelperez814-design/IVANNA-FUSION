package com.ivannafusion

import android.media.audiofx.AudioEffect
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.lang.reflect.Constructor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

private const val TAG = "IVANNA-Effects"
val IVANNA_EFFECT_UUID: UUID = UUID.fromString("7b3be4ec-c23c-4e6e-8c6d-49e5f4d54ea3")

object EffectParams {
    const val PARAM_PEQ_GAIN_BASE = 0x00
    const val PARAM_COMP_THRESHOLD = 0x50
    const val PARAM_COMP_RATIO = 0x51
    const val PARAM_EXC_DRIVE = 0x60
    const val PARAM_EXC_MIX = 0x61
    const val PARAM_GLOBAL_BYPASS = 0x70
    const val PARAM_PRESET_LOAD = 0x72
}

private val BG_TOP = Color(0xFF0A0A0F)
private val BG_BOT = Color(0xFF0D0D1A)
private val ACCENT2 = Color(0xFF00E5FF)
private val CARD_BG = Color(0xFF12121E)
class CustomAudioEffect(priority: Int = 0, vararg uuids: UUID) {
    private var effect: AudioEffect? = null
    
    init {
        try {
            val constructor: Constructor<*> = AudioEffect::class.java.getDeclaredConstructor(
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                UUID::class.java,
                UUID::class.java
            )
            constructor.isAccessible = true
            effect = constructor.newInstance(priority, 0, uuids[0], uuids[0]) as AudioEffect
            effect?.enabled = true
        } catch (e: Exception) {
            Log.e(TAG, "Error: ${e.message}")
            effect = null
        }
    }
    
    fun setParameter(paramId: Int, value: Float): Boolean {
        val eff = effect ?: return false
        return try {
            val data = ByteBuffer.allocate(8).order(ByteOrder.nativeOrder())
            data.putInt(paramId)
            data.putFloat(value)
            eff.command(2, data.array().size, data.array()) == AudioEffect.SUCCESS
        } catch (e: Exception) { false }
    }
    
    fun setParameterInt(paramId: Int, value: Int): Boolean {
        val eff = effect ?: return false
        return try {
            val data = ByteBuffer.allocate(8).order(ByteOrder.nativeOrder())
            data.putInt(paramId)
            data.putInt(value)
            eff.command(2, data.array().size, data.array()) == AudioEffect.SUCCESS
        } catch (e: Exception) { false }
    }
    
    fun release() {
        effect?.release()
        effect = null
    }
}

object EffectsController {
    private var customEffect: CustomAudioEffect? = null
    var isConnected = false        private set

    fun connect(context: android.content.Context): Boolean {
        return try {
            customEffect = CustomAudioEffect(0, IVANNA_EFFECT_UUID)
            isConnected = customEffect != null
            isConnected
        } catch (e: Exception) {
            isConnected = false
            false
        }
    }

    fun disconnect() {
        customEffect?.release()
        customEffect = null
        isConnected = false
    }

    fun setParam(paramId: Int, value: Float): Boolean = customEffect?.setParameter(paramId, value) ?: false
    fun setParamInt(paramId: Int, value: Int): Boolean = customEffect?.setParameterInt(paramId, value) ?: false
    fun loadPreset(presetId: Int): Boolean = setParam(EffectParams.PARAM_PRESET_LOAD, presetId.toFloat())
    fun setGlobalBypass(enabled: Boolean): Boolean = setParamInt(EffectParams.PARAM_GLOBAL_BYPASS, if (enabled) 1 else 0)
}

@Composable
fun EffectsControlScreen(navController: NavController) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    var connected by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        connected = withContext(Dispatchers.IO) { EffectsController.connect(context) }
    }

    Box(modifier = Modifier.fillMaxSize().background(brush = Brush.verticalGradient(listOf(BG_TOP, BG_BOT)))) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(16.dp)) {
            Text("CONTROL DE EFECTOS", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = ACCENT2)
            Spacer(modifier = Modifier.height(16.dp))
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = CARD_BG)) {
                Text("Estado: ${if (connected) "Conectado" else "Desconectado"}",
                    color = if (connected) Color(0xFF00FF9C) else Color(0xFFFF5555),
                    modifier = Modifier.padding(16.dp))
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { navController.popBackStack() }, modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = ACCENT2)) {
                Text("VOLVER", color = Color.Black)
            }
        }    }

    DisposableEffect(Unit) { onDispose { EffectsController.disconnect() } }
}
