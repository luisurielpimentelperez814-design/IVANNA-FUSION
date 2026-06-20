#ifndef PRESET_MANAGER_H
#define PRESET_MANAGER_H
#include <vector>
#include <string>
#include <map>

struct AudioPreset {
    int id;
    std::string name;
    std::string category;
    std::vector<float> eqGains;
    float compressorThreshold;
    float compressorRatio;
    float reverbMix;
    float stereoWidth;
    float stereoBalance;
    float loudnessTarget;
    bool autoEnabled;
};

class PresetManager {
public:
    PresetManager();
    ~PresetManager();
    void loadBuiltInPresets();
    std::vector<AudioPreset> getAllPresets() const;
    std::vector<AudioPreset> getPresetsByCategory(const std::string& category) const;
    bool getPresetById(int id, AudioPreset& outPreset) const;
    std::vector<AudioPreset> searchPresets(const std::string& query) const;
    AudioPreset generatePresetFromRecommendation(const std::string& name, const std::string& category,
        const std::vector<float>& eqGains, float threshold, float ratio, float reverb, float width, float loudness);
    int addCustomPreset(const AudioPreset& preset);
    bool savePresetsToFile(const std::string& path) const;
    bool loadPresetsFromFile(const std::string& path);
    std::vector<std::string> getCategories() const;
private:
    std::vector<AudioPreset> presets;
    int nextId;
};
#endif
