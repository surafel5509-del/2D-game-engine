#include "nova_renderer.hpp"

#include <algorithm>
#include <cmath>
#include <cstddef>

#include <GLES2/gl2.h>

namespace nova::render {
namespace {

constexpr const char* kVertexShader = R"GLSL(
attribute vec2 aPosition;
attribute vec2 aUv;
attribute vec4 aColor;
uniform mat4 uProjection;
varying vec2 vUv;
varying vec4 vColor;
void main(){ vUv=aUv; vColor=aColor; gl_Position=uProjection*vec4(aPosition,0.0,1.0); }
)GLSL";

constexpr const char* kFragmentShader = R"GLSL(
precision mediump float;
uniform sampler2D uTexture;
varying vec2 vUv;
varying vec4 vColor;
void main(){ gl_FragColor=texture2D(uTexture,vUv)*vColor; }
)GLSL";

bool compileShader(GLuint shader,const char* source){
    glShaderSource(shader,1,&source,nullptr); glCompileShader(shader);
    GLint ok=GL_FALSE; glGetShaderiv(shader,GL_COMPILE_STATUS,&ok); return ok==GL_TRUE;
}

} // namespace

Renderer2D::Renderer2D(){ vertices_.reserve(static_cast<std::size_t>(maxSprites_)*6u); }
Renderer2D::~Renderer2D(){ shutdown(); }

bool Renderer2D::initialize(){
    if(initialized_) return true;
    if(!createProgram() || !createBuffers()){ destroyGlResources(); return false; }
    initialized_=true; return true;
}

void Renderer2D::shutdown(){ destroyGlResources(); initialized_=false; frameActive_=false; }

void Renderer2D::resize(int width,int height){ camera_.width=static_cast<float>(std::max(1,width)); camera_.height=static_cast<float>(std::max(1,height)); glViewport(0,0,width,height); }

bool Renderer2D::createProgram(){
    const GLuint vs=glCreateShader(GL_VERTEX_SHADER), fs=glCreateShader(GL_FRAGMENT_SHADER);
    if(!vs||!fs) return false;
    if(!compileShader(vs,kVertexShader)||!compileShader(fs,kFragmentShader)){ glDeleteShader(vs); glDeleteShader(fs); return false; }
    program_=glCreateProgram(); if(!program_){ glDeleteShader(vs); glDeleteShader(fs); return false; }
    glAttachShader(program_,vs); glAttachShader(program_,fs); glBindAttribLocation(program_,0,"aPosition"); glBindAttribLocation(program_,1,"aUv"); glBindAttribLocation(program_,2,"aColor"); glLinkProgram(program_);
    GLint ok=GL_FALSE; glGetProgramiv(program_,GL_LINK_STATUS,&ok); glDeleteShader(vs); glDeleteShader(fs);
    if(ok!=GL_TRUE){ glDeleteProgram(program_); program_=0; return false; }
    positionLoc_=0; uvLoc_=1; colorLoc_=2; textureLoc_=glGetUniformLocation(program_,"uTexture"); projectionLoc_=glGetUniformLocation(program_,"uProjection"); return textureLoc_>=0&&projectionLoc_>=0;
}

bool Renderer2D::createBuffers(){ glGenBuffers(1,&vbo_); return vbo_!=0; }
bool Renderer2D::ensureContextResources(){ return initialized_&&program_!=0&&vbo_!=0; }

void Renderer2D::beginFrame(float r,float g,float b,float a){
    if(!ensureContextResources()) return;
    frameActive_=true; ++stats_.frame; stats_.submitted=stats_.visible=stats_.culled=stats_.batches=stats_.drawCalls=0; vertices_.clear(); currentTexture_=0;
    glViewport(0,0,static_cast<GLsizei>(camera_.width),static_cast<GLsizei>(camera_.height)); glClearColor(r,g,b,a); glClear(GL_COLOR_BUFFER_BIT); glUseProgram(program_); glEnable(GL_BLEND); glBlendFunc(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA);
}

bool Renderer2D::visible(const Sprite& s) const{
    const float halfW=std::abs(s.width)*.5f, halfH=std::abs(s.height)*.5f; const float viewW=camera_.width/std::max(.0001f,camera_.zoom), viewH=camera_.height/std::max(.0001f,camera_.zoom);
    return std::abs(s.x-camera_.position.x)<=viewW*.5f+halfW && std::abs(s.y-camera_.position.y)<=viewH*.5f+halfH;
}

void Renderer2D::buildVertices(const Sprite& s){
    const float c=std::cos(s.rotation), si=std::sin(s.rotation); const float left=-s.width*s.pivotX,right=s.width*(1.f-s.pivotX),top=-s.height*s.pivotY,bottom=s.height*(1.f-s.pivotY);
    const float px[4]={left,right,right,left}, py[4]={top,top,bottom,bottom}; float u0=s.u0,u1=s.u1,v0=s.v0,v1=s.v1; if(s.texture==0){};
    const float ux[4]={u0,u1,u1,u0}, uy[4]={v0,v0,v1,v1}; const int idx[6]={0,1,2,0,2,3};
    for(int k:idx){ const float x=px[k]*c-py[k]*si+s.x, y=px[k]*si+py[k]*c+s.y; vertices_.push_back({x,y,ux[k],uy[k],s.color.r,s.color.g,s.color.b,s.color.a}); }
}

void Renderer2D::submit(const Sprite& s){
    if(!frameActive_) return; ++stats_.submitted; if(!visible(s)){++stats_.culled;return;} ++stats_.visible;
    if(!vertices_.empty() && currentTexture_!=s.texture) flush(); if(vertices_.size()/6u>=maxSprites_) flush(); currentTexture_=s.texture; buildVertices(s);
}

void Renderer2D::flush(){
    if(vertices_.empty()||!ensureContextResources()) return;
    glUseProgram(program_); glBindBuffer(GL_ARRAY_BUFFER,vbo_); glBufferData(GL_ARRAY_BUFFER,static_cast<GLsizeiptr>(vertices_.size()*sizeof(Vertex)),vertices_.data(),GL_DYNAMIC_DRAW);
    glEnableVertexAttribArray(static_cast<GLuint>(positionLoc_)); glEnableVertexAttribArray(static_cast<GLuint>(uvLoc_)); glEnableVertexAttribArray(static_cast<GLuint>(colorLoc_));
    glVertexAttribPointer(static_cast<GLuint>(positionLoc_),2,GL_FLOAT,GL_FALSE,sizeof(Vertex),reinterpret_cast<const void*>(offsetof(Vertex,x)));
    glVertexAttribPointer(static_cast<GLuint>(uvLoc_),2,GL_FLOAT,GL_FALSE,sizeof(Vertex),reinterpret_cast<const void*>(offsetof(Vertex,u)));
    glVertexAttribPointer(static_cast<GLuint>(colorLoc_),4,GL_FLOAT,GL_FALSE,sizeof(Vertex),reinterpret_cast<const void*>(offsetof(Vertex,r)));
    const float z=std::max(.0001f,camera_.zoom), l=camera_.position.x-camera_.width/(2.f*z), r=camera_.position.x+camera_.width/(2.f*z), t=camera_.position.y-camera_.height/(2.f*z), b=camera_.position.y+camera_.height/(2.f*z);
    const float p[16]={2.f/(r-l),0,0,0,0,2.f/(t-b),0,0,0,0,-1,0,-(r+l)/(r-l),-(t+b)/(t-b),0,1}; glUniformMatrix4fv(projectionLoc_,1,GL_FALSE,p); glActiveTexture(GL_TEXTURE0); glBindTexture(GL_TEXTURE_2D,currentTexture_); glUniform1i(textureLoc_,0);
    glDrawArrays(GL_TRIANGLES,0,static_cast<GLsizei>(vertices_.size()/8u)); ++stats_.batches; ++stats_.drawCalls; vertices_.clear();
}

void Renderer2D::endFrame(){ if(!frameActive_) return; flush(); frameActive_=false; }

std::uint32_t Renderer2D::createTextureRGBA(int width,int height,const std::uint8_t* pixels,bool linear){
    if(width<=0||height<=0||!pixels) return 0; GLuint id=0; glGenTextures(1,&id); if(!id)return 0; glBindTexture(GL_TEXTURE_2D,id); glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,linear?GL_LINEAR:GL_NEAREST); glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,linear?GL_LINEAR:GL_NEAREST); glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE); glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE); glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA,width,height,0,GL_RGBA,GL_UNSIGNED_BYTE,pixels); return id;
}
void Renderer2D::destroyTexture(std::uint32_t texture){ if(texture){ GLuint id=texture; glDeleteTextures(1,&id); } }
void Renderer2D::clearTextures(){}
void Renderer2D::destroyGlResources(){ if(vbo_){GLuint id=vbo_;glDeleteBuffers(1,&id);vbo_=0;} if(program_){GLuint id=program_;glDeleteProgram(id);program_=0;} }

} // namespace nova::render
