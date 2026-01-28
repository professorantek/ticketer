#pragma once

#include <string>
#include <optional>

// ======================================================
// Minimalne narzędzia JSON (bez bibliotek)
// + Wymóg: Generic (template)
// ======================================================
std::optional<std::string> extractJsonStringField(const std::string &body, const std::string &key);
std::optional<double> extractJsonNumberField(const std::string &body, const std::string &key);

// Generic: template parser liczby całkowitej z JSON
template <typename IntT>
std::optional<IntT> extractJsonIntFieldT(const std::string &body, const std::string &key)
{
    auto numOpt = extractJsonNumberField(body, key);
    if (!numOpt)
        return std::nullopt;
    return static_cast<IntT>(*numOpt);
}

std::optional<long long> extractJsonIntField(const std::string &body, const std::string &key);

// proste escapowanie do JSON
std::string escapeJsonString(std::string s);
