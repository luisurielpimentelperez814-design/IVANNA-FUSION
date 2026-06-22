package com.ivannafusion.ai

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

class TrainingWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val adaptiveLearning = AdaptiveLearning(applicationContext)
        val modelManager = ModelManager(applicationContext)
        
        if (!adaptiveLearning.isReadyForTraining()) return Result.success()
        
        val experiences = adaptiveLearning.getTrainingData()
        val newVersion = modelManager.currentModelVersion.value + 1
        val modelData = ByteArray(2 * 1024 * 1024) // 2MB simulated model
        modelManager.saveFineTunedModel(modelData, newVersion)
        adaptiveLearning.clearAfterTraining(newVersion)
        modelManager.cleanupOldModels()
        
        return Result.success(workDataOf("new_version" to newVersion, "experiences_used" to experiences.size))
    }

    companion object {
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresCharging(true)
                .setRequiresBatteryNotLow(true)
                .setRequiredNetworkType(NetworkType.UNMETERED)
                .build()
            
            val request = PeriodicWorkRequestBuilder<TrainingWorker>(6, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()
            
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "ivanna_ai_training", ExistingPeriodicWorkPolicy.KEEP, request
            )
        }
    }
}
