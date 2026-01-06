#!/usr/bin/env python3
"""
Package Refactor Script
Refactors com.zenax.armorsets → com.miracle.arcanesigils

Usage:
    python refactor_packages.py           # Dry run (shows what would change)
    python refactor_packages.py --apply   # Actually apply changes
"""

import os
import re
import shutil
from pathlib import Path
from typing import List, Tuple

# Configuration
OLD_PACKAGE = "com.zenax.armorsets"
NEW_PACKAGE = "com.miracle.arcanesigils"
OLD_GROUP = "com.zenax"
NEW_GROUP = "com.miracle"
OLD_ARTIFACT = "ArmorSets"
NEW_ARTIFACT = "ArcaneSigils"

# Paths
PROJECT_ROOT = Path(__file__).parent
SRC_DIR = PROJECT_ROOT / "src" / "main" / "java"
TEST_DIR = PROJECT_ROOT / "src" / "test" / "java"
RESOURCES_DIR = PROJECT_ROOT / "src" / "main" / "resources"
POM_FILE = PROJECT_ROOT / "pom.xml"
PLUGIN_YML = RESOURCES_DIR / "plugin.yml"

# Statistics
stats = {
    'files_processed': 0,
    'package_declarations': 0,
    'import_statements': 0,
    'hardcoded_strings': 0,
    'errors': []
}

def find_java_files(directory: Path) -> List[Path]:
    """Find all .java files recursively."""
    if not directory.exists():
        return []
    return list(directory.rglob("*.java"))

def update_java_file(file_path: Path, dry_run: bool = True) -> Tuple[int, int]:
    """
    Update package declarations and imports in a Java file.
    Returns: (package_changes, import_changes)
    """
    try:
        content = file_path.read_text(encoding='utf-8')
        original_content = content

        package_changes = 0
        import_changes = 0

        # Update package declaration
        package_pattern = r'^package\s+com\.zenax\.armorsets(\.[\w.]*)?;'
        def replace_package(match):
            nonlocal package_changes
            package_changes += 1
            suffix = match.group(1) or ''
            return f'package com.miracle.arcanesigils{suffix};'

        content = re.sub(package_pattern, replace_package, content, flags=re.MULTILINE)

        # Update import statements
        import_pattern = r'import\s+com\.zenax\.armorsets\.([\w.]+);'
        def replace_import(match):
            nonlocal import_changes
            import_changes += 1
            rest = match.group(1)
            return f'import com.miracle.arcanesigils.{rest};'

        content = re.sub(import_pattern, replace_import, content)

        # Write back if changed and not dry run
        if content != original_content:
            if not dry_run:
                file_path.write_text(content, encoding='utf-8')
            return (package_changes, import_changes)

        return (0, 0)

    except Exception as e:
        stats['errors'].append(f"Error processing {file_path}: {e}")
        return (0, 0)

def update_plugin_yml(dry_run: bool = True) -> bool:
    """Update main class in plugin.yml."""
    if not PLUGIN_YML.exists():
        print(f"⚠️  plugin.yml not found at {PLUGIN_YML}")
        return False

    try:
        content = PLUGIN_YML.read_text(encoding='utf-8')
        original_content = content

        # Update main class
        content = re.sub(
            r'main:\s*com\.zenax\.armorsets\.ArmorSetsPlugin',
            'main: com.miracle.arcanesigils.ArmorSetsPlugin',
            content
        )

        if content != original_content:
            if not dry_run:
                PLUGIN_YML.write_text(content, encoding='utf-8')
            print(f"✓ Updated plugin.yml main class")
            return True
        else:
            print(f"⚠️  plugin.yml already up-to-date or no match found")
            return False

    except Exception as e:
        stats['errors'].append(f"Error updating plugin.yml: {e}")
        return False

def update_pom_xml(dry_run: bool = True) -> int:
    """Update groupId and artifactId in pom.xml."""
    if not POM_FILE.exists():
        print(f"⚠️  pom.xml not found at {POM_FILE}")
        return 0

    try:
        content = POM_FILE.read_text(encoding='utf-8')
        original_content = content
        changes = 0

        # Update groupId (only the first occurrence in the main project section)
        if '<groupId>com.zenax</groupId>' in content:
            content = content.replace(
                '<groupId>com.zenax</groupId>',
                '<groupId>com.miracle</groupId>',
                1  # Only replace first occurrence
            )
            changes += 1

        # Update artifactId
        if '<artifactId>ArmorSets</artifactId>' in content:
            content = content.replace(
                '<artifactId>ArmorSets</artifactId>',
                '<artifactId>ArcaneSigils</artifactId>',
                1  # Only replace first occurrence
            )
            changes += 1

        # Update name tag if it contains ArmorSets
        content = re.sub(
            r'<name>ArmorSets</name>',
            '<name>ArcaneSigils</name>',
            content
        )

        if content != original_content:
            if not dry_run:
                POM_FILE.write_text(content, encoding='utf-8')
            print(f"✓ Updated pom.xml (groupId, artifactId, name)")
            return changes
        else:
            print(f"⚠️  pom.xml already up-to-date")
            return 0

    except Exception as e:
        stats['errors'].append(f"Error updating pom.xml: {e}")
        return 0

def search_hardcoded_strings(directory: Path, dry_run: bool = True) -> List[Tuple[Path, int, str]]:
    """Search for hardcoded package references in strings."""
    results = []

    if not directory.exists():
        return results

    for java_file in directory.rglob("*.java"):
        try:
            lines = java_file.read_text(encoding='utf-8').splitlines()
            for i, line in enumerate(lines, 1):
                # Skip package and import lines (already handled)
                if line.strip().startswith('package ') or line.strip().startswith('import '):
                    continue

                # Look for string literals containing old package
                if '"com.zenax.armorsets' in line or "'com.zenax.armorsets" in line:
                    results.append((java_file, i, line.strip()))
                    stats['hardcoded_strings'] += 1

        except Exception as e:
            stats['errors'].append(f"Error searching {java_file}: {e}")

    return results

def move_directory_structure(dry_run: bool = True) -> bool:
    """Move src/main/java/com/zenax → src/main/java/com/miracle."""
    old_zenax_dir = SRC_DIR / "com" / "zenax"
    new_miracle_dir = SRC_DIR / "com" / "miracle"

    if not old_zenax_dir.exists():
        print(f"⚠️  Source directory not found: {old_zenax_dir}")
        return False

    # Create new directory structure
    new_arcanesigils_dir = new_miracle_dir / "arcanesigils"

    if dry_run:
        print(f"📁 Would move: {old_zenax_dir / 'armorsets'} → {new_arcanesigils_dir}")
        return True

    try:
        # Create parent directory
        new_miracle_dir.mkdir(parents=True, exist_ok=True)

        # Move armorsets to arcanesigils
        old_armorsets_dir = old_zenax_dir / "armorsets"
        if old_armorsets_dir.exists():
            shutil.move(str(old_armorsets_dir), str(new_arcanesigils_dir))
            print(f"✓ Moved directory structure")

            # Remove empty zenax directory
            if old_zenax_dir.exists() and not any(old_zenax_dir.iterdir()):
                old_zenax_dir.rmdir()
                print(f"✓ Removed empty directory: {old_zenax_dir}")

            return True
        else:
            print(f"⚠️  armorsets directory not found")
            return False

    except Exception as e:
        stats['errors'].append(f"Error moving directories: {e}")
        return False

def main():
    import sys

    # Check for --apply flag
    dry_run = '--apply' not in sys.argv

    if dry_run:
        print("=" * 60)
        print("DRY RUN MODE - No changes will be made")
        print("Run with --apply to actually apply changes")
        print("=" * 60)
        print()
    else:
        print("=" * 60)
        print("APPLYING CHANGES")
        print("=" * 60)
        print()

    # Step 1: Find all Java files
    print("📂 Finding Java files...")
    src_files = find_java_files(SRC_DIR)
    test_files = find_java_files(TEST_DIR)
    all_java_files = src_files + test_files

    print(f"   Found {len(src_files)} source files")
    if test_files:
        print(f"   Found {len(test_files)} test files")
    print()

    # Step 2: Update Java files
    print("🔧 Updating Java files...")
    for java_file in all_java_files:
        pkg_changes, imp_changes = update_java_file(java_file, dry_run)
        if pkg_changes > 0 or imp_changes > 0:
            stats['files_processed'] += 1
            stats['package_declarations'] += pkg_changes
            stats['import_statements'] += imp_changes

            if dry_run:
                print(f"   {java_file.relative_to(PROJECT_ROOT)}: {pkg_changes} pkg, {imp_changes} imports")

    print(f"✓ Processed {stats['files_processed']} files")
    print(f"   - Package declarations: {stats['package_declarations']}")
    print(f"   - Import statements: {stats['import_statements']}")
    print()

    # Step 3: Update plugin.yml
    print("📝 Updating plugin.yml...")
    update_plugin_yml(dry_run)
    print()

    # Step 4: Update pom.xml
    print("📦 Updating pom.xml...")
    update_pom_xml(dry_run)
    print()

    # Step 5: Search for hardcoded strings
    print("🔍 Searching for hardcoded package strings...")
    hardcoded = search_hardcoded_strings(SRC_DIR, dry_run)
    if hardcoded:
        print(f"⚠️  Found {len(hardcoded)} hardcoded references:")
        for file_path, line_num, line_content in hardcoded[:10]:  # Show first 10
            print(f"   {file_path.relative_to(PROJECT_ROOT)}:{line_num}")
            print(f"      {line_content}")
        if len(hardcoded) > 10:
            print(f"   ... and {len(hardcoded) - 10} more")
        print()
        print("   ⚠️  These need manual review and fixing")
    else:
        print("✓ No hardcoded package strings found")
    print()

    # Step 6: Move directory structure (LAST - after all content updates)
    if not dry_run:
        print("📁 Moving directory structure...")
        move_directory_structure(dry_run)
        print()
    else:
        print("📁 Directory move preview:")
        move_directory_structure(dry_run)
        print()

    # Summary
    print("=" * 60)
    print("SUMMARY")
    print("=" * 60)
    print(f"Files processed: {stats['files_processed']}")
    print(f"Package declarations updated: {stats['package_declarations']}")
    print(f"Import statements updated: {stats['import_statements']}")
    print(f"Hardcoded strings found: {stats['hardcoded_strings']}")

    if stats['errors']:
        print(f"\n⚠️  Errors encountered: {len(stats['errors'])}")
        for error in stats['errors'][:5]:
            print(f"   - {error}")
        if len(stats['errors']) > 5:
            print(f"   ... and {len(stats['errors']) - 5} more")

    if dry_run:
        print("\n✓ Dry run complete. Run with --apply to make changes.")
    else:
        print("\n✓ Refactor complete!")
        print("\nNext steps:")
        print("1. Test compile: mvn clean compile")
        print("2. Fix any hardcoded strings found above")
        print("3. Test build: mvn clean package -DskipTests")
        print("4. Commit: git add -A && git commit -m 'Refactor: zenax.armorsets → miracle.arcanesigils'")

if __name__ == "__main__":
    main()
