"""One-time source-to-schema extraction for review; never executes SQL."""
from pathlib import Path
import re

import argparse
parser = argparse.ArgumentParser()
parser.add_argument("--source-root", type=Path, default=Path(__file__).resolve().parents[3] / "references/matrix/matrix-backend/db")
ROOT = parser.parse_args().source_root
sources = [
    "0matrix_boot.sql", "9matrix_ai.sql", "14matrix_capsule.sql",
    "15matrix_assistant.sql", "21matrix_skill_runtime.sql", "58matrix_managed_skill.sql",
]
def statements(content):
    start = 0
    quote = None
    escaped = False
    for i, char in enumerate(content):
        if escaped:
            escaped = False
            continue
        if quote:
            if char == "\\": escaped = True
            elif char == quote: quote = None
        elif char in ("'", '"', '`'):
            quote = char
        elif char == ';':
            yield content[start:i + 1]
            start = i + 1

seen = set()
blocks = []
for source in sources:
    content = (ROOT / source).read_text(errors="replace")
    for statement in statements(content):
        match = re.search(r"CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?(?:`([A-Za-z0-9_]+)`|([A-Za-z0-9_]+))\s*\([\s\S]*\)\s*ENGINE\s*=\s*[\s\S]*;", statement, re.I)
        if not match: continue
        name = match.group(1) or match.group(2)
        if name.lower() in seen:
            continue
        seen.add(name.lower())
        blocks.append(f"-- Source: {source}, table: {name}\n" + match.group(0))
# Parent tables must exist before foreign key children. Fail if a cycle is introduced.
pending = list(blocks)
blocks = []
created = set()
while pending:
    ready = [block for block in pending if set(re.findall(r"REFERENCES\s+`?(\w+)`?", block, re.I)) <= created]
    if not ready: raise SystemExit("Cyclic or missing foreign key dependency")
    for block in ready:
        blocks.append(block)
        created.add(re.search(r"table: (\w+)", block).group(1))
        pending.remove(block)
required = {"app_user", "app_role", "app_user_role", "sys_tenant", "sys_oauth_client_details",
            "ai_capsule", "ai_capsule_revision", "ai_assistant_session", "ai_skill_run", "app_article"}
if not required <= seen:
    raise SystemExit(f"Missing P0 prerequisites: {sorted(required - seen)}")
output = """-- 此间 B00 独立空库结构提取稿。仅含参考表结构，不含示例账号或旧环境配置。\n-- 需在一次性 MySQL 8 测试库按 B00 -> B01 演练后才作为正式迁移使用。\n-- 来源锁定提交 3154567a；源文件及抽取脚本可审查。\nSET NAMES utf8mb4;\nSET time_zone = '+00:00';\n\n""" + "\n\n".join(blocks) + "\n\n" + """-- 原框架 44matrix_assistant_session_history.sql 补充的索引前置列。\nALTER TABLE ai_assistant_session ADD COLUMN last_message_at datetime(6) DEFAULT NULL;
-- Required by the locked SysTenant mapper; no inherited portal values.
ALTER TABLE sys_tenant ADD COLUMN portal_domain varchar(255) NULL;\n"""
(Path(__file__).parent / "B00_schema_review.sql").write_text(output)
print(f"extracted {len(blocks)} distinct CREATE TABLE statements")
