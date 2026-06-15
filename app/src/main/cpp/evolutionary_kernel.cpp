/*
 * IVANNA-FUSION TRASCENDENTAL
 * © 2025 Luis Uriel Pimentel Pérez. Todos los derechos reservados.
 */

#include <jni.h>
#include <cmath>
#include <cstdlib>
#include <cstring>
#include <random>

#define POPULATION_SIZE 128
#define GENOME_SIZE 256
#define ELITE_COUNT 4

struct Individual {
    uint8_t genome[GENOME_SIZE];
    float fitness;
};

struct Population {
    Individual individuals[POPULATION_SIZE];
    uint32_t generation;
    float bestFitness;
};

static Population g_population;
static std::mt19937 g_rng(42);

float evaluateFitness(const uint8_t *genome) {
    // Fitness basado en correlación con señal objetivo
    float sum = 0.0f;
    for (int i = 0; i < GENOME_SIZE; i++) {
        sum += static_cast<float>(genome[i]) / 255.0f;
    }
    return sum / GENOME_SIZE;
}

void initializePopulation() {
    for (int i = 0; i < POPULATION_SIZE; i++) {
        for (int j = 0; j < GENOME_SIZE; j++) {
            g_population.individuals[i].genome[j] = static_cast<uint8_t>(g_rng() % 256);
        }
        g_population.individuals[i].fitness = evaluateFitness(g_population.individuals[i].genome);
    }
    g_population.generation = 0;
    
    // Encontrar mejor fitness
    g_population.bestFitness = 0.0f;
    for (int i = 0; i < POPULATION_SIZE; i++) {
        if (g_population.individuals[i].fitness > g_population.bestFitness) {
            g_population.bestFitness = g_population.individuals[i].fitness;
        }
    }
}

void crossover(const uint8_t *parent1, const uint8_t *parent2, uint8_t *child) {
    int crossoverPoint = g_rng() % GENOME_SIZE;
    for (int i = 0; i < crossoverPoint; i++) {
        child[i] = parent1[i];
    }
    for (int i = crossoverPoint; i < GENOME_SIZE; i++) {
        child[i] = parent2[i];
    }
}

void mutate(uint8_t *genome, float mutationRate) {
    for (int i = 0; i < GENOME_SIZE; i++) {
        if (static_cast<float>(g_rng()) / g_rng.max() < mutationRate) {
            genome[i] = static_cast<uint8_t>(g_rng() % 256);
        }
    }
}

void evolveGeneration() {
    Individual newPopulation[POPULATION_SIZE];
    
    // Elitismo
    memcpy(newPopulation, g_population.individuals, sizeof(Individual) * ELITE_COUNT);
    
    // Generar resto
    for (int i = ELITE_COUNT; i < POPULATION_SIZE; i++) {
        // Selección por torneo
        int idx1 = g_rng() % POPULATION_SIZE;
        int idx2 = g_rng() % POPULATION_SIZE;
        const Individual *parent1 = (g_population.individuals[idx1].fitness > g_population.individuals[idx2].fitness) 
            ? &g_population.individuals[idx1] : &g_population.individuals[idx2];
        
        idx1 = g_rng() % POPULATION_SIZE;
        idx2 = g_rng() % POPULATION_SIZE;
        const Individual *parent2 = (g_population.individuals[idx1].fitness > g_population.individuals[idx2].fitness) 
            ? &g_population.individuals[idx1] : &g_population.individuals[idx2];
        
        crossover(parent1->genome, parent2->genome, newPopulation[i].genome);
        mutate(newPopulation[i].genome, 0.01f);
        newPopulation[i].fitness = evaluateFitness(newPopulation[i].genome);
    }
    
    memcpy(g_population.individuals, newPopulation, sizeof(newPopulation));
    g_population.generation++;
    
    // Actualizar mejor fitness
    g_population.bestFitness = 0.0f;
    for (int i = 0; i < POPULATION_SIZE; i++) {
        if (g_population.individuals[i].fitness > g_population.bestFitness) {
            g_population.bestFitness = g_population.individuals[i].fitness;
        }
    }
}

extern "C" {

JNIEXPORT void JNICALL
Java_com_ivannafusion_AudioEngine_nativeInitializeEvolution(JNIEnv *env, jobject thiz) {
    initializePopulation();
}

JNIEXPORT jfloat JNICALL
Java_com_ivannafusion_AudioEngine_nativeGetBestFitness(JNIEnv *env, jobject thiz) {
    return g_population.bestFitness;
}

JNIEXPORT jint JNICALL
Java_com_ivannafusion_AudioEngine_nativeGetGeneration(JNIEnv *env, jobject thiz) {
    return static_cast<jint>(g_population.generation);
}

JNIEXPORT void JNICALL
Java_com_ivannafusion_AudioEngine_nativeEvolveStep(JNIEnv *env, jobject thiz) {
    evolveGeneration();
}

} // extern "C"
