import javax.imageio.ImageIO; import java.awt.*; import java.awt.image.BufferedImage; import java.io.File; import java.util.*;
/** Tiles the mark-*.png crops of a report folder, enlarged, on one review sheet: MarkSheet <folder> <out.png> */
public class MarkSheet { public static void main(String[] a) throws Exception {
  File dir = new File(a[0]); File[] fs = dir.listFiles((d, n) -> n.startsWith("mark-")); Arrays.sort(fs);
  int cols = 5, cw = 380, ch = 230, rows = (fs.length + cols - 1) / cols;
  BufferedImage out = new BufferedImage(cols * cw, rows * ch, BufferedImage.TYPE_INT_RGB); Graphics2D g = out.createGraphics();
  g.setColor(Color.WHITE); g.fillRect(0, 0, out.getWidth(), out.getHeight()); g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
  for (int i = 0; i < fs.length; i++) { BufferedImage im = ImageIO.read(fs[i]); int x = (i % cols) * cw, y = (i / cols) * ch;
    double sc = Math.min(3.0, Math.min((cw - 10.0) / im.getWidth(), (ch - 30.0) / im.getHeight()));
    g.drawImage(im, x + 5, y + 25, (int) (im.getWidth() * sc), (int) (im.getHeight() * sc), null);
    g.setColor(Color.BLACK); g.setFont(new Font("SansSerif", Font.PLAIN, 14)); g.drawString(fs[i].getName().replace(".png", ""), x + 5, y + 16);
    g.setColor(Color.LIGHT_GRAY); g.drawRect(x, y, cw - 1, ch - 1); }
  ImageIO.write(out, "png", new File(a[1])); } }
