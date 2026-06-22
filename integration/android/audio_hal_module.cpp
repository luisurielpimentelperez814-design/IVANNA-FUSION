// IVANNA Android Audio HAL Integration
// Pure wrapper - NO DSP logic here, delegates to core

#include <hardware/audio_effect.h>
#include <cstring>

// Forward declaration of pipeline (defined in core)
namespace ivanna::pipeline {
    class AudioPipeline;
}

struct IvannaEffectContext {
    ivanna::pipeline::AudioPipeline* pipeline;
    bool initialized;
};

extern "C" {

static int ivanna_effect_process(effect_handle_t self, 
                                  audio_buffer_t* in, 
                                  audio_buffer_t* out) {
    auto* ctx = reinterpret_cast<IvannaEffectContext*>(self);
    
    if (!ctx || !ctx->initialized || !ctx->pipeline) {
        // Fallback: passthrough (never block audio)
        if (in && out && in->raw && out->raw) {
            std::memcpy(out->raw, in->raw, in->frameCount * sizeof(int16_t));
        }
        return 0;
    }
    
    // Lock-free delegation to pipeline
    // Note: In production, this would convert int16 to float, process, and convert back
    if (in && out && in->raw && out->raw) {
        std::memcpy(out->raw, in->raw, in->frameCount * sizeof(int16_t));
    }
    
    return 0;
}

static int ivanna_effect_command(effect_handle_t self, uint32_t cmdCode, 
                                  uint32_t cmdSize, void* cmdData,
                                  uint32_t* replySize, void* replyData) {
    // Handle effect commands (placeholder)
    return 0;
}

static int ivanna_effect_init(effect_handle_t self) {
    auto* ctx = reinterpret_cast<IvannaEffectContext*>(self);
    if (ctx) {
        ctx->initialized = true;
    }
    return 0;
}

static int ivanna_effect_release(effect_handle_t self) {
    auto* ctx = reinterpret_cast<IvannaEffectContext*>(self);
    if (ctx) {
        ctx->initialized = false;
    }
    return 0;
}

audio_effect_library_t AUDIO_EFFECT_LIBRARY_INFO_SYM = {
    .tag = AUDIO_EFFECT_LIBRARY_TAG,
    .version = EFFECT_LIBRARY_VERSION_CURRENT,
    .name = "IVANNA Fusion OEM",
    .implementor = "IVANNA Labs",
    .create_effect = [](const effect_uuid_t*, int32_t, int32_t, effect_handle_t* out) {
        auto* ctx = new IvannaEffectContext();
        ctx->initialized = false;
        ctx->pipeline = nullptr;  // Would be initialized with actual pipeline
        *out = reinterpret_cast<effect_handle_t>(ctx);
        return 0;
    },
    .release_effect = [](effect_handle_t h) {
        delete reinterpret_cast<IvannaEffectContext*>(h);
        return 0;
    },
    .init = ivanna_effect_init,
    .release = ivanna_effect_release,
    .process = ivanna_effect_process,
    .command = ivanna_effect_command
};

} // extern "C"
