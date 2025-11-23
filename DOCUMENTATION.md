# ArmorSets Plugin - Documentation Guide

Welcome! This guide will help you navigate the documentation for the ArmorSets plugin.

---

## 📖 Documentation Structure

All documentation is organized in the `docs/` folder:

```
docs/
├── INDEX.md                          ← START HERE (documentation guide)
├── BUILDING.md                       ← How to build the plugin
├── ADMIN_GUIDE.md                    ← Server administration guide
├── DEVELOPER_GUIDE.md                ← Plugin development guide
├── PLUGIN_DESIGN_DOCUMENT (1).md     ← Original design specification
├── FEATURE_SPEC_VISUAL_EFFECT_BUILDER.md ← Feature specifications
└── examples/                         ← Example configuration files
    ├── armor-set-basic.yml
    ├── armor-set-advanced.yml
    ├── core-function-basic.yml
    └── config-recommended.yml
```

---

## 🚀 Quick Navigation

### I'm a Server Administrator
1. Read **[docs/BUILDING.md](docs/BUILDING.md)** to build/install the plugin
2. Read **[docs/ADMIN_GUIDE.md](docs/ADMIN_GUIDE.md)** for complete configuration guide
3. Copy example files from **[docs/examples/](docs/examples/)**
4. Use **[README.md](README.md)** as a quick reference

### I'm a Plugin Developer
1. Read **[docs/DEVELOPER_GUIDE.md](docs/DEVELOPER_GUIDE.md)** for architecture and setup
2. Check **[docs/PLUGIN_DESIGN_DOCUMENT](docs/PLUGIN_DESIGN_DOCUMENT%20(1).md)** for design details
3. Study example code and follow code style guide
4. Contribute improvements via pull requests

### I'm Creating Content (Armor Sets)
1. Read **[docs/ADMIN_GUIDE.md#creating-armor-sets](docs/ADMIN_GUIDE.md#creating-armor-sets)**
2. Copy and modify **[docs/examples/armor-set-basic.yml](docs/examples/armor-set-basic.yml)**
3. Use **[README.md#effects-system](README.md#effects-system)** for effect reference

### I'm Creating Functions (Abilities)
1. Read **[docs/ADMIN_GUIDE.md#creating-core-functions](docs/ADMIN_GUIDE.md#creating-core-functions)**
2. Copy and modify **[docs/examples/core-function-basic.yml](docs/examples/core-function-basic.yml)**
3. Use **[README.md](README.md)** for trigger types and effects

### Something Isn't Working
1. Check **[docs/ADMIN_GUIDE.md#troubleshooting](docs/ADMIN_GUIDE.md#troubleshooting)**
2. Check **[docs/BUILDING.md#troubleshooting](docs/BUILDING.md#troubleshooting)**
3. Read **[README.md#troubleshooting](README.md#troubleshooting)**
4. Enable debug mode and check console logs

---

## 📚 What Each File Contains

### **[docs/INDEX.md](docs/INDEX.md)** ← START HERE
Complete navigation guide with:
- Quick-start paths for each user type
- Index of all documentation
- Search-by-topic reference

### **[docs/BUILDING.md](docs/BUILDING.md)**
How to build the plugin from source:
- Prerequisites (Java 21, Maven)
- Step-by-step build instructions
- Deployment to server
- Troubleshooting build issues

### **[docs/ADMIN_GUIDE.md](docs/ADMIN_GUIDE.md)**
Complete server administration guide:
- Installation instructions
- Configuration file structure
- Creating armor sets (with detailed examples)
- Creating core functions
- Commands and permissions
- Performance tips
- Troubleshooting

### **[docs/DEVELOPER_GUIDE.md](docs/DEVELOPER_GUIDE.md)**
Plugin development manual:
- Project structure
- Architecture overview
- Creating new effect types
- Adding event triggers
- Integration points
- Code style conventions
- Testing

### **[README.md](README.md)**
Quick reference guide:
- Features overview
- Commands reference
- Configuration file examples
- Effects system reference
- Color codes
- Permissions
- Troubleshooting

### **[docs/examples/](docs/examples/)**
Ready-to-use configuration files:
- `armor-set-basic.yml` - Simple armor set with comments
- `armor-set-advanced.yml` - Complex set with multiple synergies
- `core-function-basic.yml` - Basic socketable functions
- `config-recommended.yml` - Suggested plugin configuration

### **[docs/PLUGIN_DESIGN_DOCUMENT.md](docs/PLUGIN_DESIGN_DOCUMENT%20(1).md)**
Original design specification (reference):
- Project overview
- System architecture
- Requirements analysis
- Feature specifications
- Configuration schemas

### **[docs/FEATURE_SPEC_VISUAL_EFFECT_BUILDER.md](docs/FEATURE_SPEC_VISUAL_EFFECT_BUILDER.md)**
Feature specification (reference):
- Visual GUI builder specification
- User stories
- Technical implementation details
- Use cases

---

## 🎯 Getting Started

### Step 1: Choose Your Role
- ✅ Server Administrator
- ✅ Plugin Developer
- ✅ Content Creator
- ✅ Game Designer

### Step 2: Read the Right Guide
Look at the "Quick Navigation" section above

### Step 3: Reference Examples
Copy and modify files from `docs/examples/`

### Step 4: Use Quick Reference
Use `README.md` for fast lookups

---

## 🔍 Finding Information

### By Topic
| Topic | Location |
|-------|----------|
| Build plugin | [docs/BUILDING.md](docs/BUILDING.md) |
| Install plugin | [docs/ADMIN_GUIDE.md#installation](docs/ADMIN_GUIDE.md#installation) |
| Create armor sets | [docs/ADMIN_GUIDE.md#creating-armor-sets](docs/ADMIN_GUIDE.md#creating-armor-sets) |
| Create functions | [docs/ADMIN_GUIDE.md#creating-core-functions](docs/ADMIN_GUIDE.md#creating-core-functions) |
| Effect types | [README.md#effects-system](README.md#effects-system) |
| Commands | [docs/ADMIN_GUIDE.md#commands--permissions](docs/ADMIN_GUIDE.md#commands--permissions) |
| Permissions | [README.md#permissions](README.md#permissions) |
| Color codes | [README.md#color-codes](README.md#color-codes) |
| Development | [docs/DEVELOPER_GUIDE.md](docs/DEVELOPER_GUIDE.md) |
| Troubleshooting | [docs/ADMIN_GUIDE.md#troubleshooting](docs/ADMIN_GUIDE.md#troubleshooting) |
| Examples | [docs/examples/](docs/examples/) |

### By Skill Level
| Level | Start With | Then Read |
|-------|-----------|-----------|
| Beginner | [docs/BUILDING.md](docs/BUILDING.md) | [docs/ADMIN_GUIDE.md](docs/ADMIN_GUIDE.md) |
| Intermediate | [docs/examples/](docs/examples/) | [README.md](README.md) |
| Advanced | [docs/DEVELOPER_GUIDE.md](docs/DEVELOPER_GUIDE.md) | [docs/PLUGIN_DESIGN_DOCUMENT](docs/PLUGIN_DESIGN_DOCUMENT%20(1).md) |

---

## ✅ Documentation Checklist

- ✅ **Complete** - All major topics covered
- ✅ **Organized** - Clear folder structure
- ✅ **Searchable** - Table of contents and index
- ✅ **Practical** - Real examples and code samples
- ✅ **Updated** - Current as of November 2025
- ✅ **Accessible** - Written for different skill levels

---

## 📝 File Cleanup Summary

The following redundant files were removed to clean up the codebase:

**Deleted:**
- START_HERE.md (outdated, referenced non-existent Gradle setup)
- QUICK_START.md (superseded by BUILDING.md)
- BUILD_GUIDE.md (superseded by BUILDING.md)
- MAVEN_FREE_SETUP.md (confusing and outdated)
- SETUP_SUMMARY.txt (outdated)
- COMPILATION_OPTIONS.md (outdated)
- BUGFIXES_APPLIED.md (historical, not relevant)
- CONDITIONS_FEATURE.md (old feature doc)
- TRIGGER_SYSTEM_GUIDE.md (old feature doc)
- classpath.txt (empty file)
- ArmorSets.iml (IDE metadata, auto-generated)
- download-dependencies.ps1 (old build script)
- build.ps1 (old build script)
- build.bat (old build script, replaced by Maven)
- compile-and-deploy.sh (old build script)

**Kept:**
- README.md (main reference, comprehensive and useful)
- PLUGIN_DESIGN_DOCUMENT.md (original design spec)
- FEATURE_SPEC_VISUAL_EFFECT_BUILDER.md (feature planning)
- pom.xml (Maven build configuration)

**Created:**
- docs/INDEX.md (documentation index)
- docs/BUILDING.md (clear build instructions)
- docs/ADMIN_GUIDE.md (comprehensive admin manual)
- docs/DEVELOPER_GUIDE.md (developer manual)
- docs/examples/armor-set-basic.yml (example file)
- docs/examples/armor-set-advanced.yml (example file)
- docs/examples/core-function-basic.yml (example file)
- docs/examples/config-recommended.yml (example file)
- DOCUMENTATION.md (this file)

---

## 🚀 Next Steps

1. **Browse** the [docs/](docs/) folder
2. **Start with** [docs/INDEX.md](docs/INDEX.md)
3. **Follow** the quick-start path for your role
4. **Reference** example files from [docs/examples/](docs/examples/)
5. **Bookmark** README.md for quick lookups

---

**Questions?** Check [docs/INDEX.md](docs/INDEX.md) for help finding answers.

**Report Issues?** Enable debug mode and check console logs (see ADMIN_GUIDE.md).

