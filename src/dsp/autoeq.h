#ifndef AUTOEQ_H
#define AUTOEQ_H
#include <vector>
#include <string>
#include <map>

struct HeadphoneProfile {
    std::string name;
    std::string brand;
    std::string model;
    std::vector<float> correctionCurve;
    float targetGainDb;
};

class AutoEQ {
public:
    AutoEQ();
    ~AutoEQ();
    void loadBuiltInDatabase();
    std::vector<HeadphoneProfile> searchHeadphones(const std::string& query);
    bool getCorrectionCurve(const std::string& name, std::vector<float>& outCurve);
    std::vector<std::string> getAllHeadphoneNames() const;
    void applyCorrectionToEQ(const std::string& headphoneName, std::vector<float>& eqGains, int numBands);
    int getNumProfiles() const { return profiles.size(); }
private:
    std::vector<HeadphoneProfile> profiles;
    std::vector<float> getHarmanTarget();
};
#endif
