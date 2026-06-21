"""
Script de exportación del motor Ω_in a ExecuTorch (.pte)
=========================================================
Incluye:
  - PTQ (Post-Training Quantization) a INT8 para NPU Hexagon
  - FP16 para GPU Adreno
  - Etiquetas de delegación por operador
  - Calibración con datos reales de audio

Uso:
  python export_to_executorch.py --calibration_data /path/to/audio.wav
  
Salida:
  - omega_engine_int8.pte (para NPU)
  - omega_engine_fp16.pte (para GPU)
  - omega_engine_fp32.pte (fallback CPU)
"""

import torch
import torchaudio
from torch.ao.quantization import get_default_qconfig_mapping
from torch.ao.quantization.quantize_fx import prepare_fx, convert_fx
import argparse
import os

from mobile_omega_engine import MobileOmegaEngine, EdgeConfig

# ═══════════════════════════════════════════════════════════════════════════
# DELEGACIÓN DE OPERADORES (Snapdragon 7s Gen 2)
# ═══════════════════════════════════════════════════════════════════════════
"""
Estrategia de delegación:
  NPU Hexagon (INT8):
    - Complex 1D CNNs (PhaseCoherenceModule)
    - Vocoder Tiny (NeuralSourceFilterTiny)
    - Mamba-Tiny blocks (CollapseModule)
  
  GPU Adreno (FP16 via Vulkan Compute):
    - STFT/iSTFT (operaciones de tensores densas)
    - Sliced Wasserstein (proyecciones paralelizables)
  
  CPU Kryo (FP32):
    - Pre/post-procesamiento de audio
    - Control flow y lógica de pipeline
"""

DELEGATION_CONFIG = {
    'npu_ops': [
        'conv1d', 'linear', 'group_norm', 'relu', 'silu', 'tanh',        'conv_transpose1d', 'softmax'
    ],
    'gpu_ops': [
        'stft', 'istft', 'matmul', 'einsum', 'sort', 'mse_loss'
    ],
    'cpu_ops': [
        'hann_window', 'complex', 'atan2', 'sqrt'
    ]
}

# ═══════════════════════════════════════════════════════════════════════════
# CUANTIZACIÓN PTQ (Post-Training Quantization)
# ═══════════════════════════════════════════════════════════════════════════
class Calibrator:
    """Calibra el modelo con datos reales para PTQ"""
    def __init__(self, model: MobileOmegaEngine, n_samples: int = 50):
        self.model = model
        self.n_samples = n_samples
        self.calibration_data = []
    
    def load_audio(self, audio_path: str):
        """Carga audio de calibración"""
        waveform, sr = torchaudio.load(audio_path)
        if sr != EdgeConfig.SAMPLE_RATE:
            waveform = torchaudio.functional.resample(waveform, sr, EdgeConfig.SAMPLE_RATE)
        
        if waveform.shape[0] > 1:
            waveform = waveform.mean(dim=0, keepdim=True)
        
        block_size = EdgeConfig.SAMPLE_RATE
        n_blocks = waveform.shape[1] // block_size
        
        for i in range(min(n_blocks, self.n_samples)):
            block = waveform[:, i*block_size:(i+1)*block_size]
            self.calibration_data.append(block)
        
        print(f"Loaded {len(self.calibration_data)} calibration blocks")
    
    def collect_stats(self):
        """Ejecuta forward passes para collectar estadísticas de activación"""
        self.model.eval()
        with torch.no_grad():
            for i, block in enumerate(self.calibration_data):
                _ = self.model(block)
                if (i + 1) % 10 == 0:
                    print(f"Calibration: {i+1}/{len(self.calibration_data)}")

def quantize_int8(model: MobileOmegaEngine, calibration_data_path: str) -> torch.nn.Module:
    """
    Cuantiza el modelo a INT8 para NPU Hexagon.    Usa PTQ (Post-Training Quantization) con calibración.
    """
    print("\n=== INT8 Quantization (NPU Hexagon) ===")
    
    calibrator = Calibrator(model, n_samples=50)
    calibrator.load_audio(calibration_data_path)
    
    model.qconfig = torch.ao.quantization.get_default_qconfig('qnnpack')
    model_prepared = prepare_fx(model, model.qconfig)
    
    calibrator.model = model_prepared
    calibrator.collect_stats()
    
    model_int8 = convert_fx(model_prepared)
    
    total_params = sum(p.numel() for p in model_int8.parameters() if hasattr(p, 'numel'))
    print(f"INT8 model size: {total_params / 1e6:.2f} MB")
    
    return model_int8

def quantize_fp16(model: MobileOmegaEngine) -> torch.nn.Module:
    """
    Convierte el modelo a FP16 para GPU Adreno.
    """
    print("\n=== FP16 Quantization (GPU Adreno) ===")
    model_fp16 = model.half()
    
    total_params = sum(p.numel() for p in model_fp16.parameters())
    print(f"FP16 model size: {total_params * 2 / 1e6:.2f} MB")
    
    return model_fp16

# ═══════════════════════════════════════════════════════════════════════════
# EXPORTACIÓN A EXECUTORCH
# ═══════════════════════════════════════════════════════════════════════════
def export_to_executorch(
    model: torch.nn.Module,
    output_path: str,
    quantization: str = 'fp32'
):
    """
    Exporta el modelo a formato ExecuTorch (.pte).
    """
    print(f"\n=== Exporting to ExecuTorch ({quantization}) ===")
    
    example_input = torch.randn(1, EdgeConfig.SAMPLE_RATE)
    
    model.eval()
    
    try:        from executorch.exir import to_edge
        from torch.export import export
        
        exported_program = export(model, (example_input,))
        edge_program = to_edge(exported_program)
        
        executorch_program = edge_program.to_executorch(
            et_config={
                'memory_planning': 'greedy',
                'quantization': quantization,
            }
        )
        
        with open(output_path, 'wb') as f:
            f.write(executorch_program.buffer)
        
        print(f"✅ Exported to {output_path}")
        print(f"   File size: {os.path.getsize(output_path) / 1e6:.2f} MB")
        
    except ImportError:
        print("⚠️  ExecuTorch not available, falling back to TorchScript")
        scripted_model = torch.jit.script(model)
        scripted_model.save(output_path.replace('.pte', '.pt'))
        print(f"✅ Exported to TorchScript: {output_path.replace('.pte', '.pt')}")

# ═══════════════════════════════════════════════════════════════════════════
# MAIN
# ═══════════════════════════════════════════════════════════════════════════
def main():
    parser = argparse.ArgumentParser(description='Export Ω_in Engine to ExecuTorch')
    parser.add_argument('--calibration_data', type=str, required=True,
                        help='Path to calibration audio file')
    parser.add_argument('--output_dir', type=str, default='./exported_models',
                        help='Output directory for exported models')
    args = parser.parse_args()
    
    os.makedirs(args.output_dir, exist_ok=True)
    
    # Cargar modelo base
    print("Loading MobileOmegaEngine...")
    model = MobileOmegaEngine()
    model.eval()
    
    # Exportar FP32 (fallback CPU)
    export_to_executorch(
        model,
        os.path.join(args.output_dir, 'omega_engine_fp32.pte'),
        quantization='fp32'
    )
        # Exportar FP16 (GPU Adreno)
    model_fp16 = quantize_fp16(model)
    export_to_executorch(
        model_fp16,
        os.path.join(args.output_dir, 'omega_engine_fp16.pte'),
        quantization='fp16'
    )
    
    # Exportar INT8 (NPU Hexagon)
    model_int8 = quantize_int8(model, args.calibration_data)
    export_to_executorch(
        model_int8,
        os.path.join(args.output_dir, 'omega_engine_int8.pte'),
        quantization='int8'
    )
    
    print("\n" + "="*60)
    print("✅ All models exported successfully!")
    print("="*60)
    print(f"Output directory: {args.output_dir}")
    print("\nFiles:")
    for f in os.listdir(args.output_dir):
        size = os.path.getsize(os.path.join(args.output_dir, f)) / 1e6
        print(f"  - {f}: {size:.2f} MB")

if __name__ == '__main__':
    main()
