import { chromium } from "playwright";

export type BrowserActionResult = {
  ok: boolean;
  mode: "disabled" | "search" | "open";
  url?: string;
  title?: string;
  visibleText?: string;
  error?: string;
};

function pickUrl(text: string) {
  const match = text.match(/https?:\/\/[^\s)"']+/i);
  return match?.[0] || "";
}

function makeSearchUrl(query: string) {
  const clean = query.replace(/\s+/g, " ").trim().slice(0, 240);
  return `https://duckduckgo.com/?q=${encodeURIComponent(clean)}`;
}

export async function runBrowserAction(objective: string): Promise<BrowserActionResult> {
  const enabled = process.env.HELLHOUND_BROWSER_AUTONOMY === "true";
  if (!enabled) {
    return { ok: true, mode: "disabled", visibleText: "Browser autonomy is disabled. Set HELLHOUND_BROWSER_AUTONOMY=true to let Hellhound use Playwright." };
  }

  const explicitUrl = pickUrl(objective);
  const targetUrl = explicitUrl || makeSearchUrl(objective);
  const headless = process.env.HELLHOUND_BROWSER_HEADLESS !== "false";

  let browser: Awaited<ReturnType<typeof chromium.launch>> | null = null;

  try {
    browser = await chromium.launch({ headless });
    const page = await browser.newPage({ viewport: { width: 1365, height: 900 } });
    await page.goto(targetUrl, { waitUntil: "domcontentloaded", timeout: 30000 });
    await page.waitForTimeout(1000);

    const title = await page.title().catch(() => "");
    const url = page.url();
    const visibleText = await page.locator("body").innerText({ timeout: 8000 }).catch(() => "");

    return {
      ok: true,
      mode: explicitUrl ? "open" : "search",
      url,
      title,
      visibleText: visibleText.replace(/\n{3,}/g, "\n\n").slice(0, 5000)
    };
  } catch (error) {
    return {
      ok: false,
      mode: explicitUrl ? "open" : "search",
      url: targetUrl,
      error: error instanceof Error ? error.message : String(error)
    };
  } finally {
    await browser?.close().catch(() => undefined);
  }
}
