package Hardware.Video.HGA;

import Hardware.HardwareComponent;
import MemoryMap.MemoryReadable;
import MemoryMap.MemoryWritable;

public final class HGARam implements HardwareComponent,
    MemoryReadable,
    MemoryWritable {

  /* ----------------------------------------------------- *
   * RAM data                                              *
   * ----------------------------------------------------- */
  private final int[] m_ram = new int[0x10000]; //64k
  private final int m_ramMask = 0xffff;
  private boolean m_onlyScreenPageDIP = false;

  @Override
  public int[][] getReadableMemoryAddresses() {
    // { startAddress, size, offset }
    if(m_onlyScreenPageDIP) {
      return new int[][]{new int[]{0xb0000, 0x08000, 0x00000}};
    } else {
      return new int[][]{new int[]{0xb0000, 0x10000, 0x00000}};
    }
  }

  @Override
  public int readMEM8(int address) {
    return m_ram[address];
  }

  @Override
  public int readMEM16(int address) {
    return readMEM8(address) |
        (readMEM8(address + 1) << 8);
  }

  @Override
  public int readMEM32(int address) {
    return readMEM8(address) |
        (readMEM8(address + 1) << 8) |
        (readMEM8(address + 2) << 16) |
        (readMEM8(address + 3) << 24);
  }

  @Override
  public int[][] getWritableMemoryAddresses() {
    return new int[][] { new int[] { 0xb0000, 0x10000, 0x00000 } };
  }

  @Override
  public void writeMEM8(int address, int data) {
    m_ram[address & m_ramMask] = data;
  }


  StringBuilder[] scrn = new StringBuilder[26];

  @Override
  public void writeMEM16(int address, int data) {
    writeMEM8(address, data & 0xff);
    writeMEM8(address + 1, (data >>> 8) & 0xff);
  }

  @Override
  public void writeMEM32(int address, int data) {
    writeMEM8(address, data & 0xff);
    writeMEM8(address + 1, (data >>> 8) & 0xff);
    writeMEM8(address + 2, (data >>> 16) & 0xff);
    writeMEM8(address + 3, (data >>> 24) & 0xff);
  }

  /**
   * DIP
   */
  public void setOnlyScreenPage(boolean onlyScreenPage) {
    m_onlyScreenPageDIP = onlyScreenPage;
  }

  private void setData(int address, int data) {
    m_ram[address & m_ramMask] = data;
  }

  public int getData(int address) {
    return m_ram[address & m_ramMask];
  }

  @Override
  public void reset() {
  }

}
