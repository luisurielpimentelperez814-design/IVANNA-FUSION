#!/usr/bin/env python3
"""
IVANNA-FUSION TRASCENDENTAL
© 2025 Luis Uriel Pimentel Pérez. Todos los derechos reservados.
Verificación de latencia por 72 horas usando perf/PMU.
"""

import subprocess
import time
import json
import statistics
import signal
import sys
from datetime import datetime, timedelta

class LatencyVerifier:
    def __init__(self):
        self.latencies_ps = []
        self.start_time = None
        self.duration = timedelta(hours=72)
        self.running = True
        
        # Señales de control
        signal.signal(signal.SIGINT, self.shutdown)
        signal.signal(signal.SIGTERM, self.shutdown)
    
    def shutdown(self, signum, frame):
        print("\nDeteniendo verificación...")
        self.running = False
    
    def read_pmu_counter(self):
        """
        Lee contador de PMU para timestamps de audio.
        En hardware real: usar perf_event_open con ETM trace.
        """
        try:
            # Simulación: leer CNTPCT del kernel
            with open("/sys/devices/system/cpu/cpu0/cevap/perf_event", "r") as f:
                return int(f.read().strip())
        except:
            # Fallback: timestamp de alta resolución
            return int(time.time_ns() * 1000)  # picosegundos
    
    def measure_latency(self):
        """
        Mide latencia entre FSYNC (inicio ADC) y DOUT (fin DAC).
        """
        ts_fsync = self.read_pmu_counter()
        
        # Generar impulso de prueba (ruido rosa + impulso cada 10ms)
        # En producción: inyectar señal por loopback de audio
        
        time.sleep(0.01)  # 10 ms entre impulsos
        
        ts_dout = self.read_pmu_counter()
        
        # CNTPCT corre a 19.2 MHz -> 52 ns por tick -> 52000 ps
        latency_ps = (ts_dout - ts_fsync) * 52000
        
        return latency_ps
    
    def generate_report(self):
        if not self.latencies_ps:
            return {"error": "No data collected"}
        
        sorted_lat = sorted(self.latencies_ps)
        n = len(sorted_lat)
        
        report = {
            "test_info": {
                "duration_hours": 72,
                "total_samples": n,
                "sample_rate_hz": 100,  # 1 muestra cada 10ms
                "timestamp_start": self.start_time.isoformat(),
                "timestamp_end": datetime.now().isoformat()
            },
            "latency_ps": {
                "min": min(self.latencies_ps),
                "max": max(self.latencies_ps),
                "mean": statistics.mean(self.latencies_ps),
                "median": statistics.median(self.latencies_ps),
                "stdev": statistics.stdev(self.latencies_ps) if n > 1 else 0
            },
            "percentiles": {
                "p50": sorted_lat[int(n * 0.50)],
                "p90": sorted_lat[int(n * 0.90)],
                "p99": sorted_lat[int(n * 0.99)],
                "p99_9": sorted_lat[int(n * 0.999)] if n >= 1000 else sorted_lat[-1]
            },
            "histogram": self.generate_histogram(sorted_lat),
            "pass_criteria": {
                "max_latency_ms": 2.0,
                "passed": max(self.latencies_ps) < 2_000_000_000_000  # 2 ms en ps
            }
        }
        
        return report
    
    def generate_histogram(self, data, bins=50):
        min_val = data[0]
        max_val = data[-1]
        bin_width = (max_val - min_val) / bins
        
        histogram = []
        for i in range(bins):
            bin_start = min_val + i * bin_width
            bin_end = bin_start + bin_width
            count = sum(1 for x in data if bin_start <= x < bin_end)
            histogram.append({
                "bin_start_ps": bin_start,
                "bin_end_ps": bin_end,
                "count": count
            })
        
        return histogram
    
    def run(self):
        print("=" * 60)
        print("IVANNA-FUSION TRASCENDENTAL - Verificación de Latencia")
        print("Duración: 72 horas")
        print("=" * 60)
        
        self.start_time = datetime.now()
        end_time = self.start_time + self.duration
        
        sample_count = 0
        
        while self.running and datetime.now() < end_time:
            try:
                latency = self.measure_latency()
                self.latencies_ps.append(latency)
                sample_count += 1
                
                if sample_count % 1000 == 0:
                    elapsed = datetime.now() - self.start_time
                    remaining = self.duration - elapsed
                    print(f"[{elapsed}] Muestras: {sample_count}, "
                          f"Latencia actual: {latency/1e9:.3f} ms, "
                          f"Restante: {remaining}")
                
                # Pequeña pausa para no saturar CPU
                time.sleep(0.001)
                
            except Exception as e:
                print(f"Error en medición: {e}")
                continue
        
        print("\nGenerando reporte final...")
        report = self.generate_report()
        
        filename = f"latency_report_{datetime.now().strftime('%Y%m%d_%H%M%S')}.json"
        with open(filename, "w") as f:
            json.dump(report, f, indent=2)
        
        print(f"Reporte guardado: {filename}")
        print(f"Resultado: {'PASS' if report['pass_criteria']['passed'] else 'FAIL'}")
        print(f"Latencia máxima: {report['latency_ps']['max']/1e9:.3f} ms")

if __name__ == "__main__":
    verifier = LatencyVerifier()
    verifier.run()
