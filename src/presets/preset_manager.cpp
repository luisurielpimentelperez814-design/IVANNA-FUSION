#include "preset_manager.h"#include <algorithm>
#include <fstream>
#include <sstream>

PresetManager::PresetManager() : nextId(100) { loadBuiltInPresets(); }
PresetManager::~PresetManager() {}

void PresetManager::loadBuiltInPresets() {
    presets.clear();
    auto add = [this](const std::string& name, const std::string& cat, const std::vector<float>& eq,
                      float thresh, float ratio, float reverb, float width, float loud) {
        AudioPreset p;
        p.id = (int)presets.size();
        p.name = name; p.category = cat; p.eqGains = eq;
        p.compressorThreshold = thresh; p.compressorRatio = ratio;
        p.reverbMix = reverb; p.stereoWidth = width;
        p.stereoBalance = 0.0f; p.loudnessTarget = loud; p.autoEnabled = true;
        presets.push_back(p);
    };
    add("Rock/Metal", "Music", {2,1,0,-1,0,1,2,3,2,1}, -15, 4.0, 0.10, 1.1, -10);
    add("Pop", "Music", {2,1,0,0,1,2,2,3,3,2}, -14, 3.0, 0.10, 1.1, -11);
    add("Classical", "Music", {0,0,0,0,0,0,0,0,0,0}, -24, 1.5, 0.15, 1.2, -20);
    add("Jazz", "Music", {1,1,0,0,0,1,1,0,-1,-1}, -20, 2.0, 0.10, 1.0, -14);
    add("Electronic/EDM", "Music", {4,3,1,0,-1,0,1,2,3,2}, -12, 3.5, 0.12, 1.3, -9);
    add("Hip-Hop/Rap", "Music", {4,3,1,0,0,1,2,2,1,0}, -13, 3.5, 0.08, 1.0, -10);
    add("Acoustic", "Music", {1,1,0,0,0,1,2,1,0,-1}, -18, 2.0, 0.12, 1.0, -14);
    add("Cinema", "Movie", {3,2,1,0,0,1,1,2,2,1}, -18, 2.5, 0.20, 1.4, -14);
    add("Dialogue Boost", "Movie", {-1,0,1,2,3,4,4,3,2,1}, -16, 3.0, 0.05, 0.9, -14);
    add("Night Mode", "Movie", {2,1,0,0,0,0,0,0,-1,-2}, -20, 2.0, 0.10, 0.8, -18);
    add("FPS Gaming", "Gaming", {2,1,0,0,1,2,3,3,2,1}, -16, 2.5, 0.15, 1.3, -12);
    add("RPG/Ambient", "Gaming", {1,1,0,0,0,1,1,2,2,1}, -18, 2.0, 0.20, 1.2, -14);
    add("Podcast", "Speech", {-2,-1,0,1,2,3,3,2,1,0}, -18, 3.0, 0.05, 0.8, -16);
    add("Voice Call", "Speech", {-3,-2,-1,1,3,4,3,2,0,-2}, -15, 4.0, 0.02, 0.7, -16);
    add("Audiobook", "Speech", {-1,0,0,1,2,3,2,1,0,-1}, -20, 2.5, 0.03, 0.8, -18);
    add("Bass Boost", "EQ", {6,4,2,0,-1,0,0,0,0,0}, -18, 2.0, 0.0, 1.0, -12);
}

std::vector<AudioPreset> PresetManager::getAllPresets() const { return presets; }
std::vector<AudioPreset> PresetManager::getPresetsByCategory(const std::string& category) const {
    std::vector<AudioPreset> result;
    for (const auto& p : presets) if (p.category == category) result.push_back(p);
    return result;
}
bool PresetManager::getPresetById(int id, AudioPreset& out) const {
    for (const auto& p : presets) if (p.id == id) { out = p; return true; }
    return false;
}
std::vector<AudioPreset> PresetManager::searchPresets(const std::string& query) const {
    std::vector<AudioPreset> result;
    std::string q = query; std::transform(q.begin(), q.end(), q.begin(), ::tolower);    for (const auto& p : presets) {
        std::string n = p.name; std::transform(n.begin(), n.end(), n.begin(), ::tolower);
        if (n.find(q) != std::string::npos) result.push_back(p);
    }
    return result;
}
AudioPreset PresetManager::generatePresetFromRecommendation(const std::string& name, const std::string& cat,
    const std::vector<float>& eq, float thresh, float ratio, float reverb, float width, float loud) {
    AudioPreset p; p.id = nextId++; p.name = name; p.category = cat; p.eqGains = eq;
    p.compressorThreshold = thresh; p.compressorRatio = ratio; p.reverbMix = reverb;
    p.stereoWidth = width; p.stereoBalance = 0.0f; p.loudnessTarget = loud; p.autoEnabled = true;
    return p;
}
int PresetManager::addCustomPreset(const AudioPreset& preset) {
    AudioPreset p = preset; p.id = nextId++; presets.push_back(p); return p.id;
}
std::vector<std::string> PresetManager::getCategories() const {
    std::vector<std::string> cats;
    for (const auto& p : presets)
        if (std::find(cats.begin(), cats.end(), p.category) == cats.end()) cats.push_back(p.category);
    return cats;
}
bool PresetManager::savePresetsToFile(const std::string& path) const {
    std::ofstream f(path); if (!f.is_open()) return false;
    f << presets.size() << "\n";
    for (const auto& p : presets) {
        f << p.id << "|" << p.name << "|" << p.category << "|";
        for (float g : p.eqGains) f << g << ",";
        f << "|" << p.compressorThreshold << "|" << p.compressorRatio << "|";
        f << p.reverbMix << "|" << p.stereoWidth << "|" << p.stereoBalance << "|";
        f << p.loudnessTarget << "|" << p.autoEnabled << "\n";
    }
    return true;
}
bool PresetManager::loadPresetsFromFile(const std::string& path) {
    std::ifstream f(path); if (!f.is_open()) return false;
    int count; f >> count; presets.clear(); std::string line; std::getline(f, line);
    for (int i = 0; i < count && std::getline(f, line); i++) {
        AudioPreset p; std::istringstream ss(line); std::string token;
        std::getline(ss, token, '|'); p.id = std::stoi(token);
        std::getline(ss, p.name, '|'); std::getline(ss, p.category, '|');
        std::getline(ss, token, '|'); std::istringstream eqs(token); std::string eqTok;
        while (std::getline(eqs, eqTok, ',')) if (!eqTok.empty()) p.eqGains.push_back(std::stof(eqTok));
        std::getline(ss, token, '|'); p.compressorThreshold = std::stof(token);
        std::getline(ss, token, '|'); p.compressorRatio = std::stof(token);
        std::getline(ss, token, '|'); p.reverbMix = std::stof(token);
        std::getline(ss, token, '|'); p.stereoWidth = std::stof(token);
        std::getline(ss, token, '|'); p.stereoBalance = std::stof(token);
        std::getline(ss, token, '|'); p.loudnessTarget = std::stof(token);
        std::getline(ss, token, '|'); p.autoEnabled = (token == "1");        presets.push_back(p); if (p.id >= nextId) nextId = p.id + 1;
    }
    return true;
}
