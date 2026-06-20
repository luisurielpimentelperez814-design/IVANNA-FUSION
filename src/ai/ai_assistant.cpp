#include "ai_assistant.h"
#include <iostream>

AIAssistant::AIAssistant(int sr) : sampleRate(sr), enabled(true), autoApply(true),
    lastContentType(ContentType::Unknown), stableCount(0), processCounter(0) {
    analyzer = std::make_unique<AIAudioAnalyzer>(sr, 2048);
    convolver = std::make_unique<Convolver>(1024);
    autoEQ = std::make_unique<AutoEQ>();
    presetMgr = std::make_unique<PresetManager>();
    log("AI Assistant inicializado");
}

AIAssistant::~AIAssistant() {}

void AIAssistant::processAudio(float* buffer, int numSamples, int channels) {
    if (!enabled) return;
    analyzer->analyze(buffer, numSamples, channels);
    processCounter++;
    if (processCounter % 10 == 0) {
        ContentType current = analyzer->detectContentType();
        float conf = analyzer->getConfidence();
        if (current == lastContentType) {
            stableCount++;
            if (stableCount == 5) {
                onContentDetected(current, conf);
                AIRecommendation rec = analyzer->generateRecommendation();
                onRecommendation(rec);
                if (autoApply && rec.suggestedPresetId >= 0) applyPreset(rec.suggestedPresetId);
            }
        } else { lastContentType = current; stableCount = 0; }
    }
    convolver->process(buffer, numSamples, channels);}

void AIAssistant::onContentDetected(ContentType type, float confidence) {
    log("Contenido detectado: " + analyzer->getContentTypeName() + " (" +
        std::to_string((int)(confidence * 100)) + "%)");
    if (contentCb) contentCb(type, confidence);
}

void AIAssistant::onRecommendation(const AIRecommendation& rec) {
    log("Recomendacion: " + rec.description);
    if (recCb) recCb(rec);
}

ContentType AIAssistant::getCurrentContentType() const { return analyzer->detectContentType(); }
std::string AIAssistant::getCurrentContentTypeName() const { return analyzer->getContentTypeName(); }
float AIAssistant::getConfidence() const { return analyzer->getConfidence(); }
AIRecommendation AIAssistant::getCurrentRecommendation() const { return analyzer->generateRecommendation(); }
AudioFeatures AIAssistant::getFeatures() const { return analyzer->getFeatures(); }

void AIAssistant::applyPreset(int presetId) {
    AudioPreset p;
    if (!presetMgr->getPresetById(presetId, p)) { log("Preset no encontrado: " + std::to_string(presetId)); return; }
    log("Aplicando preset: " + p.name);
    if (presetCb) presetCb(p.name);
}

void AIAssistant::applyAutoEQ(const std::string& headphoneName) {
    std::vector<float> curve;
    if (autoEQ->getCorrectionCurve(headphoneName, curve)) log("AutoEQ aplicado: " + headphoneName);
    else log("Auriculares no encontrados: " + headphoneName);
}

void AIAssistant::applyReverb(int type) {
    switch (type) {
        case 0: convolver->useSmallRoomIR(); log("Reverb: Small Room"); break;
        case 1: convolver->useMediumRoomIR(); log("Reverb: Medium Room"); break;
        case 2: convolver->useLargeHallIR(); log("Reverb: Large Hall"); break;
        case 3: convolver->usePlateReverbIR(); log("Reverb: Plate"); break;
        case 4: convolver->useCabinetIR(); log("Reverb: Cabinet"); break;
        case 5: convolver->useHeadphoneCrossfeedIR(); log("Reverb: Crossfeed"); break;
        default: log("Tipo de reverb invalido"); break;
    }
}

std::vector<std::string> AIAssistant::getAvailableHeadphones() const { return autoEQ->getAllHeadphoneNames(); }
std::vector<AudioPreset> AIAssistant::getAvailablePresets() const { return presetMgr->getAllPresets(); }
void AIAssistant::log(const std::string& msg) { if (logCb) logCb(msg); }
