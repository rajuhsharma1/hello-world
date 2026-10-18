package com.spa.operations;

import java.io.*;
import java.util.*;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.PDType0Font;

public final class Documents {

  private Documents() {}

  public static String csvCell(Object value) {
    String s = value == null ? "" : value.toString();
    String trimmed = s.stripLeading();
    if (
      !trimmed.isEmpty() &&
      ("=+-@".indexOf(trimmed.charAt(0)) >= 0 ||
        s.startsWith("\t") ||
        s.startsWith("\r") ||
        s.startsWith("\n"))
    ) s = "'" + s;
    return "\"" + s.replace("\"", "\"\"") + "\"";
  }

  public static byte[] pdf(String title, List<String> paragraphs) {
    try (var layout = new Layout(title)) {
      for (String text : paragraphs) {
        if (
          text.startsWith("Employer signature") ||
          text.startsWith("Employee signature") ||
          text.startsWith("Staff acknowledgement") ||
          text.startsWith("Witness /")
        ) layout.signature(text);
        else if (text.startsWith("DRAFT")) layout.callout(text);
        else if (text.contains(":") && text.length() < 160) {
          int i = text.indexOf(':');
          layout.field(text.substring(0, i), text.substring(i + 1).trim());
        } else layout.paragraph(text);
      }
      return layout.finish();
    }
  }

  public static final class Layout implements AutoCloseable {

    private final PDDocument doc = new PDDocument();
    private final PDType0Font font;
    private final org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject logo;
    private final ByteArrayOutputStream output = new ByteArrayOutputStream();
    private PDPageContentStream content;
    private float y;
    private final String title;

    public Layout(String title) {
      this.title = title;
      try {
        font = PDType0Font.load(
          doc,
          Objects.requireNonNull(
            Documents.class.getResourceAsStream("/fonts/DejaVuSans.ttf")
          )
        );
        try (
          var source = Objects.requireNonNull(
            Documents.class.getResourceAsStream("/branding/ark-logo.png")
          )
        ) {
          logo =
            org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject.createFromByteArray(
              doc,
              source.readAllBytes(),
              "Ark logo"
            );
        }
        doc.getDocumentInformation().setTitle(title);
        doc.getDocumentInformation().setAuthor("The Ark Salon & Spa");
        page();
      } catch (IOException e) {
        throw new IllegalStateException(e);
      }
    }

    private void page() throws IOException {
      if (content != null) content.close();
      var page = new PDPage(org.apache.pdfbox.pdmodel.common.PDRectangle.A4);
      doc.addPage(page);
      content = new PDPageContentStream(doc, page);
      rect(0, 750, 596, 92, 8, 31, 53);
      content.drawImage(logo, 42, 765, 190, 66);
      text(360, 803, "PEOPLE & BUSINESS", 8, 190, 211, 221);
      text(360, 786, "Kharar · Punjab · India", 8, 190, 211, 221);
      rect(42, 735, 511, 3, 32, 191, 184);
      y = 710;
      text(42, y, title, 19, 8, 31, 53);
      y -= 26;
      text(42, y, "CONFIDENTIAL  /  MANAGEMENT COPY", 8, 102, 119, 132);
      y -= 28;
    }

    private void ensure(float h) throws IOException {
      if (y - h < 74) page();
    }

    private void rect(float x, float y, float w, float h, int r, int g, int b)
      throws IOException {
      content.setNonStrokingColor(r / 255f, g / 255f, b / 255f);
      content.addRect(x, y, w, h);
      content.fill();
    }

    private void text(
      float x,
      float y,
      String value,
      float size,
      int r,
      int g,
      int b
    ) throws IOException {
      content.beginText();
      content.setFont(font, size);
      content.setNonStrokingColor(r / 255f, g / 255f, b / 255f);
      content.newLineAtOffset(x, y);
      try {
        content.showText(value);
      } catch (IllegalArgumentException e) {
        throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.BAD_REQUEST,
          "PDF text contains unsupported characters. Use Latin-script text for this English document."
        );
      }
      content.endText();
    }

    private List<String> wrap(String value, float width, float size)
      throws IOException {
      List<String> lines = new ArrayList<>();
      for (String explicit : value.replace("\t", " ").split("\\R", -1)) {
        StringBuilder line = new StringBuilder();
        for (int cp : explicit.codePoints().toArray()) {
          String ch = new String(Character.toChars(cp));
          try {
            font.encode(ch);
          } catch (IllegalArgumentException e) {
            throw new org.springframework.web.server.ResponseStatusException(
              org.springframework.http.HttpStatus.BAD_REQUEST,
              "PDF text contains unsupported characters. Use Latin-script text for this English document."
            );
          }
          if (
            line.length() > 0 &&
            (font.getStringWidth(line + ch) / 1000) * size > width
          ) {
            int at = line.lastIndexOf(" ");
            if (at > 0) {
              lines.add(line.substring(0, at));
              line.delete(0, at + 1);
            } else {
              lines.add(line.toString());
              line.setLength(0);
            }
          }
          line.append(ch);
        }
        lines.add(line.toString());
      }
      return lines;
    }

    public void section(String heading) {
      try {
        ensure(70);
        y -= 8;
        rect(42, y - 8, 511, 26, 232, 241, 245);
        text(53, y, heading.toUpperCase(Locale.ROOT), 10, 8, 54, 76);
        y -= 30;
      } catch (IOException e) {
        throw new IllegalStateException(e);
      }
    }

    public void paragraph(String value) {
      try {
        for (String line : wrap(value, 511, 10)) {
          ensure(16);
          text(42, y, line, 10, 49, 65, 80);
          y -= 15;
        }
        y -= 10;
      } catch (IOException e) {
        throw new IllegalStateException(e);
      }
    }

    public void field(String label, String value) {
      try {
        var lines = wrap(value, 319, 10);
        ensure(Math.min(300, Math.max(27, lines.size() * 15 + 10)));
        text(49, y, label, 9, 83, 106, 121);
        for (String line : lines) {
          ensure(16);
          text(224, y, line, 10, 8, 31, 53);
          y -= 15;
        }
        y -= 4;
        rect(42, y + 2, 511, 0.5f, 224, 233, 237);
        y -= 12;
      } catch (IOException e) {
        throw new IllegalStateException(e);
      }
    }

    public void callout(String value) {
      section("Draft · local legal review required");
      paragraph(value);
    }

    public void signature(String value) {
      try {
        ensure(68);
        y -= 24;
        rect(42, y, 250, 0.6f, 124, 143, 155);
        y -= 17;
        text(42, y, value.replaceAll("_+", ""), 9, 65, 88, 104);
        y -= 17;
      } catch (IOException e) {
        throw new IllegalStateException(e);
      }
    }

    public byte[] finish() {
      try {
        content.close();
        content = null;
        for (int i = 0; i < doc.getNumberOfPages(); i++) {
          try (
            var footer = new PDPageContentStream(
              doc,
              doc.getPage(i),
              PDPageContentStream.AppendMode.APPEND,
              true,
              true
            )
          ) {
            footer.setNonStrokingColor(.72f, .8f, .84f);
            footer.addRect(42, 53, 511, .6f);
            footer.fill();
            footer.beginText();
            footer.setFont(font, 8);
            footer.setNonStrokingColor(.3f, .4f, .48f);
            footer.newLineAtOffset(42, 35);
            footer.showText("The Ark Salon & Spa  |  Private & confidential");
            footer.newLineAtOffset(408, 0);
            footer.showText((i + 1) + " / " + doc.getNumberOfPages());
            footer.endText();
          }
        }
        doc.save(output);
        return output.toByteArray();
      } catch (IOException e) {
        throw new IllegalStateException("Unable to generate PDF", e);
      }
    }

    public void close() {
      try {
        if (content != null) content.close();
        doc.close();
        output.close();
      } catch (IOException e) {
        throw new IllegalStateException(e);
      }
    }
  }
}
