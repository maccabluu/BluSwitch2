// SPDX-License-Identifier: GPL-3.0-or-later
#include "../app/src/main/cpp/game_demo.h"
#include <cassert>
#include <iostream>
using bluswitch2::stepMiniGame;
int main() {
    auto s=stepMiniGame(0,0,0,0,0);
    assert(s.error.empty()&&s.x==0&&s.y==0&&s.moves==0&&s.won==0&&s.guestInstructions>0);
    s=stepMiniGame(s.x,s.y,s.moves,s.won,1);
    assert(s.error.empty()&&s.y==0&&s.moves==0); // upper edge
    s=stepMiniGame(s.x,s.y,s.moves,s.won,4);
    assert(s.error.empty()&&s.x==1&&s.moves==1);
    s=stepMiniGame(s.x,s.y,s.moves,s.won,4);
    assert(s.error.empty()&&s.x==2&&s.moves==2);
    s=stepMiniGame(s.x,s.y,s.moves,s.won,4);
    assert(s.error.empty()&&s.x==2&&s.moves==2); // right edge
    s=stepMiniGame(s.x,s.y,s.moves,s.won,2);
    assert(s.error.empty()&&s.y==1&&s.moves==3);
    s=stepMiniGame(s.x,s.y,s.moves,s.won,2);
    assert(s.error.empty()&&s.y==2&&s.moves==4&&s.won==1);
    s=stepMiniGame(s.x,s.y,s.moves,s.won,3);
    assert(s.error.empty()&&s.x==2&&s.y==2&&s.moves==4&&s.won==1); // win freezes
    auto bad=stepMiniGame(-1,0,0,0,4);
    assert(!bad.error.empty());
    bad=stepMiniGame(0,0,0,0,9);
    assert(!bad.error.empty());
    bad=stepMiniGame(0,0,-1,0,0);
    assert(!bad.error.empty());
    s=stepMiniGame(0,0,0,0,2);
    s=stepMiniGame(s.x,s.y,s.moves,s.won,3);
    assert(s.error.empty()&&s.x==0&&s.y==1&&s.moves==1);
    std::cout<<"PASS: guest AArch64 game movement, boundaries, victory, reset and invalid input\n";
}
