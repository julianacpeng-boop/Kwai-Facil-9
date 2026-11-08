package br.com.juh.kwaifacil;
final class CaptionSpec {
 final String caption; final String title; final double seconds;
 CaptionSpec(String text) {
  StringBuilder out=new StringBuilder();double value=-1;boolean found=false,titleFound=false;String parsedTitle="";
  for(String line:text.replace("\r\n","\n").replace('\r','\n').split("\n",-1)) {
   String t=line.trim();
   if(t.startsWith("CAPA_SEGUNDOS")) {
    if(found||!t.matches("CAPA_SEGUNDOS\\s*=\\s*[0-9]+([.,][0-9]+)?"))throw new IllegalArgumentException("CAPA_SEGUNDOS inválido ou repetido");
    value=Double.parseDouble(t.substring(t.indexOf('=')+1).trim().replace(',','.'));
    if(Double.isInfinite(value)||value>86400)throw new IllegalArgumentException("Tempo da capa inválido");found=true;
   }else if(t.matches("(?i)titulo\\s*=.*")) {
    if(titleFound)throw new IllegalArgumentException("Título repetido");
    java.util.regex.Matcher m=java.util.regex.Pattern.compile("(?i)^titulo\\s*=\\s*\"(.*)\"$").matcher(t);
    if(!m.matches())throw new IllegalArgumentException("Formato do título inválido; use titulo = \"Seu título\"");
    parsedTitle=m.group(1).replace("\\\"","\"").trim();titleFound=true;
   }else {if(out.length()>0)out.append('\n');out.append(line);}
  }
  title=parsedTitle;caption=out.toString().trim();seconds=value;
 }
}
