package Hardware.Video.HGA;

import Hardware.HardwareComponent;
import Hardware.Video.GraphicsCard;
import Hardware.Video.GraphicsCardListener;
import IOMap.IOReadable;
import IOMap.IOWritable;
import Scheduler.Schedulable;
import Scheduler.Scheduler;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Random;

public class HGAAdapter extends GraphicsCard
    implements Schedulable,
    IOReadable,
    IOWritable {

  /* ----------------------------------------------------- *
   * CRT Controller                                        *
   * ----------------------------------------------------- */
  protected final CRTC6845 m_crtc;

  /* ----------------------------------------------------- *
   * Scheduling                                            *
   * ----------------------------------------------------- */
  private float m_cpuFrequency;
  private boolean m_isLineOnScreen;
  private int m_cyclesRemaining;
  private int m_cyclesLineOnScreen;
  private int m_cyclesLineOffScreen;

  /* ----------------------------------------------------- *
   * Status Register                                       *
   * ----------------------------------------------------- */
  //MDA: Only bits 0 and 3 are used in the MDA status register; all the other bits must contain the value

  //MDA: Horizontal synchronization signal: 0 = off; 1 = on
  //HGA: Horizontal synchronization signal: 0 = off; 1 = on
  protected final int STAT_HORIZONTAL_SYNC_SIGNAL = 0b00000001;

  //MDA: Pixel status: 0 = current pixel off; 1 = current pixel on
  //HGA: Current pixel: 0 = off; 1 = on
  protected final int STAT_PIXEL_STATUS = 0b00001000;

  //HGA: this bit always contains a 0 when the 6845 sends a vertical synchronization signal to the screen, to generate a new screen structure.
  //HGA: Vertical synchronization signal: 0 = off; 1 = on
  protected final int STAT_VERTICAL_SYNC_SIGNAL = 0b10000000;
  protected int m_status;


  /* ----------------------------------------------------- *
   * Current scanline                                      *
   * ----------------------------------------------------- */
  private int m_screenScanline;
  private int m_charScanline;


  /* ----------------------------------------------------- *
   * Rendering                                             *
   * ----------------------------------------------------- */
  private final LinkedList<HGARenderer> m_renderer;
  private HGARenderer m_currentRenderer;
  private HGARenderer m_blankRenderer;
  protected int m_vramAddr;
  protected int m_pixelShift;


  private static final int REG_CRTC_INDEX = 0x3B4; // aka address register
  private static final int REG_CRTC_DATA = 0x3B5;
  //MDA: write only
  //MDA: The normal value fort this register is 29H, which indicates that all three relevant bits default to 1.
  private static final int REG_CONTROL = 0x3B8;
  //MDA: read only
  private static final int REG_STATUS = 0x03BA;
//  private static final int REG_STATUS2 = -0x03DA;
  //HGA: You can only write to this register; if you try to read this register, the value FFH is returned.
  private static final int REG_CONFIG = 0x3BF;



  private static final int BLACK = 0xff000000;
  private static final int WHITE = 0xffffffff;


  private final HGARam m_vram;

  private final HGAFont m_font;

  private boolean m_canEnableGraphicsMode;
  private boolean m_secondScreenPageAvailable;
  private boolean m_graphicsMode;
  private boolean m_screenOn;
  private boolean m_blinking;
  private boolean m_screenPage;
  private boolean m_horizontalSyncSignal;
  private boolean m_verticalSyncSignal;
  private boolean m_pixelStatus;

  public HGAAdapter(GraphicsCardListener listener) {
    super(listener);
    // Initialize video ram
    m_vram = new HGARam();

    // Initialize registers
    m_crtc = new CRTC6845();

    // Initialize renderer
    m_renderer = new LinkedList<>();
    m_renderer.add(m_blankRenderer = new HGAAdapter.HGABlankRenderer());
    m_renderer.add(new HGAAdapter.HGATextRenderer());
    m_renderer.add(new HGAAdapter.HGAGraphicRenderer());

    // Initialize font bank
    m_font = new HGAFont("data/roms/hga/IBM-MDA.rom");
  }

  @Override
  public int[] getReadableIOPorts() {
    return new int[] {
        REG_CRTC_INDEX, REG_CRTC_DATA, REG_STATUS, REG_CONTROL
    };
  }

  @Override
  public int[] getWritableIOPorts() {
    return new int[] {
        REG_CONTROL, REG_CRTC_INDEX, REG_CRTC_DATA, REG_CONFIG
    };
  }

  @Override
  public ArrayList<HardwareComponent> getSubComponents() {

    ArrayList<HardwareComponent> subComponents = new ArrayList<>();
    subComponents.add(m_vram);

    return subComponents;
  }

  @Override
  public int readIO8(int port) {
    switch (port) {
      case REG_CONTROL:
        //HGA: if you try to read this register, the value FFH is returned.
        return 0xFF;
      case REG_STATUS:
        return m_status;
      case REG_CRTC_INDEX:
        return m_crtc.getIndex();
      case REG_CRTC_DATA:
        return m_crtc.getData();
      default:
        System.out.printf("HGAAdapter: Unknown port read: %04X\n", port);

    }
    return 0;
  }

  @Override
  public void writeIO8(int port, int data) {
    switch (port) {
      case REG_CONFIG:
        //HGA: Bit 0 specifies whether graphic mode is enabled (1) or disabled(0).
        //HGA: Can graphics mode be enabled?: 0 = no; 1 = yes
        m_canEnableGraphicsMode = (0b00000001 & data) == 0b00000001;

        //HGA: Bit 1 specifies whether the second screen page is available (0 if not, 1 ifso).
        //HGA: Access to second screen page possible?: 0 = no; 1 = yes
        m_secondScreenPageAvailable = (0b00000010 & data) == 0b00000010;
        updateRenderer();
        break;
      case REG_CONTROL:
        //MDA: Bit 0 controls the resolution on the card. Although the card only supports one resolution (80x25 characters), this bit must be set to 1 during system initialization. Otherwise, the computer goes into an infinite wait loop.
        //MDA: Always 1
        //HGA: Unlike  the  IBM  monochrome  display adapter, bit 0 is unused and doesn't have to be  set  to  1  during  the  system  boot.

        //HGA: Bit  1 determines text or graphics mode: 0 enables text mode or 1 enables graphics mode.
        //HGA: Display mode: 0 = text mode; 1 = graphics mode
        m_graphicsMode = (0b00000010 & data) > 0;

        //MDA: if bit 3 is set to 0, the screenis black and the blinking cursor disappears. If bit 3 is set to 1, the display returns to the screen
        //MDA: Screen status: 0 = screen of; 1 = screen on
        //HGA: Screen status: 0 = screen of; 1 = screen on
        m_screenOn = (0b00001000 & data) > 0;

        //MDA: Bit 5 has a similar function (as bit 3)
        //MDA: If bit 7 in the attribute byte of the character is set to 1, it enables blinking characters. If bit  7  contains  the  value  0,  the  character appears,  unblinking,  in  front  of  a  light background color.
        //MDA: Bit 7 (see somewhere else the attribute byte) of the attribute byte: 0 = high intensity; 1 = blinking
        //HGA: Blinking: 0 = disabled; 1 = enabled
        m_blinking = (0b00100000 & data) > 0;

        //HGA: If this bit is 0, the first screen page appears and if it is 1, the second screen page appears.
        //HGA: Screen page: 0 = screen page 1; 1 = screen page 2
        m_screenPage = (0b10000000 & data) > 0;
        updateRenderer();
        break;
      case REG_CRTC_INDEX:
        m_crtc.setIndex(data);
        break;
      case REG_CRTC_DATA:
        m_crtc.setData(data, this::updateTimings);
        break;
      default:
        throw new IllegalArgumentException(String.format("Illegal access while writing %02xh to port %04xh", data, port));
    }
  }

  private void updateTimings() {

    int charWidth = m_graphicsMode ? 16 : 9;

    // Calculate number of cycles for a whole line (on- and offscreen)
    float charClock = (m_cpuFrequency / getHGAClockFrequency()) * charWidth;
    float cyclesLineOnScreen = charClock * m_crtc.getHorizontalTotalChars();
    float cyclesLineOffScreen = charClock * (m_crtc.getHorizontalTotalChars() - m_crtc.getDisplayedCharsPerLine());

    // Set new resolution
    int width = m_crtc.getDisplayedCharsPerLine() * charWidth;
    int height = m_crtc.getNumberOfDisplayedCharRows() * m_crtc.getNumberOfCharScanLines();
    setResolution(width, height);

    // Find a renderer that can render line in the current operating mode
    updateRenderer();

    // Update timings
    m_cyclesLineOnScreen = Scheduler.toFixedPoint(cyclesLineOnScreen);
    m_cyclesLineOffScreen = Scheduler.toFixedPoint(cyclesLineOffScreen);


        int verticalTotal = 364; //m_crtc.getNumberOfTotalCharRow();
        float horizontalRefreshRate = getHGAClockFrequency() / (m_crtc.getHorizontalTotalChars() * charWidth);
        float verticalRefreshRate = horizontalRefreshRate / verticalTotal;
/*
        System.out.println("HGAAdapter::updateTimings():");
        // 18.141 kHz and 49.84 Hz text
        // 18.519 kHz and 50.32 Hz graphics
        System.out.printf("  Resolution ...........: %dx%d [h:%f Hz, v:%f Hz]\n", m_frameWidth, m_frameHeight, horizontalRefreshRate, verticalRefreshRate);
        System.out.printf("  Renderer .............: %s\n", m_currentRenderer.getClass().getName());
        System.out.printf("  CPU Clock ............: %.2f MHz\n", m_cpuFrequency / 1000000.0f);
        System.out.printf("  HGA Clock ............: %.2f MHz\n", getHGAClockFrequency() / 1000000.0f);
        System.out.printf("  Char Clock ...........: %f Hz\n", charClock);
        System.out.printf("  Timing \"On Screen\" ...: %f cycles\n", cyclesLineOnScreen);
        System.out.printf("  Timing \"Off Screen\" ..: %f cycles\n", cyclesLineOffScreen);
        System.out.printf("  HDispEnd .............: %d characters\n", m_crtc.getDisplayedCharsPerLine());
        System.out.printf("  HTotal ...............: %d characters\n", m_crtc.getHorizontalTotalChars());
        System.out.printf("  VDispEnd .............: %d scanlines\n", m_crtc.getNumberOfDisplayedCharRows() * m_crtc.getNumberOfCharScanLines());
        System.out.printf("  VTotal ...............: %d scanlines\n", m_crtc.getNumberOfTotalCharRow() * m_crtc.getNumberOfCharScanLines());
//        System.out.printf("  VRetrace .............: %d scanline (ends on %d)\n", m_crtc.getVerticalRetraceStart(), m_crtc.getVerticalRetraceEnd());
        System.out.printf("  Num Char Scanline ....: %d scanlines\n", m_crtc.getNumberOfCharScanLines());
        System.out.println();
*/
  }

  private void updateRenderer() {
// Set the current renderer to the blank renderer
    m_currentRenderer = m_blankRenderer;

    // Try to find a renderer that can draw a line in the current operating mode
    for(HGARenderer renderer : m_renderer) {

      if(renderer.isSuitableRenderer()) {

        m_currentRenderer = renderer;
        break;
      }
    }
  }

  private float getHGAClockFrequency() {
    if(m_graphicsMode) {
//      return 13072834.08f; TODO: Graphics mode not yet adjusted
      return 16000416f;
    } else {
      return 16000362f;
    }
  }

  @Override
  public void setBaseFrequency(float frequency) {
    m_cpuFrequency = frequency;
    updateTimings();
  }

  private static String binary(int data, int digits) {
    return String.format("%" + digits + "s", Integer.toBinaryString(data)).replace(' ', '0');
  }

  private void setStatus(int flag, boolean val) {
    if(val) {
      m_status |= flag;
    } else {
      m_status &= ~flag;
    }
  }

  @Override
  public void updateClock(int cycles) {
    m_cyclesRemaining -= cycles;

    if(new Random().nextBoolean()) {
      m_status &= 0b11111110;
    } else {
      m_status |= 0x00000001;
    }

    if(new Random().nextBoolean()) {
      m_status &= 0b01111111;
    } else {
      m_status |= 0x10000000;
    }

    while(m_cyclesRemaining <= 0) {

      if(m_isLineOnScreen) {
        m_isLineOnScreen = false;
        m_cyclesRemaining += m_cyclesLineOffScreen;


        // Draw current scanline
        if(m_screenScanline < m_frameHeight)
          m_currentRenderer.drawLine(m_screenScanline * m_frameWidth);


      }
      else {

        m_isLineOnScreen = true;
        m_cyclesRemaining += m_cyclesLineOnScreen;


        // Update status register and vram address of the next scanline
        if(m_screenScanline < m_frameHeight) {

            if(m_charScanline >= m_crtc.getNumberOfCharScanLines() - 1) {
              m_charScanline = 0;
              m_vramAddr = m_crtc.getOutputAddress();
            }
            else {
              m_charScanline = (m_charScanline + 1) & 0x1f;
            }
        }

        // Advance to the next scanline on the screen
        m_screenScanline = (m_screenScanline + 1) & 0xfff;

        setStatus(STAT_VERTICAL_SYNC_SIGNAL, true);
        if(m_screenScanline >= m_crtc.getNumberOfDisplayedCharRows() * m_crtc.getNumberOfCharScanLines()) {
          m_vramAddr = m_crtc.getOutputAddress();
          setStatus(STAT_VERTICAL_SYNC_SIGNAL, false);
          drawOutput();
        }
        if(m_screenScanline >= m_crtc.getNumberOfTotalCharRow() * m_crtc.getNumberOfCharScanLines()) {
          m_screenScanline = 0;
          m_charScanline = 0;
          m_vramAddr = m_crtc.getOutputAddress();
        }
      }

      setStatus(STAT_HORIZONTAL_SYNC_SIGNAL, m_isLineOnScreen);

    }
  }

  @Override
  public void reset() {
    // Reset CRTC
    m_crtc.reset();

    // Reset STATUS
    m_status = 0b11110110;

    // Reset some cached values
    m_cyclesLineOnScreen = m_cyclesLineOffScreen = 32768;
    m_cyclesRemaining = m_cyclesLineOnScreen;
    m_screenScanline = m_charScanline = 0;
    m_vramAddr = 0;
    m_isLineOnScreen = true;
  }

  private final class HGABlankRenderer implements HGARenderer {
    @Override
    public boolean isSuitableRenderer() {
      return ! m_screenOn;
    }

    @Override
    public void drawLine(int offset) {
      for(int x = 0; x < m_frameWidth; x++, offset++)
        m_frameData[offset] = BLACK;
    }
  }

  public final class HGATextRenderer implements HGARenderer {
    @Override
    public boolean isSuitableRenderer() {
      return !m_graphicsMode;
    }

    @Override
    public void drawLine(int offset) {
      int charWidth = 9;
      int textLine = offset / (14*720);
      int addr = m_vramAddr + textLine * 160;


      for(int x = 0; x < m_frameWidth; x += charWidth, addr+=2, offset += charWidth) {
        int chr = m_vram.getData(addr);
        int att = m_vram.getData(addr+ 1);
        int fnt = m_font.getLine(chr, m_charScanline);

        AttrColor attrColor = AttrColor.fromAttr(att);

        CRTC6845.CursorDisplayMode cursorDisplayMode = m_crtc.getCursorDisplayMode();
        boolean isCursorVisible;
        switch (cursorDisplayMode) {
          case NON_BLINK:
            isCursorVisible =
                m_charScanline >= m_crtc.getCursorScanStartLine() &&
                m_charScanline <= m_crtc.getCursorScanEndLine() &&
                    (m_frameNumber & 0x7f) >= 0x40; //TODO: should it be here ?
            ;
            break;
          case BLINK_1_16_FIELD_RATE:
            isCursorVisible =
                m_charScanline >= m_crtc.getCursorScanStartLine() &&
                m_charScanline <= m_crtc.getCursorScanEndLine() &&
                (m_frameNumber & 0x7f) >= 0x40;
            break;
          case BLINK_1_32_FIELD_RATE:
            isCursorVisible =
                m_charScanline >= m_crtc.getCursorScanStartLine() &&
                m_charScanline <= m_crtc.getCursorScanEndLine() &&
                (m_frameNumber & 0x3f) >= 0x20;
            break;
          case CURSOR_NON_DISPLAY:
          default:
            isCursorVisible = false;
            break;
        }

        boolean showCursor = isCursorVisible && m_crtc.getCursorPosition() == addr/ 2;

        for(int cx = 0; cx < charWidth; cx++) {
          if((attrColor.underline && m_charScanline == 13) || showCursor) {
            m_frameData[offset + cx] = attrColor.fgcolor;
            continue;
          }

          if(cx < 8) {
            m_frameData[offset + cx] = ((fnt & (0x80 >> cx)) != 0) ? attrColor.fgcolor : attrColor.bgcolor;
          } else {
            if(chr >= 0xb0 && chr <= 0xdf) { // drawing characters expand to 9th column
              m_frameData[offset + cx] = ((fnt & (0x80 >> (cx - 1))) != 0) ? attrColor.fgcolor : attrColor.bgcolor;
            } else {
              m_frameData[offset + cx] = attrColor.bgcolor;
            }
          }
        }
      }
    }
  }

  private static class AttrColor {
    int fgcolor;
    int bgcolor;
    boolean blinker;
    boolean underline;
    boolean bold;

    private static AttrColor fromAttr(int attr) {
      AttrColor attrColor = new AttrColor();

      attrColor.blinker = (attr & 0b10000000) > 0;
      attrColor.bold = (attr & 0b00001000) > 0;

      attrColor.bgcolor = ((attr & 0b01110000) == 0) ? BLACK : WHITE;
      attrColor.underline = (attr & 0b01110111) == 0b00000001;
      attrColor.fgcolor = ((attr & 0b00000111) == 0b00000111 ? WHITE : BLACK);
      return attrColor;
    }
  }

  public final class HGAGraphicRenderer implements HGARenderer {
    @Override
    public boolean isSuitableRenderer() {
      return m_graphicsMode;
    }

    @Override
    public void drawLine(int offset) {

      int pageOffset = m_screenPage ? 0x0 : 0x8000;

      int y = offset / m_frameWidth;
      int memBlockStart = (y % 4) * 0x2000;
      int memLineStart = memBlockStart + 90 * (y / 4);   //90 = 720/8bit


      for(int cx = 0; cx < 90; cx++) {
        int data = m_vram.getData(pageOffset + memLineStart + cx);
        for(int bit = 0; bit < 8; bit++) {
          m_frameData[offset + cx] = ((0x80 >> bit) & data) == 0 ? BLACK : WHITE;
        }
      }
    }
  }
}
