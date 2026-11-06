// SPDX-License-Identifier: GPL-3.0-or-later
#include "game_demo.h"
#include "engine_core.h"
#include <cstdint>
#include <map>
#include <string>
#include <vector>

namespace bluswitch2 {
namespace {
constexpr uint64_t kCode=0x10000, kData=0x12000;
constexpr uint32_t kRet=0xD65F03C0u;
uint32_t movz(unsigned rd,unsigned v,unsigned half=0) {
    return 0xD2800000u|(half<<21)|((v&0xffffu)<<5)|rd;
}
uint32_t movk(unsigned rd,unsigned v,unsigned half=0) {
    return 0xF2800000u|(half<<21)|((v&0xffffu)<<5)|rd;
}
uint32_t add(unsigned rd,unsigned rn,unsigned imm) {
    return 0x91000000u|((imm&0xfffu)<<10)|(rn<<5)|rd;
}
uint32_t sub(unsigned rd,unsigned rn,unsigned imm) {
    return 0xD1000000u|((imm&0xfffu)<<10)|(rn<<5)|rd;
}
uint32_t ldr(unsigned rd,unsigned rn,unsigned offset) {
    return 0xF9400000u|((offset/8)<<10)|(rn<<5)|rd;
}
uint32_t str(unsigned rd,unsigned rn,unsigned offset) {
    return 0xF9000000u|((offset/8)<<10)|(rn<<5)|rd;
}
class Assembler {
public:
    void emit(uint32_t op){ code_.push_back(op); }
    void label(const char* name){ labels_[name]=code_.size(); }
    void branch(const char* name){ fixups_.push_back({code_.size(),name,0,0});emit(0); }
    void cbz(unsigned reg,const char* name){ fixups_.push_back({code_.size(),name,1,reg});emit(0); }
    void cbnz(unsigned reg,const char* name){ fixups_.push_back({code_.size(),name,2,reg});emit(0); }
    bool resolve() {
        for(const auto& f:fixups_) {
            const auto label=labels_.find(f.name);
            if(label==labels_.end())return false;
            const int64_t d=static_cast<int64_t>(label->second)-static_cast<int64_t>(f.index);
            if(f.kind==0) {
                if(d<-(int64_t(1)<<25)||d>=(int64_t(1)<<25))return false;
                code_[f.index]=0x14000000u|(static_cast<uint32_t>(d)&0x3ffffffu);
            } else {
                if(d<-(int64_t(1)<<18)||d>=(int64_t(1)<<18))return false;
                code_[f.index]=(f.kind==1?0xB4000000u:0xB5000000u)
                    |((static_cast<uint32_t>(d)&0x7ffffu)<<5)|f.reg;
            }
        }
        return true;
    }
    const std::vector<uint32_t>& instructions()const{return code_;}
private:
    struct Fixup {std::size_t index;std::string name;int kind;unsigned reg;};
    std::vector<uint32_t> code_;
    std::map<std::string,std::size_t> labels_;
    std::vector<Fixup> fixups_;
};
Assembler createProgram() {
    Assembler a;
    a.emit(movz(1,0x2000));a.emit(movk(1,1,1)); // X1=0x12000
    a.emit(ldr(2,1,0));  // x
    a.emit(ldr(3,1,8));  // y
    a.emit(ldr(4,1,16)); // input
    a.emit(ldr(5,1,24)); // moves
    a.emit(ldr(6,1,32)); // won
    a.cbnz(6,"save");    // freeze after winning
    a.emit(sub(7,4,1));a.cbz(7,"up");
    a.emit(sub(7,4,2));a.cbz(7,"down");
    a.emit(sub(7,4,3));a.cbz(7,"left");
    a.emit(sub(7,4,4));a.cbz(7,"right");
    a.branch("save");

    a.label("up");
    a.cbz(3,"save");a.emit(sub(3,3,1));a.branch("moved");
    a.label("down");
    a.emit(sub(7,3,2));a.cbz(7,"save");
    a.emit(add(3,3,1));a.branch("moved");
    a.label("left");
    a.cbz(2,"save");a.emit(sub(2,2,1));a.branch("moved");
    a.label("right");
    a.emit(sub(7,2,2));a.cbz(7,"save");
    a.emit(add(2,2,1));

    a.label("moved");
    a.emit(add(5,5,1));
    a.emit(sub(7,2,2));a.cbnz(7,"save");
    a.emit(sub(7,3,2));a.cbnz(7,"save");
    a.emit(movz(6,1)); // collect treasure at x=2,y=2

    a.label("save");
    a.emit(str(2,1,0));a.emit(str(3,1,8));
    a.emit(str(5,1,24));a.emit(str(6,1,32));
    a.emit(kRet);
    return a;
}
}
MiniGameState stepMiniGame(int x,int y,int moves,int won,int direction) {
    MiniGameState result{x,y,moves,won,0,{}};
    if(x<0||x>2||y<0||y>2||moves<0||moves>100000||
       won<0||won>1||direction<0||direction>4) {
        result.error="invalid mini-game input";return result;
    }
    GuestMemory memory(kCode,0x3000);
    Assembler program=createProgram();
    if(!program.resolve()) {
        result.error="invalid guest code relocation";return result;
    }
    const auto& instructions=program.instructions();
    if(instructions.empty()||instructions.size()>128) {
        result.error="invalid guest code size";return result;
    }
    for(std::size_t i=0;i<instructions.size();++i) {
        if(!memory.store32(kCode+i*4,instructions[i])) {
            result.error="cannot map guest instructions";return result;
        }
    }
    if(!memory.store64(kData,x)||!memory.store64(kData+8,y)||
       !memory.store64(kData+16,direction)||!memory.store64(kData+24,moves)||
       !memory.store64(kData+32,won)) {
        result.error="cannot map guest state";return result;
    }
    A64Cpu cpu(memory);cpu.reset(kCode);
    if(!cpu.run(128)) {
        result.error="guest CPU: "+cpu.failure();return result;
    }
    uint64_t gx=0,gy=0,gm=0,gw=0;
    if(!memory.load64(kData,gx)||!memory.load64(kData+8,gy)||
       !memory.load64(kData+24,gm)||!memory.load64(kData+32,gw)||
       gx>2||gy>2||gm>100000||gw>1) {
        result.error="invalid guest result";return result;
    }
    result.x=static_cast<int>(gx);result.y=static_cast<int>(gy);
    result.moves=static_cast<int>(gm);result.won=static_cast<int>(gw);
    result.guestInstructions=cpu.instructions();
    return result;
}
}
