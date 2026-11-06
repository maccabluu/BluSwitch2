// SPDX-License-Identifier: GPL-3.0-or-later
#pragma once
#include <array>
#include <cstddef>
#include <cstdint>
#include <string>
#include <vector>

namespace bluswitch2 {

// A deliberately small guest address window. This does not allocate 12 GB or
// represent the full Switch 2 memory map. Guest pointers are never host pointers.
class GuestMemory {
public:
    explicit GuestMemory(uint64_t base, std::size_t size);
    bool load32(uint64_t address, uint32_t& output) const;
    bool store32(uint64_t address, uint32_t value);
    bool load64(uint64_t address, uint64_t& output) const;
    bool store64(uint64_t address, uint64_t value);
    bool contains(uint64_t address, uint64_t count) const;
    bool storeBytes(uint64_t address, const uint8_t* data, std::size_t count);
    bool zeroBytes(uint64_t address, std::size_t count);
private:
    bool valid(uint64_t address, std::size_t count) const;
    const uint64_t base_;
    std::vector<uint8_t> bytes_;
};

// A proof-of-concept AArch64 instruction interpreter, not a game-ready CPU.
// Prototype: MOVZ/MOVK/MOVN, ADD/SUB, LDR/STR64, B/BL, CBZ/CBNZ, NOP, RET.
class A64Cpu {
public:
    explicit A64Cpu(GuestMemory& memory);
    void reset(uint64_t start);
    bool run(unsigned max_steps);
    uint64_t registerValue(unsigned index) const;
    unsigned instructions() const;
    const std::string& failure() const;
private:
    bool step();
    GuestMemory& memory_;
    uint64_t sp_{0};
    std::array<uint64_t, 31> x_{};
    uint64_t pc_{0};
    bool halted_{false};
    unsigned steps_{0};
    std::string failure_;
};

std::string selfTest();
}
