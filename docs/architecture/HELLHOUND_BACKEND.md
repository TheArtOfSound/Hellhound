# Hellhound Backend Architecture

Hellhound is not a chat skin. It is an agent runtime.

## Layers

1. Cloudflare Worker
   - Public UI
   - Edge API gateway
   - Provider proxy
   - Light web context

2. Agent backend
   - Agent loop
   - LOLM-style decision packet
   - Memory routing
   - Search/news/read tools
   - Provider routing
   - Receipt creation

3. Sandbox backend
   - Command execution
   - npm install/test/build
   - Git tasks
   - Browser tasks later
   - Timeouts and output caps

4. Memory
   - Postgres
   - pgvector later
   - Projects/goals/preferences/conversation memory

5. NFET receipts
   - Action hash chain
   - Memory write receipts
   - Tool run receipts
   - Code execution receipts

6. Scheduler
   - Cloudflare Cron for light nudges
   - Backend queue for heavier planning

7. LOLM controller
   - Surface need
   - Tool need
   - Memory need
   - Receipt need
   - Regime/mode detection
   - Manifestation decision

## Agent loop

Input
→ memory load
→ decision packet
→ tool calls
→ provider answer
→ memory write
→ receipt
→ schedule nudge

## Goal

Hellhound should act like a project-aware operator:
- search
- read
- plan
- code
- run
- remember
- nudge
- verify
- create receipts
