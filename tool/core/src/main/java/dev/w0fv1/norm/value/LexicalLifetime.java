package dev.w0fv1.norm.value;

import java.util.Objects;

public final class LexicalLifetime {
  private static final LexicalLifetime LONG_LIVED = new LexicalLifetime(null, true);
  private static final LexicalLifetime UNUSABLE = new LexicalLifetime(null, false);

  private final Region region;
  private final boolean valid;

  private LexicalLifetime(Region region, boolean valid) {
    this.region = region;
    this.valid = valid;
  }

  public static LexicalLifetime longLived() {
    return LONG_LIVED;
  }

  public static LexicalLifetime unusable() {
    return UNUSABLE;
  }

  public boolean outlives(LexicalLifetime other) {
    Objects.requireNonNull(other, "other");
    if (!valid || !other.valid) return false;
    if (region == null) return true;
    for (Region candidate = other.region; candidate != null; candidate = candidate.parent) {
      if (candidate == region) return true;
    }
    return false;
  }

  public LexicalLifetime narrowest(LexicalLifetime other) {
    Objects.requireNonNull(other, "other");
    if (outlives(other)) return other;
    if (other.outlives(this)) return this;
    return UNUSABLE;
  }

  public static final class Region {
    private final Region parent;
    private final LexicalLifetime lifetime;

    private Region(Region parent) {
      this.parent = parent;
      lifetime = new LexicalLifetime(this, true);
    }

    public static Region root() {
      return new Region(null);
    }

    public Region child() {
      return new Region(this);
    }

    public LexicalLifetime lifetime() {
      return lifetime;
    }
  }
}
