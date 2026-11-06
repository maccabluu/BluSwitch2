// SPDX-License-Identifier: GPL-3.0-or-later
#include "../app/src/main/cpp/elf_loader.h"
#include <cassert>
#include <iostream>
using namespace bluswitch2;
static void set(std::vector<uint8_t>& d,unsigned p,uint64_t v,unsigned n) {
    for(unsigned i=0;i<n;++i)d[p+i]=static_cast<uint8_t>(v>>(8*i));
}
static std::vector<uint8_t> makeElf() {
    std::vector<uint8_t> a(512,0);
    a[0]=0x7f;a[1]='E';a[2]='L';a[3]='F';a[4]=2;a[5]=1;a[6]=1;
    set(a,16,2,2);set(a,18,183,2);set(a,20,1,4);
    set(a,24,0x401000,8);set(a,32,64,8);
    set(a,52,64,2);set(a,54,56,2);set(a,56,1,2);
    set(a,64,1,4);set(a,68,5,4);
    set(a,72,0x100,8);set(a,80,0x401000,8);
    set(a,96,12,8);set(a,104,16,8);set(a,112,0x100,8);
    set(a,0x100,0xD2800460u,4);
    set(a,0x104,0x91001C00u,4);
    set(a,0x108,0xD65F03C0u,4);
    return a;
}
int main() {
    assert(elfLoaderSelfTest().rfind("PASS:",0)==0);
    GuestMemory mem(0x400000,0x20000);
    auto img=makeElf();
    const auto loaded=loadTestElf(img,mem);
    assert(loaded.ok&&loaded.entry==0x401000);
    uint32_t bss=1;assert(mem.load32(0x40100c,bss)&&bss==0);
    auto fails=[&](const std::vector<uint8_t>& invalid) {
        assert(!loadTestElf(invalid,mem).ok);
    };
    auto bad=img;bad.resize(30);fails(bad);
    bad=img;bad[4]=1;fails(bad);
    bad=img;set(bad,18,62,2);fails(bad);
    bad=img;set(bad,16,3,2);fails(bad);
    bad=img;set(bad,32,UINT64_MAX,8);fails(bad);
    bad=img;set(bad,56,0,2);fails(bad);
    bad=img;set(bad,64,3,4);fails(bad);
    bad=img;set(bad,96,0xffff,8);fails(bad);
    bad=img;set(bad,80,0xa00000,8);fails(bad);
    bad=img;set(bad,68,4,4);fails(bad);
    bad=img;set(bad,24,0x40100c,8);fails(bad);
    bad=img;set(bad,112,3,8);fails(bad);
    bad=img;set(bad,104,8,8);fails(bad);
    // PT_LOAD segment overlap must be rejected before modifying memory.
    bad=img;set(bad,56,2,2);set(bad,120,1,4);set(bad,124,4,4);
    set(bad,128,0x110,8);set(bad,136,0x401008,8);
    set(bad,152,4,8);set(bad,160,4,8);set(bad,168,1,8);
    fails(bad);
    assert(!mem.storeBytes(0x420000,reinterpret_cast<const uint8_t*>("hi"),2));
    std::cout<<"PASS: ELF fixture, memory boundaries, invalid ELF rejection\n";
}
