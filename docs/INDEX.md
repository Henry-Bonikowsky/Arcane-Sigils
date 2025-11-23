# ArmorSets Plugin - Documentation Index

Welcome to the ArmorSets Plugin documentation! This guide will help you find what you need.

---

## 📚 For Different Audiences

### 👤 For Server Administrators

Start here if you want to install and configure the plugin on your server.

1. **[BUILDING.md](BUILDING.md)** - How to build/compile the plugin
   - Prerequisites (Java, Maven)
   - Step-by-step build instructions
   - Deploying to your server
   - Troubleshooting build issues

2. **[ADMIN_GUIDE.md](ADMIN_GUIDE.md)** - Complete admin manual
   - Installation instructions
   - Configuration file structure
   - Creating armor sets
   - Creating core functions
   - Commands and permissions
   - Troubleshooting
   - Performance tips

3. **[../README.md](../README.md)** - Quick reference
   - Feature overview
   - Command list
   - Configuration examples
   - Color codes reference
   - Permissions reference

4. **[examples/](examples/)** - Copy-paste example files
   - `armor-set-basic.yml` - Simple armor set
   - `armor-set-advanced.yml` - Complex armor set
   - `core-function-basic.yml` - Simple functions
   - `config-recommended.yml` - Suggested configuration

### 👨‍💻 For Developers

Start here if you want to extend or contribute to the plugin.

1. **[DEVELOPER_GUIDE.md](DEVELOPER_GUIDE.md)** - Development manual
   - Project structure
   - Architecture overview
   - How to build locally
   - Creating new effect types
   - Adding event triggers
   - Integration points
   - Code style guide
   - Testing

2. **[../README.md](../README.md)** - Effect reference
   - All available effects
   - How effects work
   - Creating custom effects

### 🎨 For Content Creators

Start here if you want to create armor sets and functions.

1. **[ADMIN_GUIDE.md](ADMIN_GUIDE.md)** - Configuration guide
   - Armor set structure
   - Core function structure
   - All available triggers
   - Tips for creating balanced content

2. **[examples/](examples/)** - Example files to learn from
   - Study basic examples
   - Study advanced examples
   - Copy and modify

---

## 📖 Full Documentation List

### Getting Started
- **[BUILDING.md](BUILDING.md)** - Compile and deploy the plugin

### Configuration Guides
- **[ADMIN_GUIDE.md](ADMIN_GUIDE.md)** - Complete server administration guide
- **[../README.md](../README.md)** - Quick reference and effects list

### Development
- **[DEVELOPER_GUIDE.md](DEVELOPER_GUIDE.md)** - Plugin development guide

### Example Files
- **[examples/armor-set-basic.yml](examples/armor-set-basic.yml)** - Simple armor set example
- **[examples/armor-set-advanced.yml](examples/armor-set-advanced.yml)** - Complex armor set example
- **[examples/core-function-basic.yml](examples/core-function-basic.yml)** - Core function examples
- **[examples/config-recommended.yml](examples/config-recommended.yml)** - Recommended configuration

### Design Documentation
- **[PLUGIN_DESIGN_DOCUMENT (1).md](PLUGIN_DESIGN_DOCUMENT%20(1).md)** - Original design spec
- **[FEATURE_SPEC_VISUAL_EFFECT_BUILDER.md](FEATURE_SPEC_VISUAL_EFFECT_BUILDER.md)** - Feature specification

---

## 🚀 Quick Start Paths

### "I want to install and use this plugin"
1. Follow [BUILDING.md](BUILDING.md) to compile
2. Copy JAR to your server's `plugins/` folder
3. Restart server
4. Read [ADMIN_GUIDE.md](ADMIN_GUIDE.md) to configure
5. Copy examples from [examples/](examples/) and customize

### "I want to create armor sets"
1. Copy [examples/armor-set-basic.yml](examples/armor-set-basic.yml)
2. Read the comments in the file
3. Modify names, effects, and values
4. Place in `plugins/ArmorSets/sets/`
5. Use `/as reload` to test changes

### "I want to create core functions"
1. Copy [examples/core-function-basic.yml](examples/core-function-basic.yml)
2. Read the comments in the file
3. Modify names, effects, and values
4. Place in `plugins/ArmorSets/core-functions/`
5. Use `/as reload` to test changes

### "I want to extend the plugin"
1. Read [DEVELOPER_GUIDE.md](DEVELOPER_GUIDE.md)
2. Set up development environment
3. Study [DEVELOPER_GUIDE.md#creating-new-effect-types](DEVELOPER_GUIDE.md) for adding features
4. Run tests with `mvn test`
5. Submit pull request

### "Something isn't working"
1. Check [ADMIN_GUIDE.md#troubleshooting](ADMIN_GUIDE.md#troubleshooting)
2. Enable debug mode in config
3. Check console for error messages
4. Read [README.md#troubleshooting](../README.md#troubleshooting)

---

## 📋 File Structure

```
docs/
├── INDEX.md                          ← You are here
├── BUILDING.md                       ← How to build plugin
├── ADMIN_GUIDE.md                    ← Server admin manual
├── DEVELOPER_GUIDE.md                ← Developer manual
├── PLUGIN_DESIGN_DOCUMENT (1).md     ← Original design spec
├── FEATURE_SPEC_VISUAL_EFFECT_BUILDER.md ← Feature specification
└── examples/                         ← Example configuration files
    ├── armor-set-basic.yml
    ├── armor-set-advanced.yml
    ├── core-function-basic.yml
    └── config-recommended.yml

../
├── README.md                         ← Quick reference & effects list
├── pom.xml                           ← Maven build configuration
└── src/                              ← Source code
```

---

## 🔍 Finding Specific Information

### Commands
- See [ADMIN_GUIDE.md#commands--permissions](ADMIN_GUIDE.md#commands--permissions)
- Quick list in [README.md#commands](../README.md#commands)

### Effect Types
- Complete list in [README.md#effects-system](../README.md#effects-system)
- Developer guide at [DEVELOPER_GUIDE.md#creating-new-effect-types](DEVELOPER_GUIDE.md#creating-new-effect-types)

### Permissions
- All permissions in [ADMIN_GUIDE.md#permissions](ADMIN_GUIDE.md#permissions)
- Usage examples in [README.md#permissions](../README.md#permissions)

### Color Codes
- Reference in [README.md#color-codes](../README.md#color-codes)

### Configuration Files
- Format explained in [ADMIN_GUIDE.md#configuration-files](ADMIN_GUIDE.md#configuration-files)
- Examples in [examples/](examples/)

### Troubleshooting
- Server issues in [ADMIN_GUIDE.md#troubleshooting](ADMIN_GUIDE.md#troubleshooting)
- Build issues in [BUILDING.md#troubleshooting](BUILDING.md#troubleshooting)
- Plugin issues in [README.md#troubleshooting](../README.md#troubleshooting)

### Performance
- Tips in [ADMIN_GUIDE.md#performance-tips](ADMIN_GUIDE.md#performance-tips)
- Configuration in [examples/config-recommended.yml](examples/config-recommended.yml)

---

## 📞 Getting Help

### Check the Docs First
- Search this INDEX for your topic
- Most questions are answered in ADMIN_GUIDE.md or README.md

### Enable Debug Mode
1. Open `plugins/ArmorSets/config.yml`
2. Change `debug: false` to `debug: true`
3. Restart server
4. Check console for detailed logs

### Common Issues
See [ADMIN_GUIDE.md#troubleshooting](ADMIN_GUIDE.md#troubleshooting)

### Report Issues
- Check console for error messages
- Enable debug mode and reproduce issue
- Provide console output when reporting

---

## 📝 Documentation Quality

All documentation in this folder is:
- ✅ Up-to-date (as of November 2025)
- ✅ Tested and verified
- ✅ Written for clarity
- ✅ Includes practical examples
- ✅ Organized by audience

---

## 🎯 Next Steps

1. **Choose your path** above based on your role
2. **Read the relevant guide** from the list
3. **Check examples** for practical reference
4. **Test on your server** or development environment
5. **Report issues** or **submit improvements**

---

Happy configuring! 🚀

