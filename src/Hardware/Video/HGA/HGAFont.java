package Hardware.Video.HGA;

import Utility.FileResource;

import java.io.File;
import java.io.IOException;

/**
 * HGA Font ROM
 */
public class HGAFont {

  private final int[] m_font_rom = new int[8192]; //

  public HGAFont(String fontRom) {
    try {
      FileResource.read(m_font_rom, new File(fontRom));
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  /**
   * Returns bites of horizontal line of the character
   * @param ascii ascii code of the character
   * @param line character scan line
   */
  public int getLine(int ascii, int line) {
    int offset = (line >> 3) << 11; //upper or lower part = (line / 8)*(256*8)
    offset += ascii << 3; // begin of character data (ascii*8)
    offset += line & 0x7;   // line selection (line % 8)

    return m_font_rom[offset];
  }
}
