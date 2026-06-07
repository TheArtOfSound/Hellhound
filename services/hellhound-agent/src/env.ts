export const env = {
  port: Number(process.env.PORT || 8788),
  databaseUrl: process.env.DATABASE_URL || "",
  answerKey: process.env.HELLHOUND_ANSWER_KEY || "",
  searchKey: process.env.HELLHOUND_SEARCH_KEY || "",
  sandboxUrl: process.env.HELLHOUND_SANDBOX_URL || "http://127.0.0.1:8790",
  defaultAnswerProvider: process.env.HELLHOUND_DEFAULT_ANSWER_PROVIDER || "openrouter",
  defaultSearchProvider: process.env.HELLHOUND_DEFAULT_SEARCH_PROVIDER || "brave"
};
