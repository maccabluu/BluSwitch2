// Screenshot previews of the in-repository BluSwitch UI, not a game-running Android capture.
// SPDX-License-Identifier: GPL-3.0-or-later
import { chromium } from 'playwright';
import { mkdir } from 'node:fs/promises';
import { resolve } from 'node:path';
import { pathToFileURL } from 'node:url';

const browser = await chromium.launch({ headless: true });
try {
  const page = await browser.newPage({
    viewport: { width: 1280, height: 720 },
    deviceScaleFactor: 1,
    reducedMotion: 'reduce'
  });
  await page.goto(pathToFileURL(resolve('bluswitch-ui/index.html')).href, {
    waitUntil: 'load'
  });
  await page.waitForFunction(() => typeof window.BluSwitch === 'object');
  await page.evaluate(() => {
    window.BluSwitch.setGames('[]');
    document.getElementById('clock').textContent = '12:34';
  });
  await mkdir('docs/screenshots', { recursive: true });
  await page.screenshot({ path: 'docs/screenshots/library.png', animations: 'disabled' });
  await page.evaluate(() => {
    window.BluSwitch.open('collections');
    document.getElementById('clock').textContent = '12:34';
  });
  await page.screenshot({ path: 'docs/screenshots/favourites.png', animations: 'disabled' });
  console.log('Created BluSwitch 2 Library and Favourites UI screenshot previews');
} finally {
  await browser.close();
}