import { Pool } from "pg";
import { env } from "../env.js";

const pool = env.databaseUrl ? new Pool({ connectionString: env.databaseUrl }) : null;

export type MemoryInput = {
  userId: string;
  kind: "profile" | "goal" | "project" | "preference" | "conversation" | "source" | "code";
  content: string;
  importance?: number;
};

const localFallback: MemoryInput[] = [];

export async function writeMemory(memory: MemoryInput) {
  const record = { ...memory, importance: memory.importance ?? 0.5 };

  if (!pool) {
    localFallback.unshift(record);
    return { id: `local-${Date.now()}`, local: true };
  }

  const result = await pool.query(
    `insert into memories (user_id, kind, content, importance)
     values ($1, $2, $3, $4)
     returning id`,
    [record.userId, record.kind, record.content, record.importance]
  );

  return { id: result.rows[0].id, local: false };
}

export async function readMemory(userId: string, limit = 20) {
  if (!pool) {
    return localFallback.filter((m) => m.userId === userId).slice(0, limit);
  }

  const result = await pool.query(
    `select id, kind, content, importance, created_at
     from memories
     where user_id = $1
     order by importance desc, created_at desc
     limit $2`,
    [userId, limit]
  );

  return result.rows;
}
