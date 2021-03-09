package Hardware.Video.HGA;

import java.util.Arrays;

public class CRTC6845 {
  //Total horizontal character
  public static final int ADDR_CHAR_HORIZONTAL_TOTAL = 0x00;
  //Horizontal character displayed
  public static final int ADDR_CHAR_HORIZONTAL_DISPLAYED = 0x01;
  //Horiz. synchronization signal after character
  public static final int ADDR_CHAR_H_SYNC_POSITION = 0x02;
  //Horiz. synchronization signal width
  public static final int ADDR_SYNC_WIDTH = 0x03;
  //Total vertical character
  public static final int ADDR_CHAR_ROW_VERTICAL_TOTAL = 0x04;
  //Vertical character justified
  public static final int ADDR_SCAN_LINE_V_TOTAL_ADJUST = 0x05;
  //Vertical character displayed
  public static final int ADDR_CHAR_ROW_VERTICAL_DISPLAYED = 0x06;
  //Vert. synchronization signal after character
  public static final int ADDR_CHAR_ROW_V_SYNC_POSITION = 0x07;
  //Interlace mode
  public static final int ADDR_INTERLACE_MODE_AND_SKEW = 0x08;
  //Number of scan lines per line
  public static final int ADDR_SCAN_LINE_MAX_SCAN_LINE_ADDRESS = 0x09;
  //Starting line of blinking cursor
  public static final int ADDR_SCAN_LINE_CURSOR_START = 0x0A;
  //Ending line of the blinking cursors
  public static final int ADDR_SCAN_LINE_CURSOR_END = 0x0B;
  //High byte of screen page starting address
  public static final int ADDR_START_ADDRESS_H = 0x0C;
  //Low byte of screen page starting address
  public static final int ADDR_START_ADDRESS_L = 0x0D;
  //High byte of blinking cursor char. address
  public static final int ADDR_CURSOR_H = 0x0E;
  //Low byte of blinking cursor char. address
  public static final int ADDR_CURSOR_L = 0x0F;
  //Light pen position (high byte)
  public static final int ADDR_LIGHT_PEN_H = 0x10;
  //Light pen position (low byte)
  public static final int ADDR_LIGHT_PEN_L = 0x11;


  private final int[] m_crtc;
  private int m_crtcIndex;

  private int m_cyclesLineOnScreen;
  private int m_cyclesLineOffScreen;


  public CRTC6845() {
    m_crtc = new int[64];
  }

  public void reset() {
    Arrays.fill(m_crtc, 0x00);
    m_crtc[ADDR_CHAR_ROW_VERTICAL_DISPLAYED] = 0xff;
    m_crtcIndex = ADDR_CHAR_HORIZONTAL_TOTAL;
  }

  public int getData() {
    return getData(m_crtcIndex);
  }

  public int getData(int index) {
    return m_crtc[index];
  }

  public int getIndex() {
    return m_crtcIndex;
  }

  public void setIndex(int crtcIndex) {
    this.m_crtcIndex = crtcIndex;
  }

  public void setData(int data, Runnable updateTimings) {
    int oldData = m_crtc[m_crtcIndex];
    if(oldData != data) {

      if(((m_crtc[ADDR_LIGHT_PEN_L] & 0x80) != 0) && m_crtcIndex <= 7) {

        if(m_crtcIndex == ADDR_CHAR_ROW_V_SYNC_POSITION) {
          m_crtc[ADDR_CHAR_ROW_V_SYNC_POSITION] = (m_crtc[ADDR_CHAR_ROW_V_SYNC_POSITION] & ~0x10) | (data & 0x10);
        }
      }
      else {
        m_crtc[m_crtcIndex] = data;
      }

      updateCRTC(m_crtcIndex, oldData ^ m_crtc[m_crtcIndex], updateTimings);
    }
  }

  private void updateCRTC(int regIndex, int changedData, Runnable updateTimings) {
    if(updateTimings != null) {
      updateTimings.run();
    }
  }

  public int getHorizontalTotalChars() { // 97 + 1 = 98  | gr: 53 + 1 = 54
    return (m_crtc[ADDR_CHAR_HORIZONTAL_TOTAL] & 0xFF)+ 1;
  }

  public int getDisplayedCharsPerLine() { // 80 | gr: 45
    return m_crtc[ADDR_CHAR_HORIZONTAL_DISPLAYED] & 0xFF;
  }

  public int getHorizontalSyncPos() { // 82  | gr: 46
    return m_crtc[ADDR_CHAR_H_SYNC_POSITION] & 0xFF;
  }

  public int getHorizontalSyncPulseWidth() { // 15 | gr: 7
    return m_crtc[ADDR_SYNC_WIDTH] & 0b00001111;
  }

  public int getNumberOfTotalCharRow() { // 25 + 1 = 26 | gr: 91 + 1 = 92
    return (m_crtc[ADDR_CHAR_ROW_VERTICAL_TOTAL] & 0b01111111) + 1;
  }

  // Fraction of character line times
  public int getNumberOfScanLinesRequired() { // 6 | gr: 2
    return m_crtc[ADDR_SCAN_LINE_V_TOTAL_ADJUST] & 0b00011111;
  }

  public int getNumberOfDisplayedCharRows() { // 25 | gr: 87
    return m_crtc[ADDR_CHAR_ROW_VERTICAL_DISPLAYED] & 0b01111111;
  }

  public int getVerticalSyncPos() { // 25 | gr: 87
    return m_crtc[ADDR_CHAR_ROW_V_SYNC_POSITION] & 0b01111111;
  }

  public InterlaceMode getInterlaceMode() { // 0b10 -> NON_INTERLACE | gr: the same
    int code = m_crtc[ADDR_INTERLACE_MODE_AND_SKEW] & 0b11;
    return InterlaceMode.byCode(code);
  }

  public int getNumberOfCharScanLines() { // 13+1 = 14 | gr: 3+1=4
    return m_crtc[ADDR_SCAN_LINE_MAX_SCAN_LINE_ADDRESS] + 1;
  }

  public CursorDisplayMode getCursorDisplayMode() { // 00 -> NON_BLINK
    int code = (m_crtc[ADDR_SCAN_LINE_CURSOR_START] & 0b01100000) >> 5;
    return CursorDisplayMode.byCode(code);
  }

  public int getCursorScanStartLine() { //11
    return m_crtc[ADDR_SCAN_LINE_CURSOR_START] & 0b00011111;
  }

  public int getCursorScanEndLine() { //12
    return m_crtc[ADDR_SCAN_LINE_CURSOR_END] & 0b00011111;
  }

  public int getCursorPosition() {
    return
        ((m_crtc[ADDR_CURSOR_H] & 0b00111111) << 8) |
            (m_crtc[ADDR_CURSOR_L] & 0b11111111);
  }

  public int getOutputAddress() { // 0
    return ((m_crtc[ADDR_START_ADDRESS_H] & 0b00111111) << 8) |
        (m_crtc[ADDR_START_ADDRESS_L] & 0b11111111);
  }



  public String toString() {
    StringBuilder sb = new StringBuilder();
    strApp(sb, "Horizontal Total", ADDR_CHAR_HORIZONTAL_TOTAL);
    strApp(sb, "Horizontal Displayed", ADDR_CHAR_HORIZONTAL_DISPLAYED);
    strApp(sb, "H. Sync Position", ADDR_CHAR_H_SYNC_POSITION);
    strApp(sb, "Sync Width", ADDR_SYNC_WIDTH);
    strApp(sb, "Vertical Total", ADDR_CHAR_ROW_VERTICAL_TOTAL);
    strApp(sb, "V. Total Adjust", ADDR_SCAN_LINE_V_TOTAL_ADJUST);
    strApp(sb, "Vertical Displayed", ADDR_CHAR_ROW_VERTICAL_DISPLAYED);
    strApp(sb, "V. Sync Position", ADDR_CHAR_ROW_V_SYNC_POSITION);
    strApp(sb, "Interlace Mode and Skew", ADDR_INTERLACE_MODE_AND_SKEW);
    strApp(sb, "Max Scan Line Address", ADDR_SCAN_LINE_MAX_SCAN_LINE_ADDRESS);
    strApp(sb, "Cursor Start", ADDR_SCAN_LINE_CURSOR_START);
    strApp(sb, "Cursor End", ADDR_SCAN_LINE_CURSOR_END);
    strApp(sb, "Start Address (H)", ADDR_START_ADDRESS_H);
    strApp(sb, "Start Address (L)", ADDR_START_ADDRESS_L);
    strApp(sb, "Cursor (H)", ADDR_CURSOR_H);
    strApp(sb, "Cursor (L)", ADDR_CURSOR_L);
    strApp(sb, "Light Pen (H)", ADDR_LIGHT_PEN_H);
    strApp(sb, "Light Pen (L)", ADDR_LIGHT_PEN_L);
    return sb.toString();
  }

  private void strApp(StringBuilder sb, String name, int addr) {
    sb.append(name).append("=").append(m_crtc[addr]).append('\n');
  }

  public enum CursorDisplayMode {
    NON_BLINK(0b00),
    CURSOR_NON_DISPLAY(0b01),
    BLINK_1_16_FIELD_RATE(0b10),
    BLINK_1_32_FIELD_RATE(0b11);

    private final int code;

    CursorDisplayMode(int code) {
      this.code = code;
    }

    public static CursorDisplayMode byCode(int code) {
      for(CursorDisplayMode mode : values()) {
        if(code == mode.code) {
          return mode;
        }
      }
      return null;
    }
  }

  public enum InterlaceMode {
    NON_INTERLACE(0b00, 0b10),
    INTERLACE_SYNC(0b01),
    INTERLACE_SYNC_AND_VIDEO(0b11);

    private final int[] codes;

    InterlaceMode(int... codes) {
      this.codes = codes;
    }
    
    public static InterlaceMode byCode(int code) {
      for(InterlaceMode mode : values()) {
        if(Arrays.stream(mode.codes).anyMatch(value -> value == code)) {
          return mode;
        }
      }
      return null;
    }

  }
}
