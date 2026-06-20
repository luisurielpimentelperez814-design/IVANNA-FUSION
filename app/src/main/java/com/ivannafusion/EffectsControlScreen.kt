package com.ivannafusion

import android.os.Bundle
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class EffectsControlScreen : AppCompatActivity() {
    
    private var effectEnabled = false
    private var currentGain = 0.0f
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_effects_control)
        
        val btnToggle = findViewById<Button>(R.id.btnToggleEffect)
        val seekGain = findViewById<SeekBar>(R.id.seekGain)
        val textGain = findViewById<TextView>(R.id.textGainValue)
        val btnApply = findViewById<Button>(R.id.btnApplyEffect)
        
        btnToggle.setOnClickListener {
            effectEnabled = !effectEnabled
            btnToggle.text = if (effectEnabled) "Desactivar Efecto" else "Activar Efecto"
            Toast.makeText(this, "Efecto: ${if (effectEnabled) "Activado" else "Desactivado"}", Toast.LENGTH_SHORT).show()
        }
        
        seekGain.max = 40
        seekGain.progress = 20
        seekGain.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val gain = (progress - 20).toFloat()
                textGain.text = "Ganancia: ${gain}dB"
                if (fromUser) currentGain = gain
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        
        btnApply.setOnClickListener {
            try {
                IvannaNativeLib.setEnabled(effectEnabled)
                IvannaNativeLib.eqSetGain(0, currentGain)
                Toast.makeText(this, "Efecto aplicado: Gain=${currentGain}dB", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
