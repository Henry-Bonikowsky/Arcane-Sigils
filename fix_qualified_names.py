#!/usr/bin/env python3
"""Fix fully qualified class names in Java files"""

import re
from pathlib import Path

PROJECT_ROOT = Path(__file__).parent
SRC_DIR = PROJECT_ROOT / "src" / "main" / "java"

def fix_file(file_path: Path) -> int:
    """Fix fully qualified class names in a file. Returns number of changes."""
    try:
        content = file_path.read_text(encoding='utf-8')
        original = content

        # Replace fully qualified class names
        # Pattern: com.zenax.armorsets. followed by word characters
        content = re.sub(
            r'\bcom\.zenax\.armorsets\.',
            'com.miracle.arcanesigils.',
            content
        )

        if content != original:
            file_path.write_text(content, encoding='utf-8')
            changes = content.count('com.miracle.arcanesigils') - original.count('com.miracle.arcanesigils')
            return changes
        return 0

    except Exception as e:
        print(f"Error fixing {file_path}: {e}")
        return 0

def main():
    total_files = 0
    total_changes = 0

    for java_file in SRC_DIR.rglob("*.java"):
        changes = fix_file(java_file)
        if changes > 0:
            total_files += 1
            total_changes += changes
            print(f"[OK] {java_file.relative_to(PROJECT_ROOT)}: {changes} changes")

    print(f"\n[OK] Fixed {total_changes} qualified names in {total_files} files")

if __name__ == "__main__":
    main()
