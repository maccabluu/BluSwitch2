// SPDX-License-Identifier: GPL-3.0-or-later
#include "elf_loader.h"
#include <cstddef>
#include <cstdint>

namespace bluswitch2 {
namespace {
constexpr std::size_t maxBytes=4*1024*1024;
bool range(std::size_t size, uint64_t offset, uint64_t count) {
    return offset<=size && count<=size-offset;
}
uint64_t le(const std::vector<uint8_t>& d,std::size_t p,unsigned n) {
    uint64_t v=0;
    for(unsigned i=0;i<n;++i) v|=uint64_t(d[p+i])<<(8*i);
    return v;
}
void put(std::vector<uint8_t>& d,std::size_t p,uint64_t v,unsigned n) {
    for(unsigned i=0;i<n;++i) d[p+i]=static_cast<uint8_t>(v>>(8*i));
}
struct Segment {uint64_t addr,offset,filesz,memsz;unsigned flags;};
LoadResult reject(const char* reason){return {false,0,reason};}
}

LoadResult loadTestElf(const std::vector<uint8_t>& img,GuestMemory& memory) {
    if(img.size()<64 || img.size()>maxBytes) return reject("invalid ELF length");
    if(img[0]!=0x7f || img[1]!='E' || img[2]!='L' || img[3]!='F')
        return reject("not an ELF file");
    if(img[4]!=2 || img[5]!=1 || img[6]!=1)
        return reject("requires little-endian ELF64");
    if(le(img,16,2)!=2 || le(img,18,2)!=183 || le(img,20,4)!=1)
        return reject("requires static AArch64 ET_EXEC");
    const uint64_t entry=le(img,24,8);
    const uint64_t offset=le(img,32,8);
    const uint64_t n=le(img,56,2);
    if(le(img,52,2)!=64 || le(img,54,2)!=56 || n==0 || n>32 ||
       !range(img.size(),offset,n*56))
        return reject("invalid program header table");
    std::vector<Segment> segments;
    bool executable=false;
    for(uint64_t i=0;i<n;++i) {
        const auto p=static_cast<std::size_t>(offset+i*56);
        const uint64_t type=le(img,p,4);
        if(type==0)continue;  // PT_NULL
        if(type!=1)return reject("unsupported program header");
        Segment s{le(img,p+16,8),le(img,p+8,8),le(img,p+32,8),
                  le(img,p+40,8),static_cast<unsigned>(le(img,p+4,4))};
        const uint64_t align=le(img,p+48,8);
        if(s.memsz==0 || s.filesz>s.memsz || !range(img.size(),s.offset,s.filesz) ||
           !memory.contains(s.addr,s.memsz))
            return reject("invalid segment range");
        if(align>1 && ((align&(align-1))!=0 ||
           s.addr%align!=s.offset%align))
            return reject("invalid segment alignment");
        if((s.flags & ~7u)!=0)return reject("invalid segment flags");
        if(entry>=s.addr && entry-s.addr<s.filesz &&
           s.filesz-(entry-s.addr)>=4 && (s.flags&1u)!=0 && entry%4==0)
            executable=true;
        for(const Segment& prev:segments) {
            if(s.addr<prev.addr+prev.memsz && prev.addr<s.addr+s.memsz)
                return reject("overlapping segments");
        }
        segments.push_back(s);
    }
    if(segments.empty())return reject("no loadable segments");
    if(!executable)return reject("entry not in executable code");
    for(const Segment& s:segments) {
        if(!memory.zeroBytes(s.addr,static_cast<std::size_t>(s.memsz)) ||
           !memory.storeBytes(s.addr,img.data()+s.offset,
                             static_cast<std::size_t>(s.filesz)))
            return reject("memory mapping failed");
    }
    return {true,entry,""};
}

std::string elfLoaderSelfTest() {
    constexpr uint64_t base=0x400000,entry=0x401000;
    std::vector<uint8_t> img(0x200,0);
    img[0]=0x7f;img[1]='E';img[2]='L';img[3]='F';
    img[4]=2;img[5]=1;img[6]=1;
    put(img,16,2,2);put(img,18,183,2);put(img,20,1,4);
    put(img,24,entry,8);put(img,32,64,8);
    put(img,52,64,2);put(img,54,56,2);put(img,56,1,2);
    put(img,64,1,4);put(img,68,5,4);
    put(img,72,0x100,8);put(img,80,entry,8);
    put(img,96,12,8);put(img,104,16,8);put(img,112,0x100,8);
    // Original synthetic ARM64 instructions.
    put(img,0x100,0xD2800460u,4); // MOVZ X0,#35
    put(img,0x104,0x91001C00u,4); // ADD X0,X0,#7
    put(img,0x108,0xD65F03C0u,4); // RET X30 to test sentinel
    GuestMemory mem(base,0x20000);
    const LoadResult load=loadTestElf(img,mem);
    if(!load.ok)return "FAIL: ELF: "+load.error;
    A64Cpu cpu(mem);
    cpu.reset(load.entry);
    if(!cpu.run(16))return "FAIL: CPU: "+cpu.failure();
    if(cpu.registerValue(0)!=42 || cpu.instructions()!=3)
        return "FAIL: unexpected synthetic ARM64 result";
    uint32_t bss=0xffffffff;
    if(!mem.load32(entry+12,bss)||bss!=0)
        return "FAIL: ELF BSS was not cleared";
    return "PASS: static AArch64 ELF test loaded and interpreted, X0=42. "
           "Switch 2 game support is not implemented.";
}
}
