package Main.UI.MenuBar;

import Hardware.Video.GraphicsCard;
import Hardware.Video.GraphicsCardListener;
import Hardware.Video.VGA.TsengET4000.TsengET4000;

import javax.swing.AbstractButton;
import javax.swing.ButtonGroup;
import javax.swing.JMenu;
import javax.swing.JRadioButtonMenuItem;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.function.Function;

public class JPCGraphics extends JMenu {

  private final ButtonGroup m_gfxGrp;
  private final ButtonGroup m_ratioGrp;

  public JPCGraphics() {
    super("Graphics");

    m_gfxGrp = new ButtonGroup();
    m_ratioGrp = new ButtonGroup();

    for(GraphicsSelection gfxSelection : GraphicsSelection.values()) {
      JRadioButtonMenuItem gfxItem = new JRadioButtonMenuItem(gfxSelection.getName());
      add(gfxItem);
      m_gfxGrp.add(gfxItem);
    }
    addSeparator();
  }

  public GraphicsSelection getSelectedGraphics() {
    Enumeration<AbstractButton> selection = m_gfxGrp.getElements();
    while(selection.hasMoreElements()) {
      AbstractButton btn = selection.nextElement();
      if(btn.isSelected()) {
        return GraphicsSelection.fromName(btn.getText());
      }
    }
    return GraphicsSelection.TSENG_ET4000;
  }

  /**
   * Aspect ratio of the display. Useful for displays with not 1:1 sized pixels.
   */
  public enum GraphicsRatio {
    _NATIVE("Native"),
    _4_3("4:3"),
    ;

    private String name;

    GraphicsRatio(String name) {

      this.name = name;
    }
  }

  public enum GraphicsPreso {
    CLEAR("Clear"),
    INTERLACED("Interlaced");

    private String name;

    GraphicsPreso(String name) {

      this.name = name;
    }
  }

  /**
   * Filter for display output. Usable for monochormatic graphic adapters
   */
  public enum ScreenFilter {
    WHITE("White"),
    GREEN("Green"),
    AMBER("Amber"),
    ;

    private String name;

    ScreenFilter(String name) {

      this.name = name;
    }
  }

  /**
   * Available Graphics adapters
   */
  public enum GraphicsSelection {
    TSENG_ET4000("Tseng Labs ET4000", TsengET4000::new);

    private final String name;
    private final Function<GraphicsCardListener, GraphicsCard> creator;

    GraphicsSelection(String name, Function<GraphicsCardListener, GraphicsCard> creator) {
      this.name = name;
      this.creator = creator;
    }
    public String getName() {
      return name;
    }

    public GraphicsCard create(GraphicsCardListener gfxListener) {
      return creator.apply(gfxListener);
    }

    public static GraphicsSelection fromName(String name) {
      return Arrays.stream(values())
          .filter(x -> x.name.equals(name))
          .findFirst().orElse(null);
    }

  }
}
