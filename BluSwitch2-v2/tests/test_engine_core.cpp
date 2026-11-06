// SPDX-License-Identifier: GPL-3.0-or-later
#include "../app/src/main/cpp/engine_core.h"
#include <cassert>
#include <iostream>
#include <string>

using bluswitch2::A64Cpu;
using bluswitch2::GuestMemory;

static uint32_t movz(unsigned rd, unsigned imm, unsigned hw=0) {
    return 0xD2800000u | (hw << 21) | (imm << 5) | rd;
}
static uint32_t movk(unsigned rd, unsigned imm, unsigned hw=0) {
    return 0xF2800000u | (hw << 21) | (imm << 5) | rd;
}
static uint32_t addImm(unsigned rd, unsigned rn, unsigned imm) {
    return 0x91000000u | (imm << 10) | (rn << 5) | rd;
}
static uint32_t subImm(unsigned rd, unsigned rn, unsigned imm) {
    return 0xD1000000u | (imm << 10) | (rn << 5) | rd;
}
static uint32_t storeX(unsigned rt, unsigned rn, unsigned offsetBytes) {
    assert(offsetBytes % 8 == 0);
    return 0xF9000000u | ((offsetBytes/8) << 10) | (rn << 5) | rt;
}
static uint32_t loadX(unsigned rt, unsigned rn, unsigned offsetBytes) {
    assert(offsetBytes % 8 == 0);
    return 0xF9400000u | ((offsetBytes/8) << 10) | (rn << 5) | rt;
}

int main() {
    assert(bluswitch2::selfTest().rfind("PASS:", 0) == 0);
    GuestMemory memory(0x1000, 0x2000);
    const uint32_t program[] = {
        movz(0,35),             // X0 = 35
        addImm(0,0,7),          // X0 = 42
        movz(1,0x1000),         // X1 = guest base
        storeX(0,1,0x100),      // store 42 to guest memory
        loadX(2,1,0x100),       // X2 = 42
        subImm(3,2,1),          // X3 = 41
        0xB5000043u,           // CBNZ X3,+8, skip next instruction
        movz(0,99),            // skipped
        movk(0,0x12,1),        // X0 = 0x12002A
        0xD65F03C0u            // RET X30 to sentinel 0
    };
    for (unsigned i=0; i<sizeof(program)/sizeof(program[0]); ++i) {
        assert(memory.store32(0x1000 + i*4, program[i]));
    }
    A64Cpu cpu(memory);
    cpu.reset(0x1000);
    assert(cpu.run(32));
    assert(cpu.instructions()==9);
    assert(cpu.registerValue(0)==0x12002Au);
    assert(cpu.registerValue(2)==42);
    assert(cpu.registerValue(3)==41);
    uint64_t readback=0;
    assert(memory.load64(0x1100,readback) && readback==42);
    assert(!memory.store64(0x3000,7));
    assert(!memory.load64(0x1101,readback));
    assert(!memory.store32(0x0,1));

    // Unconditional B must skip the following MOVZ.
    GuestMemory branchMem(0x2000,0x100);
    assert(branchMem.store32(0x2000,0x14000002u)); // B +8
    assert(branchMem.store32(0x2004,movz(0,99)));
    assert(branchMem.store32(0x2008,movz(0,7)));
    assert(branchMem.store32(0x200C,0xD65F03C0u));
    A64Cpu branchCpu(branchMem);
    branchCpu.reset(0x2000);
    assert(branchCpu.run(8) && branchCpu.registerValue(0)==7);

    // Unsupported instructions must fail, not be ignored.
    GuestMemory invalid(0x4000,0x100);
    assert(invalid.store32(0x4000,0xFFFFFFFFu));
    A64Cpu bad(invalid);
    bad.reset(0x4000);
    assert(!bad.run(3));
    assert(bad.failure().find("unimplemented")!=std::string::npos);
    std::cout << "PASS: CPU decoder, guest memory, branches, failure handling" << std::endl;
}
