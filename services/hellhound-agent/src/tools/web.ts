import { env } from "../env.js";

export type SearchResult = {
  title: string;
  url: string;
  description: string;
  age?: string;
  source?: string;
};

export async function searchWeb(query: string): Promise<SearchResult[]> {
  if (!env.searchKey) {
    return searchFallback(query);
  }

  const url = new URL("https://api.search.brave.com/res/v1/web/search");
  url.searchParams.set("q", query);
  url.searchParams.set("count", "8");
  url.searchParams.set("freshness", "pw");

  const response = await fetch(url, {
    headers: {
      "Accept": "application/json",
      "X-Subscription-Token": env.searchKey
    }
  });

  const data: any = await response.json().catch(() => ({}));

  if (!response.ok) {
    throw new Error(data?.message || data?.error || `Search failed: ${response.status}`);
  }

  return (data?.web?.results || []).map((item: any) => ({
    title: item.title || "Untitled",
    url: item.url || "",
    description: item.description || "",
    age: item.age,
    source: item.profile?.name
  }));
}

export async function searchNews(query: string): Promise<SearchResult[]> {
  if (!env.searchKey) {
    return searchFallback(query);
  }

  const url = new URL("https://api.search.brave.com/res/v1/news/search");
  url.searchParams.set("q", query);
  url.searchParams.set("count", "8");
  url.searchParams.set("freshness", "pd");

  const response = await fetch(url, {
    headers: {
      "Accept": "application/json",
      "X-Subscription-Token": env.searchKey
    }
  });

  const data: any = await response.json().catch(() => ({}));

  if (!response.ok) {
    throw new Error(data?.message || data?.error || `News failed: ${response.status}`);
  }

  return (data?.results || []).map((item: any) => ({
    title: item.title || "Untitled",
    url: item.url || "",
    description: item.description || "",
    age: item.age,
    source: item.source
  }));
}

async function searchFallback(query: string): Promise<SearchResult[]> {
  const url = new URL("https://hn.algolia.com/api/v1/search");
  url.searchParams.set("query", query);
  url.searchParams.set("tags", "story");
  url.searchParams.set("hitsPerPage", "8");

  const response = await fetch(url);
  const data: any = await response.json().catch(() => ({}));

  return (data?.hits || []).map((hit: any) => ({
    title: hit.title || hit.story_title || "Untitled",
    url: hit.url || hit.story_url || "",
    description: `${hit.points ?? "?"} points · ${hit.num_comments ?? "?"} comments`,
    source: "Hacker News"
  }));
}

export async function readUrl(url: string) {
  const jina = `https://r.jina.ai/http://r.jina.ai/http://https://r.jina.ai/http://r.jina.ai/http://${url}`;
  const response = await fetch(jina).catch(() => null);

  if (!response || !response.ok) {
    return "";
  }

  return (await response.text()).slice(0, 12000);
}
