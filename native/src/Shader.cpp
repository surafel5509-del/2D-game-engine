#include "../include/Shader.h"
#include <android/log.h>
#include <vector>
#include <cassert>

static void logGlError(const char* tag, const std::string& msg) {
    __android_log_print(ANDROID_LOG_ERROR, tag, "%s", msg.c_str());
}

Shader::Shader() {}

Shader::~Shader() {
    if (program_) glDeleteProgram(program_);
}

GLuint Shader::compileShader(GLenum type, const std::string& src) {
    GLuint shader = glCreateShader(type);
    const char* s = src.c_str();
    glShaderSource(shader, 1, &s, nullptr);
    glCompileShader(shader);
    GLint status = 0;
    glGetShaderiv(shader, GL_COMPILE_STATUS, &status);
    if (!status) {
        GLint len = 0;
        glGetShaderiv(shader, GL_INFO_LOG_LENGTH, &len);
        std::vector<char> buf(len + 1);
        glGetShaderInfoLog(shader, len, nullptr, buf.data());
        logGlError("Future2D", std::string("Shader compile error: ") + buf.data());
        glDeleteShader(shader);
        return 0;
    }
    return shader;
}

bool Shader::loadFromSource(const std::string& vertexSrc, const std::string& fragmentSrc) {
    GLuint vs = compileShader(GL_VERTEX_SHADER, vertexSrc);
    GLuint fs = compileShader(GL_FRAGMENT_SHADER, fragmentSrc);
    if (!vs || !fs) return false;

    program_ = glCreateProgram();
    glAttachShader(program_, vs);
    glAttachShader(program_, fs);
    glLinkProgram(program_);

    GLint status = 0;
    glGetProgramiv(program_, GL_LINK_STATUS, &status);
    if (!status) {
        GLint len = 0;
        glGetProgramiv(program_, GL_INFO_LOG_LENGTH, &len);
        std::vector<char> buf(len + 1);
        glGetProgramInfoLog(program_, len, nullptr, buf.data());
        logGlError("Future2D", std::string("Program link error: ") + buf.data());
        glDeleteProgram(program_);
        program_ = 0;
        glDeleteShader(vs);
        glDeleteShader(fs);
        return false;
    }

    glDetachShader(program_, vs);
    glDetachShader(program_, fs);
    glDeleteShader(vs);
    glDeleteShader(fs);
    return true;
}

void Shader::use() const {
    if (program_) glUseProgram(program_);
}
