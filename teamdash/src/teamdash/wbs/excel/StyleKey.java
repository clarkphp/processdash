/**
 * 
 */
package teamdash.wbs.excel;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;

import org.apache.poi.hssf.usermodel.HSSFCellStyle;
import org.apache.poi.hssf.usermodel.HSSFFont;
import org.apache.poi.hssf.util.HSSFColor;

public class StyleKey {

    private static final short BLACK = HSSFColor.BLACK.index;

    private static final short WHITE = HSSFColor.WHITE.index;

    private static final short RED = HSSFColor.RED.index;

    private static final short BLUE = HSSFColor.BLUE.index;


    short color = BLACK;

    boolean bold = false;

    boolean italic = false;

    short indent = 0;

    short format = 0;

    public void configure(HSSFFont font) {
        if (color != BLACK)
            font.setColor(color);
        if (bold)
            font.setBoldweight(HSSFFont.BOLDWEIGHT_BOLD);
        if (italic)
            font.setItalic(true);
    }

    public void loadFrom(Component comp) {
        setColor(comp.getForeground());
        setFont(comp.getFont());
    }

    public void setColor(Color c) {
        if (c == Color.RED)
            color = RED;
        else if (c == Color.BLUE)
            color = BLUE;
        else if (c == Color.WHITE)
            color = WHITE;
    }

    public void setFont(Font f) {
        bold = (f != null && f.isBold());
        italic = (f != null && f.isItalic());
    }

    public void configure(HSSFCellStyle style) {
        if (indent > 0)
            style.setIndention(indent);
        if (format > 0)
            style.setDataFormat(format);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof StyleKey) {
            StyleKey that = (StyleKey) obj;
            return this.color == that.color && this.bold == that.bold
                    && this.italic == that.italic
                    && this.indent == that.indent
                    && this.format == that.format;
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        int result = color;
        result = result << 1 + (bold ? 1 : 0);
        result = result << 1 + (italic ? 1 : 0);
        result = result << 4 + indent;
        result = result << 7 + format;
        return result;
    }

}
