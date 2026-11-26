#include <jni.h>
#include <cstdlib>
#include <cstring>
#include <string>
#include <vector>
#include "node.h"

extern "C"
JNIEXPORT jint JNICALL
Java_com_dansapk_nodejsrunner_RunnerService_startNodeWithArguments(
        JNIEnv *env, jobject, jobjectArray arguments) {
    const jsize count = env->GetArrayLength(arguments);
    std::vector<std::string> values;
    values.reserve(static_cast<size_t>(count));
    size_t totalBytes = 0;

    for (jsize i = 0; i < count; ++i) {
        jstring item = static_cast<jstring>(env->GetObjectArrayElement(arguments, i));
        const char *chars = env->GetStringUTFChars(item, nullptr);
        values.emplace_back(chars ? chars : "");
        totalBytes += values.back().size() + 1;
        if (chars) env->ReleaseStringUTFChars(item, chars);
        env->DeleteLocalRef(item);
    }

    std::vector<char> buffer(totalBytes, '\0');
    std::vector<char *> argv;
    argv.reserve(values.size());
    char *position = buffer.data();
    for (const std::string &value : values) {
        std::memcpy(position, value.c_str(), value.size() + 1);
        argv.push_back(position);
        position += value.size() + 1;
    }

    return static_cast<jint>(node::Start(static_cast<int>(argv.size()), argv.data()));
}
