#include "../include/LuaVM.h"
#include "../include/Physics.h"
#include <android/log.h>

#if __has_include(<lua.hpp>)
#include <lua.hpp>
#define FUTURE2D_HAS_LUA 1
#elif __has_include(<lua.h>)
#include <lua.h>
#include <lauxlib.h>
#include <lualib.h>
#define FUTURE2D_HAS_LUA 1
#else
#define FUTURE2D_HAS_LUA 0
#endif

namespace future2d {

struct LuaVM::Impl {
#if FUTURE2D_HAS_LUA
    lua_State* L = nullptr;
    PhysicsWorld* physics = nullptr;
#endif
};

LuaVM::LuaVM(): impl_(new Impl()) {}

LuaVM::~LuaVM() {
#if FUTURE2D_HAS_LUA
    if (impl_->L) lua_close(impl_->L);
#endif
}

static int l_createBox(lua_State* L) {
#if FUTURE2D_HAS_LUA
    LuaVM::Impl* impl = (LuaVM::Impl*)lua_touserdata(L, lua_upvalueindex(1));
    if (!impl || !impl->physics) { lua_pushinteger(L, -1); return 1; }
    float x = (float)luaL_checknumber(L, 1);
    float y = (float)luaL_checknumber(L, 2);
    float hx = (float)luaL_checknumber(L, 3);
    float hy = (float)luaL_checknumber(L, 4);
    int dyn = lua_toboolean(L, 5);
    int id = impl->physics->createBox(x, y, hx, hy, dyn != 0);
    lua_pushinteger(L, id);
    return 1;
#else
    (void)L; return 0;
#endif
}

bool LuaVM::init(PhysicsWorld* physics) {
#if FUTURE2D_HAS_LUA
    impl_->L = luaL_newstate();
    if (!impl_->L) return false;
    luaL_openlibs(impl_->L);
    impl_->physics = physics;
    // register createBox
    lua_pushlightuserdata(impl_->L, impl_.get());
    lua_pushcclosure(impl_->L, l_createBox, 1);
    lua_setglobal(impl_->L, "createBox");
    return true;
#else
    (void)physics; return false;
#endif
}

bool LuaVM::doString(const std::string& code, const std::string& name) {
#if FUTURE2D_HAS_LUA
    if (!impl_->L) return false;
    int r = luaL_loadbuffer(impl_->L, code.c_str(), code.size(), name.c_str()) || lua_pcall(impl_->L, 0, LUA_MULTRET, 0);
    if (r) {
        const char* msg = lua_tostring(impl_->L, -1);
        __android_log_print(ANDROID_LOG_ERROR, "Future2D::LuaVM", "Lua error: %s", msg ? msg : "<error>");
        lua_pop(impl_->L, 1);
        return false;
    }
    return true;
#else
    (void)code; (void)name; return false;
#endif
}

void LuaVM::callOnUpdate(float dt) {
#if FUTURE2D_HAS_LUA
    if (!impl_->L) return;
    lua_getglobal(impl_->L, "onUpdate");
    if (lua_isfunction(impl_->L, -1)) {
        lua_pushnumber(impl_->L, dt);
        if (lua_pcall(impl_->L, 1, 0, 0) != 0) {
            const char* msg = lua_tostring(impl_->L, -1);
            __android_log_print(ANDROID_LOG_ERROR, "Future2D::LuaVM", "onUpdate error: %s", msg ? msg : "<error>");
            lua_pop(impl_->L, 1);
        }
    } else {
        lua_pop(impl_->L, 1);
    }
#else
    (void)dt;
#endif
}

} // namespace future2d
