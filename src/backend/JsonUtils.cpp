#include "JsonUtils.h"
#include <cctype>

std::optional<std::string> extractJsonStringField(const std::string &body, const std::string &key)
{
    const std::string pattern = "\"" + key + "\"";
    size_t pos = body.find(pattern);
    if (pos == std::string::npos)
        return std::nullopt;

    pos = body.find(':', pos);
    if (pos == std::string::npos)
        return std::nullopt;

    pos = body.find('"', pos);
    if (pos == std::string::npos)
        return std::nullopt;

    size_t start = pos + 1;
    size_t end = body.find('"', start);
    if (end == std::string::npos)
        return std::nullopt;

    return body.substr(start, end - start);
}

std::optional<double> extractJsonNumberField(const std::string &body, const std::string &key)
{
    const std::string pattern = "\"" + key + "\"";
    size_t pos = body.find(pattern);
    if (pos == std::string::npos)
        return std::nullopt;

    pos = body.find(':', pos);
    if (pos == std::string::npos)
        return std::nullopt;

    pos++;
    while (pos < body.size() && std::isspace(static_cast<unsigned char>(body[pos])))
        pos++;

    size_t end = pos;
    while (end < body.size())
    {
        char c = body[end];
        if (!(std::isdigit(static_cast<unsigned char>(c)) || c == '.' || c == '-'))
            break;
        end++;
    }
    if (end == pos)
        return std::nullopt;

    try
    {
        return std::stod(body.substr(pos, end - pos));
    }
    catch (...)
    {
        return std::nullopt;
    }
}

std::optional<long long> extractJsonIntField(const std::string &body, const std::string &key)
{
    return extractJsonIntFieldT<long long>(body, key);
}

std::string escapeJsonString(std::string s)
{
    size_t pos = 0;
    while ((pos = s.find('"', pos)) != std::string::npos)
    {
        s.insert(pos, "\\");
        pos += 2;
    }
    return s;
}
