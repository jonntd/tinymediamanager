package org.tinymediamanager.core.mediainfo;

import java.util.Locale;

public enum MediaInfo3D {

  // MI: https://github.com/MediaArea/MediaInfoLib/blob/master/Source/MediaInfo/Multiple/File_Mk.cpp#L554
  // KODI: https://github.com/xbmc/xbmc/blob/master/xbmc/guilib/StereoscopicsManager.cpp#L48
  MONO(""),
  LEFT_RIGHT("Side by Side (left eye first)"),
  RIGHT_LEFT("Side by Side (right eye first)"),
  BOTTOM_TOP("Top-Bottom (right eye first)"),
  TOP_BOTTOM("Top-Bottom (left eye first)"),
  CHECKERBOARD_RL("Checkboard (right eye first)"),
  CHECKERBOARD_LR("Checkboard (left eye first)"),
  ROW_INTERLEAVED_RL("Row Interleaved (right eye first)"),
  ROW_INTERLEAVED_LR("Row Interleaved (left eye first)"),
  COL_INTERLEAVED_RL("Column Interleaved (right eye first)"),
  COL_INTERLEAVED_LR("Column Interleaved (left eye first)"),
  ANAGLYPH_CYAN_RED("Anaglyph (cyan/red)"),
  ANAGLYPH_GREEN_MAGENTA("Anaglyph (green/magenta)"),
  ANAGLYPH_YELLOW_BLUE("Anaglyph (yellow/blue)"), // not in MediaInfo, but in Kodi
  BLOCK_LR("Both Eyes laced in one block (left eye first)"),
  BLOCK_RL("Both Eyes laced in one block (right eye first)");

  private String desc = "";

  MediaInfo3D(String desc) {
    this.desc = desc;
  }

  public String toString() {
    return desc;
  }

  // returns the Kodi friendly name (enum lowercase)
  public String getId() {
    return name().toLowerCase(Locale.ROOT);
  }

  public static MediaInfo3D get3DFrom(String value) {
    if (value == null || value.isBlank()) {
      return MONO;
    }

    for (MediaInfo3D val : values()) {
      if (val.toString().equalsIgnoreCase(value)) { // by description
        return val;
      }
      if (val.name().equalsIgnoreCase(value)) { // by name
        return val;
      }
    }
    return MONO;
  }
}
