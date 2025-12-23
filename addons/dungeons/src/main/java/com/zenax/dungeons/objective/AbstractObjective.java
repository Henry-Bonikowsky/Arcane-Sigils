package com.zenax.dungeons.objective;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * Abstract base class for dungeon objectives.
 * Provides default implementations for common objective functionality.
 */
public abstract class AbstractObjective implements DungeonObjective {
    protected final String id;
    protected final ObjectiveType type;
    protected final String description;
    protected boolean complete;

    /**
     * Creates a new abstract objective.
     *
     * @param id The unique identifier for this objective
     * @param type The type of this objective
     * @param description The description of this objective
     */
    protected AbstractObjective(String id, ObjectiveType type, String description) {
        this.id = id;
        this.type = type;
        this.description = description;
        this.complete = false;
    }

    @Override
    public ObjectiveType getType() {
        return type;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public boolean isComplete() {
        return complete;
    }

    /**
     * Sets the completion status of this objective.
     *
     * @param complete true if the objective is complete
     */
    protected void setComplete(boolean complete) {
        this.complete = complete;
    }

    @Override
    public Component getDisplayComponent() {
        Component progressComponent = Component.text(
            String.format("%.0f%%", getProgress() * 100),
            complete ? NamedTextColor.GREEN : NamedTextColor.YELLOW
        );

        Component descriptionComponent = Component.text(
            description,
            complete ? NamedTextColor.GRAY : NamedTextColor.WHITE
        );

        Component checkmark = complete
            ? Component.text("✓ ", NamedTextColor.GREEN, TextDecoration.BOLD)
            : Component.text("○ ", NamedTextColor.GRAY);

        return Component.text()
            .append(checkmark)
            .append(descriptionComponent)
            .append(Component.text(" [", NamedTextColor.DARK_GRAY))
            .append(progressComponent)
            .append(Component.text("]", NamedTextColor.DARK_GRAY))
            .build();
    }

    @Override
    public String toString() {
        return String.format("Objective{id=%s, type=%s, complete=%s, progress=%.2f}",
            id, type, complete, getProgress());
    }
}
