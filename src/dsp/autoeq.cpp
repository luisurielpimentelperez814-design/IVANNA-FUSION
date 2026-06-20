#include "autoeq.h"
#include <algorithm>
#include <cctype>

AutoEQ::AutoEQ() { loadBuiltInDatabase(); }
AutoEQ::~AutoEQ() {}

std::vector<float> AutoEQ::getHarmanTarget() {
    return {6.0f, 5.5f, 5.0f, 4.5f, 4.0f, 3.5f, 3.0f, 2.5f, 2.0f, 1.5f,
            1.0f, 0.5f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.5f, 1.0f, 1.5f,
            2.0f, 2.5f, 3.0f, 2.5f, 2.0f, 1.5f, 1.0f, 0.0f, -1.0f, -2.0f, -3.0f, -4.0f};
}
void AutoEQ::loadBuiltInDatabase() {
    auto addProfile = [this](const std::string& brand, const std::string& model, const std::vector<float>& curve) {
        HeadphoneProfile p;
        p.brand = brand; p.model = model; p.name = brand + " " + model;
        p.correctionCurve = curve; p.targetGainDb = 0.0f;
        profiles.push_back(p);
    };
    addProfile("Sennheiser", "HD 600", {2.0f, 1.5f, 1.0f, 0.5f, 0.0f, -0.5f, -1.0f, -1.0f, -0.5f, 0.0f, 0.5f, 1.0f, 1.5f, 2.0f, 2.5f, 3.0f, 2.5f, 2.0f, 1.5f, 1.0f, 0.5f, 0.0f, -0.5f, -1.0f, -1.5f, -2.0f, -2.5f, -3.0f, -3.0f, -3.0f, -3.0f});
    addProfile("Sennheiser", "HD 650", {2.5f, 2.0f, 1.5f, 1.0f, 0.5f, 0.0f, -0.5f, -1.0f, -1.0f, -0.5f, 0.0f, 0.5f, 1.0f, 1.5f, 2.0f, 2.5f, 2.0f, 1.5f, 1.0f, 0.5f, 0.0f, -0.5f, -1.0f, -1.5f, -2.0f, -2.5f, -3.0f, -3.5f, -3.5f, -3.5f, -3.5f});
    addProfile("Sennheiser", "HD 800 S", {1.5f, 1.0f, 0.5f, 0.0f, -0.5f, -1.0f, -1.5f, -2.0f, -1.5f, -1.0f, -0.5f, 0.0f, 0.5f, 1.0f, 1.5f, 2.0f, 2.5f, 3.0f, 2.5f, 2.0f, 1.5f, 1.0f, 0.5f, 0.0f, -0.5f, -1.0f, -1.5f, -2.0f, -2.5f, -3.0f, -3.0f});
    addProfile("Beyerdynamic", "DT 770 Pro", {3.0f, 2.5f, 2.0f, 1.5f, 1.0f, 0.5f, 0.0f, -0.5f, -1.0f, -0.5f, 0.0f, 0.5f, 1.0f, 1.5f, 2.0f, 2.5f, 3.0f, 3.5f, 4.0f, 3.5f, 3.0f, 2.5f, 2.0f, 1.5f, 1.0f, 0.5f, 0.0f, -0.5f, -1.0f, -1.5f, -2.0f});
    addProfile("Beyerdynamic", "DT 990 Pro", {2.5f, 2.0f, 1.5f, 1.0f, 0.5f, 0.0f, -0.5f, -1.0f, -0.5f, 0.0f, 0.5f, 1.0f, 1.5f, 2.0f, 2.5f, 3.0f, 3.5f, 4.0f, 4.5f, 4.0f, 3.5f, 3.0f, 2.5f, 2.0f, 1.5f, 1.0f, 0.5f, 0.0f, -0.5f, -1.0f, -1.5f});
    addProfile("Audio-Technica", "ATH-M50x", {3.5f, 3.0f, 2.5f, 2.0f, 1.5f, 1.0f, 0.5f, 0.0f, -0.5f, -1.0f, -0.5f, 0.0f, 0.5f, 1.0f, 1.5f, 2.0f, 2.5f, 3.0f, 2.5f, 2.0f, 1.5f, 1.0f, 0.5f, 0.0f, -0.5f, -1.0f, -1.5f, -2.0f, -2.5f, -3.0f, -3.0f});
    addProfile("Sony", "WH-1000XM4", {2.0f, 1.5f, 1.0f, 0.5f, 0.0f, -0.5f, -1.0f, -0.5f, 0.0f, 0.5f, 1.0f, 1.5f, 2.0f, 2.5f, 3.0f, 3.5f, 3.0f, 2.5f, 2.0f, 1.5f, 1.0f, 0.5f, 0.0f, -0.5f, -1.0f, -1.5f, -2.0f, -2.5f, -3.0f, -3.0f, -3.0f});
    addProfile("Sony", "MDR-7506", {1.0f, 0.5f, 0.0f, -0.5f, -1.0f, -1.5f, -2.0f, -1.5f, -1.0f, -0.5f, 0.0f, 0.5f, 1.0f, 1.5f, 2.0f, 2.5f, 3.0f, 3.5f, 4.0f, 3.5f, 3.0f, 2.5f, 2.0f, 1.5f, 1.0f, 0.5f, 0.0f, -0.5f, -1.0f, -1.5f, -2.0f});
    addProfile("AKG", "K702", {1.0f, 0.5f, 0.0f, -0.5f, -1.0f, -1.5f, -2.0f, -1.5f, -1.0f, -0.5f, 0.0f, 0.5f, 1.0f, 1.5f, 2.0f, 2.5f, 3.0f, 2.5f, 2.0f, 1.5f, 1.0f, 0.5f, 0.0f, -0.5f, -1.0f, -1.5f, -2.0f, -2.5f, -3.0f, -3.5f, -4.0f});
    addProfile("Audeze", "LCD-X", {1.5f, 1.0f, 0.5f, 0.0f, -0.5f, -1.0f, -1.0f, -0.5f, 0.0f, 0.5f, 1.0f, 1.5f, 2.0f, 2.5f, 3.0f, 3.5f, 3.0f, 2.5f, 2.0f, 1.5f, 1.0f, 0.5f, 0.0f, -0.5f, -1.0f, -1.5f, -2.0f, -2.5f, -3.0f, -3.5f, -4.0f});
    addProfile("Focal", "Clear", {2.0f, 1.5f, 1.0f, 0.5f, 0.0f, -0.5f, -1.0f, -0.5f, 0.0f, 0.5f, 1.0f, 1.5f, 2.0f, 2.5f, 3.0f, 3.5f, 3.0f, 2.5f, 2.0f, 1.5f, 1.0f, 0.5f, 0.0f, -0.5f, -1.0f, -1.5f, -2.0f, -2.5f, -3.0f, -3.5f, -4.0f});
    addProfile("Apple", "AirPods Pro", {2.5f, 2.0f, 1.5f, 1.0f, 0.5f, 0.0f, -0.5f, -1.0f, -0.5f, 0.0f, 0.5f, 1.0f, 1.5f, 2.0f, 2.5f, 3.0f, 2.5f, 2.0f, 1.5f, 1.0f, 0.5f, 0.0f, -0.5f, -1.0f, -1.5f, -2.0f, -2.5f, -3.0f, -3.0f, -3.0f, -3.0f});
    addProfile("Bose", "QC45", {2.0f, 1.5f, 1.0f, 0.5f, 0.0f, -0.5f, -1.0f, -0.5f, 0.0f, 0.5f, 1.0f, 1.5f, 2.0f, 2.5f, 3.0f, 3.5f, 3.0f, 2.5f, 2.0f, 1.5f, 1.0f, 0.5f, 0.0f, -0.5f, -1.0f, -1.5f, -2.0f, -2.5f, -3.0f, -3.0f, -3.0f});
}

std::vector<HeadphoneProfile> AutoEQ::searchHeadphones(const std::string& query) {
    std::vector<HeadphoneProfile> results;
    std::string queryLower = query;
    std::transform(queryLower.begin(), queryLower.end(), queryLower.begin(), ::tolower);
    for (const auto& p : profiles) {
        std::string nameLower = p.name;
        std::transform(nameLower.begin(), nameLower.end(), nameLower.begin(), ::tolower);
        if (nameLower.find(queryLower) != std::string::npos) results.push_back(p);
    }
    return results;
}

bool AutoEQ::getCorrectionCurve(const std::string& name, std::vector<float>& outCurve) {
    for (const auto& p : profiles) {
        if (p.name == name) { outCurve = p.correctionCurve; return true; }
    }
    return false;
}

std::vector<std::string> AutoEQ::getAllHeadphoneNames() const {
    std::vector<std::string> names;
    for (const auto& p : profiles) names.push_back(p.name);
    return names;
}

void AutoEQ::applyCorrectionToEQ(const std::string& headphoneName, std::vector<float>& eqGains, int numBands) {
    std::vector<float> curve;    if (!getCorrectionCurve(headphoneName, curve)) return;
    int bandMapping[] = {1, 3, 5, 8, 11, 14, 17, 20, 23, 26};
    eqGains.resize(numBands);
    for (int i = 0; i < numBands && i < 10; i++) {
        int srcIdx = bandMapping[i];
        if (srcIdx < (int)curve.size()) eqGains[i] = curve[srcIdx];
    }
}
