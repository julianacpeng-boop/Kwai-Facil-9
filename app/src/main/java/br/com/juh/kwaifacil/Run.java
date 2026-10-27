package br.com.juh.kwaifacil;
final class Run {
 static volatile boolean active=false, auto=false, sent=false;
 static String caption="", target="", status="Pronto para preparar uma publicação.";
 static double coverSeconds=-1,coverDuration=0; static boolean coverDone=true; static int coverStage=0; static long coverAction;
 static long started,sentAt; static int advances=0; static boolean filled=false;
 static void stop(String text) { active=false; status=text; }
}
