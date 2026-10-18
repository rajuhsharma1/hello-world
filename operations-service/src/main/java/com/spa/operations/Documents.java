package com.spa.operations;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import java.io.*;
import java.util.*;
public final class Documents {
 private Documents() {}
 public static String csvCell(Object value) {
  String s=value==null?"":value.toString();
  String trimmed=s.stripLeading();
  if(!trimmed.isEmpty() && ("=+-@".indexOf(trimmed.charAt(0))>=0 || s.startsWith("\t") || s.startsWith("\r") || s.startsWith("\n"))) s="'"+s;
  return "\""+s.replace("\"","\"\"")+"\"";
 }
 public static byte[] pdf(String title,List<String> paragraphs) {
  try(PDDocument doc=new PDDocument();InputStream stream=Documents.class.getResourceAsStream("/fonts/DejaVuSans.ttf");ByteArrayOutputStream out=new ByteArrayOutputStream()) {
   var font=PDType0Font.load(doc,Objects.requireNonNull(stream));
   List<String> lines=new ArrayList<>();lines.add("THE ARK SALON & SPA");lines.add(title);lines.add("");
   for(String paragraph:paragraphs) {
    for(String explicit:paragraph.split("\\R",-1)) {
     StringBuilder clean=new StringBuilder();
     for(int cp:explicit.codePoints().toArray()) {
      try { font.encode(new String(Character.toChars(cp))); } catch(IllegalArgumentException unsupported) { throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,"PDF text contains unsupported characters. Use Latin-script text for this English document."); }
      clean.appendCodePoint(cp);
     }
     StringBuilder line=new StringBuilder();
     for(int cp:clean.toString().codePoints().toArray()) {
      String character=new String(Character.toChars(cp));
      while(line.length()>0 && font.getStringWidth(line+character)/1000*10>490) {
       int space=line.lastIndexOf(" ");
       if(space>0){lines.add(line.substring(0,space));line.delete(0,space+1);}
       else {lines.add(line.toString());line.setLength(0);}
      }
      line.append(character);
     }
     lines.add(line.toString());
    }
    lines.add("");
   }
   for(int start=0;start<lines.size();start+=46) {
    var page=new PDPage();doc.addPage(page);
    try(var content=new PDPageContentStream(doc,page)) {
     content.beginText();content.setFont(font,10);content.setLeading(15);content.newLineAtOffset(50,740);
     for(int i=start;i<Math.min(start+46,lines.size());i++){content.showText(lines.get(i));content.newLine();}
     content.endText();content.beginText();content.setFont(font,8);content.newLineAtOffset(50,30);content.showText("The Ark | Page "+doc.getNumberOfPages());content.endText();
    }
   }
   doc.save(out);return out.toByteArray();
  }catch(IOException e){throw new IllegalStateException("Unable to generate PDF",e);}
 }
}
