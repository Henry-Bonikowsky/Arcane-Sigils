package com.miracle.arcanesigils.effects;

/**
 * Holds skin texture data for a player.
 * Used by SkinChangeManager to cache and apply skin changes.
 */
public class SkinData {

    private final String textureValue;
    private final String textureSignature;

    public SkinData(String textureValue, String textureSignature) {
        this.textureValue = textureValue;
        this.textureSignature = textureSignature;
    }

    /**
     * Get the base64-encoded texture value.
     */
    public String getTextureValue() {
        return textureValue;
    }

    /**
     * Get the Mojang signature for the texture.
     */
    public String getTextureSignature() {
        return textureSignature;
    }

    /**
     * Check if this skin data is valid (has both value and signature).
     */
    public boolean isValid() {
        return textureValue != null && !textureValue.isEmpty()
            && textureSignature != null && !textureSignature.isEmpty();
    }

    @Override
    public String toString() {
        return "SkinData{value=" + (textureValue != null ? textureValue.substring(0, Math.min(20, textureValue.length())) + "..." : "null") + "}";
    }
}
