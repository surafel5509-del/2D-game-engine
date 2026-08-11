#pragma once

#include <string>
#include <GLES3/gl3.h>

class Shader {
public:
    Shader();
    ~Shader();

    bool loadFromSource(const std::string& vertexSrc, const std::string& fragmentSrc);
    void use() const;
    GLuint id() const { return program_; }

private:
    GLuint compileShader(GLenum type, const std::string& src);
    GLuint program_ = 0;
};
