#ifndef AI_ASSISTANT_H
#define AI_ASSISTANT_H
#include <functional>
#include <string>
#include <memory>
#include "../dsp/ai_audio_analyzer.h"
#include "../dsp/convolver.h"
#include "../dsp/autoeq.h"
#include "../presets/preset_manager.h"

class AIAssistant {
public:
    AIAssistant(int sampleRate = 44100);
    ~AIAssistant();
    void processAudio(float* buffer, int numSamples, int channels);
    void setEnabled(bool enabled) { this->enabled = enabled; }
    bool isEnabled() const { return enabled; }
    void setAutoApply(bool autoApply) { this->autoApply = autoApply; }
    bool isAutoApply() const { return autoApply; }
    ContentType getCurrentContentType() const;
    std::string getCurrentContentTypeName() const;
    float getConfidence() const;
    AIRecommendation getCurrentRecommendation() const;
    AudioFeatures getFeatures() const;
    void applyPreset(int presetId);
    void applyAutoEQ(const std::string& headphoneName);
    void applyReverb(int type);
    std::vector<std::string> getAvailableHeadphones() const;
    std::vector<AudioPreset> getAvailablePresets() const;
    using ContentCallback = std::function<void(ContentType, float)>;
    using RecommendationCallback = std::function<void(const AIRecommendation&)>;
    using PresetAppliedCallback = std::function<void(const std::string&)>;
    using LogCallback = std::function<void(const std::string&)>;
    void setContentCallback(ContentCallback cb) { contentCb = cb; }
    void setRecommendationCallback(RecommendationCallback cb) { recCb = cb; }
    void setPresetAppliedCallback(PresetAppliedCallback cb) { presetCb = cb; }
    void setLogCallback(LogCallback cb) { logCb = cb; }
private:
    int sampleRate;
    bool enabled, autoApply;
    std::unique_ptr<AIAudioAnalyzer> analyzer;    std::unique_ptr<Convolver> convolver;
    std::unique_ptr<AutoEQ> autoEQ;
    std::unique_ptr<PresetManager> presetMgr;
    ContentType lastContentType;
    int stableCount;
    int processCounter;
    ContentCallback contentCb;
    RecommendationCallback recCb;
    PresetAppliedCallback presetCb;
    LogCallback logCb;
    void log(const std::string& msg);
    void onContentDetected(ContentType type, float confidence);
    void onRecommendation(const AIRecommendation& rec);
};
#endif
