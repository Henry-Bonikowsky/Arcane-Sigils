# Minecraft Display Entity Transformations: A Comprehensive Research Report

**Author:** Research compiled from 35+ authoritative sources
**Date:** January 2026
**Scope:** Java Edition 1.19.4+ Display Entities, Transformations, and Rotation Systems

---

## Table of Contents

1. [Introduction to Display Entities](#1-introduction-to-display-entities)
2. [The Three Display Entity Types](#2-the-three-display-entity-types)
3. [The Critical Difference: BlockDisplay vs ItemDisplay Origins](#3-the-critical-difference-blockdisplay-vs-itemdisplay-origins)
4. [Transformation System Deep Dive](#4-transformation-system-deep-dive)
5. [Matrix Format and SVD Decomposition](#5-matrix-format-and-svd-decomposition)
6. [Quaternion Mathematics](#6-quaternion-mathematics)
7. [JOML Library Integration](#7-joml-library-integration)
8. [Billboard Modes](#8-billboard-modes)
9. [Interpolation and Animation](#9-interpolation-and-animation)
10. [Additional Display Properties](#10-additional-display-properties)
11. [Tools and Resources](#11-tools-and-resources)
12. [Solving the Rotation Pivot Problem](#12-solving-the-rotation-pivot-problem)
13. [Performance Considerations](#13-performance-considerations)
14. [Complete Source Bibliography](#14-complete-source-bibliography)

---

## 1. Introduction to Display Entities

Display entities were introduced in **Minecraft Java Edition 1.19.4** (Snapshot 23w06a, released February 8, 2023) as a powerful feature for map and data pack creators to showcase various elements within the game [1][2].

### Key Characteristics

- **Non-ticking entities**: Display entities do not tick, making them significantly less resource-intensive than armor stands [3]
- **No hitbox**: Using F3+B shows no hitbox for display entities [4]
- **No physics**: They do not move, take damage, make sounds, or have collision [4]
- **Command-only creation**: Can only be created via `/summon` or `/execute summon` commands [4]
- **Arbitrary transformations**: Support full 3D affine transformations including translation, rotation, and scale [5]

### Historical Context

Before 1.19.4, creators used armor stands for custom displays and holograms. Display entities were specifically designed to replace armor stands for decorative purposes [3]:

> "In 1.19.4, Mojang added 3 different display entities. They exist to replace armor stands when decorating builds or creating holograms."

---

## 2. The Three Display Entity Types

### 2.1 Block Display (`block_display`)
Displays any block from the game using the `block_state` NBT tag [4].

**Default NBT**: `{block_state:{Name:"minecraft:air"}}`

### 2.2 Item Display (`item_display`)
Displays any item using the `item` NBT tag. Can also display blocks as items [4].

**Default NBT**: `{item:{id:"minecraft:air",count:0}}`

**Additional property - `item_display` tag**: Controls the render transform applied to the item model. Options include:
- `none` (default)
- `thirdperson_lefthand` / `thirdperson_righthand`
- `firstperson_lefthand` / `firstperson_righthand`
- `head`
- `gui`
- `ground`
- `fixed` (Item Frame style) [6]

### 2.3 Text Display (`text_display`)
Displays formatted text using JSON text components [4].

**Default NBT**: `{text:'{"text":""}'}`

---

## 3. The Critical Difference: BlockDisplay vs ItemDisplay Origins

**THIS IS THE MOST IMPORTANT SECTION FOR YOUR ROTATION PROBLEM.**

### The Origin Point Difference

From the official Minecraft Wiki [4][7]:

> "Despite its name, an item display can also display blocks, with the difference being the position in the model - **an item display has its position in the center**, whereas **a block display has its position in the corner of the block** (this can be seen with the hitbox debug mode - F3+B)."

More specifically [8]:

- **ItemDisplay**: The location represents the **CENTER** of the item/block model
- **BlockDisplay**: The location represents the **north-west-bottom corner** (negative XYZ corner) of the block

### Visual Representation

```
BlockDisplay Origin (corner):          ItemDisplay Origin (center):

     +--------+                              +--------+
    /        /|                             /        /|
   /        / |                            /        / |
  +--------+  |                           +--------+  |
  |        |  +                           |    *   |  +
  |        | /                            |        | /
  | *      |/                             |        |/
  +--------+                              +--------+

  * = origin point                        * = origin point
  (corner at 0,0,0)                       (center at 0.5,0.5,0.5)
```

### Why This Matters for Rotation

When you apply a rotation transformation:
- **ItemDisplay**: Rotates around its center - natural and intuitive
- **BlockDisplay**: Rotates around its corner - causes displacement during rotation

This is the **root cause** of why your disc of blocks appears as an oval when rotation is applied. Each block rotates around its own corner, causing it to shift position in a way that distorts the circular pattern.

---

## 4. Transformation System Deep Dive

### 4.1 Transformation Order

Transformations are applied in a **specific, fixed order** [5][8]:

```
1. right_rotation  →  2. scale  →  3. left_rotation  →  4. translation
```

This is the **decomposed form** of the transformation. Mathematically, the final transformation is:

```
Final = Translation × LeftRotation × Scale × RightRotation × vertex
```

### 4.2 Transformation Tag Structure

The transformation can be specified in two forms [8][9]:

#### Decomposed Form (Recommended)
```nbt
transformation: {
    translation: [tx, ty, tz],      // Vector3f - position offset
    left_rotation: [x, y, z, w],    // Quaternion - applied after scale
    scale: [sx, sy, sz],            // Vector3f - scaling factors
    right_rotation: [x, y, z, w]    // Quaternion - applied first
}
```

#### Matrix Form
A 16-element array describing a 4x4 row-major affine transformation matrix [9]:
```nbt
transformation: [s0,s1,s2,tx, s3,s4,s5,ty, s6,s7,s8,tz, i0,i1,i2,w]
```

### 4.3 Understanding Left vs Right Rotation

- **right_rotation**: Applied FIRST, before scaling. Corresponds to the right-singular vector matrix after SVD [8]
- **left_rotation**: Applied AFTER scaling. Corresponds to the left-singular vector matrix after SVD [8]

For simple rotations around a single axis, you typically only need one rotation (usually right_rotation).

---

## 5. Matrix Format and SVD Decomposition

### 5.1 The 4x4 Matrix Layout

From the MulverineX documentation [9]:

```
| s0  s1  s2  tx |
| s3  s4  s5  ty |
| s6  s7  s8  tz |
| i0  i1  i2  w  |
```

Where:
- **s0-s8**: The 3x3 rotation/scale submatrix
- **tx, ty, tz**: Translation components
- **w**: Divisor for all other values (except i0-i2)
- **i0-i2**: Ignored by the game

### 5.2 The `w` Component

**Critical understanding** [9]:

> "This value is used as a divisor of all other values (except i0-i2) - i.e., all the other values get divided by w before doing any further calculations."

After division by `w`:
- Translation becomes: `[tx/w, ty/w, tz/w]`
- The 3x3 submatrix is passed through SVD

### 5.3 Singular Value Decomposition (SVD)

The game uses **Singular Value Decomposition** to extract components from the 3x3 matrix [9][10]:

```
M = U × Σ × V^T
```

Where:
- **U** → `left_rotation` (orthogonal rotation matrix → quaternion)
- **Σ** → `scale` (diagonal matrix of singular values)
- **V^T** → `right_rotation` (orthogonal rotation matrix → quaternion)

**Important warning** [9]:
> "The exact values of this decomposition are difficult to predict."

This means if you provide a matrix, the decomposed values may not match your intuitive expectations.

---

## 6. Quaternion Mathematics

### 6.1 Why Quaternions?

From 3D game development literature [11][12]:

Quaternions are preferred over Euler angles and rotation matrices because:
1. **No gimbal lock**: Euler angles suffer from gimbal lock at certain orientations
2. **Memory efficient**: 4 floats vs 9 for a rotation matrix
3. **Computation efficient**: Quaternion multiplication is faster than matrix multiplication
4. **Smooth interpolation**: SLERP (Spherical Linear Interpolation) works naturally with quaternions

### 6.2 Quaternion Structure

A quaternion has four components [12][13]:

```
q = (x, y, z, w)  or  q = (vector, scalar)
```

Where:
- `x, y, z` = imaginary/vector components (define rotation axis)
- `w` = real/scalar component

**Minecraft format** [8]: Components are stored as `[x, y, z, w]` with the **real component last**.

### 6.3 Unit Quaternions

For representing pure rotation, quaternions must be **unit quaternions** (magnitude = 1) [12]:

```
|q| = sqrt(x² + y² + z² + w²) = 1
```

**Important** [8]:
> "Note that non-unit quaternion also scales the model."

### 6.4 Axis-Angle to Quaternion Conversion

Given an axis `(ax, ay, az)` and angle `θ` in radians [13]:

```
x = ax × sin(θ/2)
y = ay × sin(θ/2)
z = az × sin(θ/2)
w = cos(θ/2)
```

### 6.5 Quaternion Negation Property

A surprising property [12]:

> "q and -q describe the same angular displacement."

Both `(x, y, z, w)` and `(-x, -y, -z, -w)` represent the same rotation.

---

## 7. JOML Library Integration

### 7.1 What is JOML?

JOML (Java OpenGL Math Library) is the math library used by Minecraft and Paper for 3D transformations [14][15].

Key classes:
- `Vector3f` - 3D vector
- `Quaternionf` - Quaternion for rotations
- `AxisAngle4f` - Axis-angle rotation representation
- `Matrix4f` - 4x4 transformation matrix

### 7.2 Matrix Transformation Order in JOML

From the JOML wiki [16]:

> "All transformation operations in the matrix and quaternion classes act in the same way as OpenGL and GLU by post-multiplying the operation's result to the object on which they are invoked."

This means transformations are applied **right to left** when reading code:

```java
Matrix4f m = new Matrix4f()
    .translate(2, 0, 0)    // Applied LAST
    .scale(0.5f)           // Applied SECOND
    .rotateY(radians);     // Applied FIRST
```

### 7.3 Creating Rotations

**From axis-angle** [17]:
```java
Quaternionf q = new Quaternionf().rotationAxis(angle, axisX, axisY, axisZ);
// or
Quaternionf q = new Quaternionf().rotationY(angle);  // Rotate around Y axis
```

**From AxisAngle4f** [18]:
```java
AxisAngle4f aa = new AxisAngle4f(angle, axisX, axisY, axisZ);
Quaternionf q = new Quaternionf(aa);
```

### 7.4 Paper API Usage

From the PaperMC documentation [5]:

```java
// Method 1: Using Transformation class
entity.setTransformation(new Transformation(
    new Vector3f(),                                    // translation
    new AxisAngle4f((float) -Math.toRadians(45), 1, 0, 0),  // left rotation
    new Vector3f(2, 2, 2),                             // scale
    new AxisAngle4f((float) Math.toRadians(45), 0, 0, 1)    // right rotation
));

// Method 2: Using raw Matrix4f
entity.setTransformationMatrix(new Matrix4f()
    .scale(2)
    .rotateY(angle));
```

---

## 8. Billboard Modes

### 8.1 Available Modes

The `billboard` property controls automatic rotation to face the player [4][8]:

| Mode | Behavior |
|------|----------|
| `fixed` | No automatic rotation (default) |
| `vertical` | Rotates around vertical (Y) axis to face player |
| `horizontal` | Rotates around horizontal axis to face player |
| `center` | Fully rotates to always face the player |

### 8.2 When to Use Each Mode

- **fixed**: Static displays, furniture, architectural elements
- **vertical**: Signs, billboards that should always be readable but stay upright
- **horizontal**: Ceiling/floor displays that tilt toward player
- **center**: Holograms, floating text that should always face the viewer

---

## 9. Interpolation and Animation

### 9.1 Interpolatable Properties

The following properties can be smoothly animated [4][8]:

- `transformation` (all components)
- `shadow_radius`
- `shadow_strength`
- Entity position (via `teleport_duration`)

### 9.2 Key Properties

**interpolation_duration** [8]:
- Duration in game ticks for transformation interpolation
- When changed, client interpolates from old to new transformation

**start_interpolation** [8]:
- Delay before interpolation begins (in game ticks)
- Setting to 0 starts immediately
- Not saved to entity NBT

**teleport_duration** [8]:
- Duration for position interpolation when teleporting
- **Values clamped between 0 and 59 ticks** (max ~3 seconds)
- Use for smooth movement without affecting transformation

### 9.3 Animation Workflow

1. Spawn entity with initial state
2. Wait 2 ticks (important for spawn animation to work) [5]
3. Set `interpolation_duration`
4. Merge new transformation data
5. Entity animates to new state

### 9.4 Known Bug (Fixed in 25w05a)

There was a bug where display entities used `interpolation_duration` for teleportation instead of `teleport_duration` [19].

---

## 10. Additional Display Properties

### 10.1 Rendering Properties

| Property | Description | Default |
|----------|-------------|---------|
| `view_range` | Max render distance multiplier | 1.0 |
| `shadow_radius` | Shadow size (max 64) | 0 |
| `shadow_strength` | Shadow opacity | 1.0 |
| `glow_color_override` | Glowing outline color (ARGB) | -1 (team color) |
| `brightness` | Override block/sky light | omitted |

### 10.2 Culling Properties

| Property | Description | Default |
|----------|-------------|---------|
| `width` | Culling box width | 0 (disabled) |
| `height` | Culling box height | 0 (disabled) |

**Important** [20]: If width and height are 0, culling is disabled and the entity is always rendered regardless of camera direction.

### 10.3 Text Display Specific

| Property | Description |
|----------|-------------|
| `text` | JSON text component |
| `text_opacity` | Alpha value (0-255) |
| `background` | Background color (ARGB) |
| `line_width` | Max line width for wrapping |
| `default_background` | Use chat-style background |

---

## 11. Tools and Resources

### 11.1 Visual Editors

**BDStudio** [21][22]:
- Web-based 3D editor for display entities
- URL: https://eszesbalint.github.io/bdstudio/editor
- Features: Visual editing, command generation, collection management

**BDEngine** [23]:
- Online editor with animation support
- URL: https://bdengine.app/
- Features: Block/Item/Text displays, animation timeline, datapack export

**Misode's Transformation Visualizer** [24]:
- Interactive transformation preview
- URL: https://misode.github.io/transformation/
- Useful for quick prototyping

### 11.2 Command Generators

- **Gamer Geeks Block Display Generator** [25]: https://www.gamergeeks.net/apps/minecraft/block-display-command-generator
- **MCStacker** [26]: https://mcstacker.net/

### 11.3 In-Game Plugins

- **Display Entity Editor** (SpigotMC) [27]: Visual editing plugin
- **EasyArmorStands** [28]: Supports both armor stands and display entities
- **Block Display Helper** (Datapack) [29]: Move/scale/rotate sticks

---

## 12. Solving the Rotation Pivot Problem

### 12.1 The Problem

When rotating a BlockDisplay, it rotates around its corner (0,0,0) rather than its center (0.5, 0.5, 0.5). This causes:
- Blocks to shift position during rotation
- Circular arrangements to become ovals
- Unpredictable visual results

### 12.2 Solution Option 1: Use ItemDisplay Instead

**The simplest solution** [4][7]:

Since ItemDisplay has its origin at the center by default, simply use ItemDisplay instead of BlockDisplay:

```java
// Instead of BlockDisplay
ItemDisplay display = world.spawn(location, ItemDisplay.class, entity -> {
    entity.setItemStack(new ItemStack(Material.SAND));
    // Rotation will be around center automatically!
    entity.setTransformation(new Transformation(
        new Vector3f(),
        new Quaternionf().rotationY(yawRadians),
        new Vector3f(1, 1, 1),
        new Quaternionf()
    ));
});
```

### 12.3 Solution Option 2: Manual Center Compensation

If you must use BlockDisplay, compensate mathematically:

**Step 1**: Translate the block so its center is at origin
**Step 2**: Apply rotation
**Step 3**: Translate back

```java
float halfScale = 0.5f * scale;
float angle = (float) Math.toRadians(yaw);
float cos = (float) Math.cos(angle);
float sin = (float) Math.sin(angle);

// The rotation moves the center, so we need to compensate
// Original center: (0.5, 0.5, 0.5) * scale
// After Y-rotation: center moves to a new position
// Compensation = original_center - rotated_center

float tx = halfScale * (1 - cos) + halfScale * sin;
float ty = -halfScale;
float tz = halfScale * (1 - cos) - halfScale * sin;

// Or more simply, using matrix approach:
Matrix4f transform = new Matrix4f()
    .translate(0.5f * scale, 0.5f * scale, 0.5f * scale)  // Move center to origin
    .rotateY(angle)                                         // Rotate
    .translate(-0.5f * scale, -0.5f * scale, -0.5f * scale) // Move back
    .scale(scale);

display.setTransformationMatrix(transform);
```

### 12.4 Solution Option 3: Pre-offset Spawn Position

Adjust the spawn position to account for rotation displacement:

```java
// Calculate where the center will end up after rotation
// Then adjust spawn position to compensate
double rotatedOffsetX = 0.5 * (Math.cos(yaw) - 1) - 0.5 * Math.sin(yaw);
double rotatedOffsetZ = 0.5 * Math.sin(yaw) + 0.5 * (Math.cos(yaw) - 1);

Location spawnLoc = baseLocation.clone().add(-rotatedOffsetX, 0, -rotatedOffsetZ);
```

### 12.5 Community Request for Native Solution

There is an official Minecraft feedback post requesting a pivot point tag [30]:

> "Display Entities should have a point of rotation tag"

Until Mojang implements this, the workarounds above are necessary.

---

## 13. Performance Considerations

### 13.1 Display Entities vs Armor Stands

| Aspect | Display Entities | Armor Stands |
|--------|-----------------|--------------|
| Ticking | Non-ticking | Ticks every game tick |
| TPS Impact | Minimal | ~0.1 TPS per entity |
| Memory | Lower | Higher |
| Collision | None | Has hitbox |
| Recommended | Yes | Legacy/compatibility only |

### 13.2 Culling Configuration

For optimal performance [20]:
- Set appropriate `width` and `height` values
- Entities outside culling box won't render
- Leaving at 0 means always rendered (expensive for many entities)

### 13.3 View Range

Adjust `view_range` based on importance:
- Large structures: 1.0 (default)
- Details: 0.5 or lower
- Critical elements: Up to 64.0

---

## 14. Complete Source Bibliography

### Official Documentation

[1] Minecraft. (2023). *Minecraft Snapshot 23w06a*. https://www.minecraft.net/en-us/article/minecraft-snapshot-23w06a

[2] Minecraft Wiki. *Java Edition 23w06a*. https://minecraft.wiki/w/Java_Edition_23w06a

[3] SpigotMC Forums. *Summon block display*. https://www.spigotmc.org/threads/summon-block-display.606267/

[4] Minecraft Wiki. *Display*. https://minecraft.wiki/w/Display

[5] PaperMC. *Display entities*. https://docs.papermc.io/paper/dev/display-entities/

[6] Minecraft Wiki. *Display/ED/Item Display*. https://minecraft.wiki/w/Display/ED/Item_Display

[7] SpigotMC Forums. *Rotating block display around its center*. https://www.spigotmc.org/threads/rotating-block-display-around-its-center.598014/

[8] Minecraft Wiki. *Chunk format/display entity*. https://minecraft.wiki/w/Chunk_format/display_entity

### Technical References

[9] MulverineX. *Minecraft Display Entity Transformation*. GitHub Gist. https://gist.github.com/MulverineX/f473dbfd7cc8dadb326074fef05ad76a

[10] Wikipedia. *Singular value decomposition*. https://en.wikipedia.org/wiki/Singular_value_decomposition

[11] 3D Game Engine Programming. *Understanding Quaternions*. https://www.3dgep.com/understanding-quaternions/

[12] Game Math Book. *Rotation in Three Dimensions*. https://gamemath.com/book/orient.html

[13] Harold Serrano. *Quaternions in Computer Graphics*. https://www.haroldserrano.com/blog/quaternions-in-computer-graphics

### JOML Documentation

[14] JOML. *Java OpenGL Math Library*. https://joml-ci.github.io/JOML/

[15] GitHub. *JOML-CI/JOML*. https://github.com/JOML-CI/JOML

[16] JOML Wiki. *Tutorial - Matrix Transformation Order*. https://github.com/JOML-CI/JOML/wiki/Tutorial---Matrix-Transformation-Order

[17] JOML API. *Quaternionf*. https://joml-ci.github.io/JOML/apidocs/org/joml/Quaternionf.html

[18] JOML API. *AxisAngle4f*. https://joml-ci.github.io/JOML/apidocs/org/joml/AxisAngle4f.html

### Bug Reports and Issues

[19] Mojang Bug Tracker. *MC-279534 - Display entities use interpolation duration value for teleport*. https://bugs.mojang.com/browse/MC-279534

[20] GitHub Sodium. *Issue #2114 - Display entities with large width/height make entity culling slow*. https://github.com/CaffeineMC/sodium/issues/2114

### Tools and Resources

[21] BDStudio. *Minecraft Block Display Studio*. https://eszesbalint.github.io/bdstudio/editor

[22] GitHub. *eszesbalint/bdstudio*. https://github.com/eszesbalint/bdstudio

[23] BDEngine. *Minecraft Display Entity Editor*. https://bdengine.app/

[24] Misode. *Transformation Visualizer*. https://misode.github.io/transformation/

[25] Gamer Geeks. *Block Display Command Generator*. https://www.gamergeeks.net/apps/minecraft/block-display-command-generator

[26] MCStacker. *Minecraft Command Generator*. https://mcstacker.net/

[27] SpigotMC. *Display Entity Editor*. https://www.spigotmc.org/resources/display-entity-editor.110267/

[28] GitHub. *56738/EasyArmorStands*. https://github.com/56738/EasyArmorStands

[29] Modrinth. *Block Display Helper*. https://modrinth.com/datapack/block-display-helper

### Community Resources

[30] Minecraft Feedback. *Display Entities should have a point of rotation tag*. https://feedback.minecraft.net/hc/en-us/community/posts/17457916296973-Display-Entities-should-have-a-point-of-rotation-tag

[31] Planet Minecraft Forums. *How the Heck Does Display Rotation Work??*. https://www.planetminecraft.com/forums/help/javaedition/how-the-heck-do-display-entities-work-678047/

[32] Eszes Balint. *Minecraft Display Entities: A Comprehensive Guide*. https://eszesbalint.github.io/posts/2023/06/blog-post-5/

[33] Bedrock Wiki. *FMBE - A New Way to Create Display Entities*. https://wiki.bedrock.dev/commands/display-entities

[34] Minecraft Wiki. *Text component format*. https://minecraft.wiki/w/Text_component_format

[35] Minecraft Wiki. */data command*. https://minecraft.wiki/w/Commands/data

[36] Paper API Javadocs. *BlockDisplay (1.21.8)*. https://jd.papermc.io/paper/1.21.8/org/bukkit/entity/BlockDisplay.html

[37] Spigot API. *Display Interface*. https://hub.spigotmc.org/javadocs/bukkit/org/bukkit/entity/Display.html

---

## Summary: Key Takeaways

1. **BlockDisplay origin is at the corner; ItemDisplay origin is at the center** - This is the fundamental cause of rotation issues

2. **Use ItemDisplay for center-based rotation** - If you need blocks to rotate around their centers, ItemDisplay is the correct choice

3. **Transformation order is fixed**: right_rotation → scale → left_rotation → translation

4. **Quaternions use (x, y, z, w) format** with w (real component) last

5. **SVD decomposition** breaks down matrices into rotation and scale components - results can be unpredictable

6. **Interpolation enables smooth animation** - set duration first, then change values

7. **Performance**: Display entities are significantly more efficient than armor stands

---

*End of Report*
