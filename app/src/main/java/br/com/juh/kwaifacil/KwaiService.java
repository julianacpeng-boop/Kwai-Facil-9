package br.com.juh.kwaifacil;
import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.graphics.Bitmap;
import android.graphics.PointF;
import android.graphics.Rect;
import android.hardware.HardwareBuffer;
import android.view.Display; import android.view.accessibility.*; import android.os.*; import android.widget.Toast; import java.util.*;
public class KwaiService extends AccessibilityService {
 static KwaiService instance; Handler h=new Handler(Looper.getMainLooper()); long captionMismatchSince=0; boolean hashtagRetry=false; int hashtagRetries=0; long warningUntil=0; long last=0; String lastScreen=""; int verificationAttempts=0,lastProfileCheckAttempt=0; long finalVerificationUntil=0; long verificationSentAt=-1,verificationActionAt=0,verificationStartedAt=0; int verificationStage=0; boolean profileScrolled=false; boolean readingTest=false; String lastReadCaption=""; boolean verificationMatched=false; long nextVerificationAt=0; String verificationFailure="";
 private static final long FIRST_VERIFICATION_DELAY_MS = 70_000L;
 private static final long NEXT_VERIFICATION_DELAY_MS = 30_000L;
 private int captionEntryAttempts;
 private boolean captionScreenSeen;
 private int coverAdjustments;
 private int coverOpenAttempts;
 private boolean coverNameVerified;
 private static final int COVER_OPEN=1, COVER_TIMELINE=2, COVER_STYLE=3, COVER_CONFIRMED=4;
 private static final int COVER_INPUT=5, COVER_INPUT_CONFIRM=6, COVER_POSITION=7, COVER_CAPTURE=8;
 private static final int COVER_MANUAL=9, COVER_SAVE=10;
 boolean diagnosticPending=false;
 void scheduleDiagnostic(String phase){
  if(diagnosticPending||Run.active||QueueStore.running)return;
  diagnosticPending=true;
  Toast.makeText(this,"Diagnóstico em 10 segundos. Abra a tela indicada do Kwai.",Toast.LENGTH_LONG).show();
  h.postDelayed(()->captureDiagnostic(phase),10000);
 }
 void captureDiagnostic(String phase){
  diagnosticPending=false;
  AccessibilityNodeInfo root=getRootInActiveWindow();
  String pkg=root==null||root.getPackageName()==null?"":root.getPackageName().toString();
  if(!pkg.equals("com.kwai.video")&&!pkg.equals("com.smile.gifmaker")){
   getSharedPreferences("diagnostic",0).edit().putString("error","Não capturei: o Kwai não estava na frente. Abra a tela indicada durante os 10 segundos.").putBoolean("show",true).commit();
  }else{
   StringBuilder out=new StringBuilder();
   android.util.DisplayMetrics metrics=new android.util.DisplayMetrics();
   ((android.view.WindowManager)getSystemService(WINDOW_SERVICE)).getDefaultDisplay().getRealMetrics(metrics);
   out.append("KWAI FÁCIL — DIAGNÓSTICO DE ACESSIBILIDADE\nEtapa: ").append(phase)
      .append("\nData: ").append(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z",Locale.ROOT).format(new Date()))
      .append("\nPacote: ").append(pkg).append("\nTela física: ").append(metrics.widthPixels).append("x").append(metrics.heightPixels)
      .append("\nFormato: índice, pai, classe, id, limites, visível, habilitado, clicável, ações, texto e descrição.\n");
   int[] counter={0};dumpDiagnostic(root,out,counter,-1,0);
   if(counter[0]>=1500)out.append("\n[RELATÓRIO LIMITADO A 1500 ELEMENTOS]\n");
   getSharedPreferences("diagnostic",0).edit().putString(phase,out.toString()).putString("latest",phase).remove("error").putBoolean("show",true).commit();
  }
  android.content.Intent i=new android.content.Intent(this,MainActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK|android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);startActivity(i);
 }
 void dumpDiagnostic(AccessibilityNodeInfo n,StringBuilder out,int[] count,int parent,int depth){
  if(n==null)return;
  if(count[0]>=1500||depth>60){out.append("\n[LIMITE DO RELATÓRIO ATINGIDO]\n");return;}
  int index=count[0]++;
  android.graphics.Rect r=new android.graphics.Rect();n.getBoundsInScreen(r);
  out.append("\n#").append(index).append(" pai=").append(parent).append(" classe=").append(n.getClassName())
   .append(" id=").append(n.getViewIdResourceName()).append(" limites=").append(r.toShortString())
   .append(" visível=").append(n.isVisibleToUser()).append(" habilitado=").append(n.isEnabled())
   .append(" clicável=").append(n.isClickable()).append(" selecionado=").append(n.isSelected())
   .append(" ações=").append(n.getActionList()).append("\ntexto=").append(diagnosticText(n.getText()))
   .append("\ndescrição=").append(diagnosticText(n.getContentDescription())).append("\n");
  for(int k=0;k<n.getChildCount()&&count[0]<1500;k++){
   AccessibilityNodeInfo child=n.getChild(k);if(child==null)continue;
   try{dumpDiagnostic(child,out,count,index,depth+1);}finally{child.recycle();}
  }
 }
 String diagnosticText(CharSequence text){return text==null?"<vazio>":text.toString().replace("\n","\\n");}
 protected void onServiceConnected(){instance=this;QueueStore.init(this);h.post(tick);}
 public void onDestroy(){instance=null;h.removeCallbacks(tick);QueueStore.pause("Assistente desativado. Revise o item em andamento.");super.onDestroy();}
 public void onInterrupt(){QueueStore.pause("Assistente interrompido. Revise o item em andamento.");}
 public void onAccessibilityEvent(AccessibilityEvent e){
 if(readingTest||!Run.active||e.getPackageName()==null)return;
 boolean target=Run.target.contentEquals(e.getPackageName());
 boolean toast=e.getEventType()==AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED;
 if(!target&&!(toast&&"android".contentEquals(e.getPackageName())))return;
 boolean eventAfterSend=Run.sent&&Run.sentAt>0&&e.getEventTime()>=Run.sentAt;
 for(CharSequence t:e.getText())if(t!=null){String text=t.toString();if((target||toast)&&text.toLowerCase(Locale.ROOT).contains("máximo de 5 hashtags"))onHashtagWarning();if(eventAfterSend&&Run.filled&&Run.auto&&confirmed(text)){QueueStore.success();return;}}
 if(target&&eventAfterSend){AccessibilityNodeInfo source=e.getSource();if(source!=null){List<AccessibilityNodeInfo> eventNodes=new ArrayList<>();collect(source,eventNodes);for(AccessibilityNodeInfo n:eventNodes)if(Run.filled&&Run.auto&&confirmed(label(n))){QueueStore.success();return;}}}
 }
 void onHashtagWarning(){warningUntil=System.currentTimeMillis()+3000;if(Run.sent){hashtagRetry=true;Run.status="Aviso de hashtags. Aguardando para tentar Publicar novamente…";}}
 String normalizedCaption(CharSequence value){if(value==null)return "";return java.text.Normalizer.normalize(value.toString(),java.text.Normalizer.Form.NFC).replaceAll("[\\s\\p{Z}\\p{Cf}]", "");}
 boolean sameCaption(CharSequence value){return !normalizedCaption(Run.caption).isEmpty()&&normalizedCaption(Run.caption).equals(normalizedCaption(value));}
 String normalizeStatus(String value){if(value==null)return "";return java.text.Normalizer.normalize(value,java.text.Normalizer.Form.NFD).replaceAll("\\p{M}+","").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+"," ").trim().replaceAll("\\s+"," ");}
 boolean confirmed(String t){String s=normalizeStatus(t);return s.equals("publicado com sucesso")||s.equals("video publicado com sucesso")||s.equals("publicacao concluida")||s.equals("posted successfully")||s.equals("postado com sucesso")||s.equals("seu video foi publicado")||s.equals("publicacao realizada com sucesso")||s.equals("post publicado com sucesso")||s.equals("postagem publicada com sucesso");}
 Runnable tick=new Runnable(){public void run(){if(Run.active)step();else readingTest=false;h.postDelayed(this,800);}};
 void step(){if(readingTest){
 AccessibilityNodeInfo root=getRootInActiveWindow();
 if(SystemClock.uptimeMillis()-verificationStartedAt>46000){endReadingTest("Teste: não consegui ler a legenda. Confira a acessibilidade do Kwai.");return;}
 if(root==null||!Run.target.contentEquals(root.getPackageName()))return;
 List<AccessibilityNodeInfo> nodes=new ArrayList<>();collect(root,nodes);verifyAfterSend(root,nodes);return;
 }if(System.currentTimeMillis()-Run.started>(Run.sent?300000:90000)){if(Run.sent&&!uploadInProgressFromRoot())QueueStore.pause("Não publicado: tempo limite de confirmação. Confira o perfil antes de continuar.");else finish(Run.sent?"O Kwai ainda não terminou de enviar ou confirmar este vídeo. Confira antes de continuar.":"Tempo limite ao preparar o vídeo. Confira a tela do Kwai; nenhuma nova tentativa será feita.");return;}
 AccessibilityNodeInfo root=kwaiRoot();if(root==null)return;
 List<AccessibilityNodeInfo> nodes=new ArrayList<>();collect(root,nodes);for(AccessibilityNodeInfo n:nodes)if(label(n).contains("máximo de 5 hashtags"))onHashtagWarning();String screen="";for(AccessibilityNodeInfo n:nodes)screen+=label(n)+"|";
 if(hashtagRetry&&System.currentTimeMillis()>=warningUntil){
 AccessibilityNodeInfo button=publishButton(nodes,root);boolean sameCaption=false;for(AccessibilityNodeInfo n:nodes)if(n.isEditable()&&sameCaption(n.getText()))sameCaption=true;
 if(button!=null&&sameCaption){if(hashtagRetries>=2){finish("O aviso de hashtags voltou. Revise a legenda antes de continuar.");return;}hashtagRetries++;hashtagRetry=false;Run.sent=false;last=0;}
 }
 if(Run.sent){for(AccessibilityNodeInfo n:nodes)if(confirmed(label(n))){QueueStore.success();return;}verifyAfterSend(root,nodes);return;}
 if(System.currentTimeMillis()-last<1600||System.currentTimeMillis()<warningUntil)return;
 // Não atravessa confirmação, login ou avisos de conta.
 for(AccessibilityNodeInfo n:nodes){String t=label(n);if(t.contains("verifique")||t.contains("captcha")||t.contains("fazer login")||t.contains("aceitar termos")){finish("O Kwai pede sua atenção. A execução foi interrompida.");return;}}
 AccessibilityNodeInfo captionField=captionField(nodes);
 AccessibilityNodeInfo publish=publishButton(nodes,root);
 AccessibilityNodeInfo editorNext=exact(nodes,"próximo","avançar","seguinte","next");
 boolean trimControls=false,editorControls=false,captionControls=false;
 for(AccessibilityNodeInfo node:nodes){if(!node.isVisibleToUser())continue;String t=label(node);
  if(t.equals("velocidade")||t.equals("girar")||t.contains("selecionados"))trimControls=true;
  if(t.equals("texto")||t.equals("stickers")||t.equals("filtro")||t.equals("melhorar"))editorControls=true;
  if(t.contains("descreva sua publicação")||t.equals("adicionar local")||t.equals("configurações avançadas")||t.equals("hashtag"))captionControls=true;
 }
 captionControls|=captionField!=null&&(captionFieldScore(captionField)>=4||editorNext==null);
 int kind=ScreenKind.detect(editorNext!=null,publish!=null,trimControls,editorControls,captionControls,coverPanel(nodes,root)!=null);
 if(!Run.coverDone&&(Run.coverStage>0||kind==ScreenKind.COVER)){
  coverStep(root,nodes,kind==ScreenKind.CAPTION?publish:null,editorNext);return;
 }
 if(!Run.coverDone&&editorCoverControl(nodes,root)!=null){
  coverStep(root,nodes,null,editorNext);return;
 }
 if(kind==ScreenKind.TRIM){if(click(editorNext)){last=System.currentTimeMillis();Run.started=last;Run.status="Etapa 1: avançando do corte para a edição…";}return;}
 if(kind!=ScreenKind.CAPTION)publish=null;
 if(!Run.coverDone&&(kind==ScreenKind.EDITOR||kind==ScreenKind.CAPTION)){
  coverStep(root,nodes,publish,kind==ScreenKind.EDITOR?editorNext:null);return;
 }
 if(!Run.coverDone&&editorNext!=null){Run.status="Aguardando reconhecer corte ou edição; nenhum gesto de capa foi feito.";return;}
 if(!Run.titleDone){
  if(kind==ScreenKind.EDITOR||Run.titleStage>0){titleStep(root,nodes);return;}
  if(kind==ScreenKind.CAPTION){finish("Não consegui inserir o título no vídeo. A publicação foi interrompida para revisão.");return;}
 }
 if(kind==ScreenKind.CAPTION)captionScreenSeen=true;
 if(kind==ScreenKind.CAPTION||(captionScreenSeen&&keyboardVisible())){
  if(!prepareCaption(captionField,nodes))return;
  if(publish==null){
   if(keyboardVisible()){performGlobalAction(GLOBAL_ACTION_BACK);last=System.currentTimeMillis();}
   return;
  }
  if(!Run.auto){finish("Legenda preenchida. Revise e toque em Publicar no Kwai.");return;}
  Run.sentAt=SystemClock.uptimeMillis();
  if(click(publish)){resetVerification();Run.sent=true;Run.started=System.currentTimeMillis();Run.status="Enviando vídeo; aguardando confirmação explícita do Kwai…";}
  else Run.sentAt=0;
  return;
 }
 AccessibilityNodeInfo next=exact(nodes,"próximo","avançar","seguinte","next");if(next!=null&&Run.advances<3&&!screen.equals(lastScreen)){if(click(next)){Run.advances++;lastScreen=screen;last=System.currentTimeMillis();Run.status="Avançando para a legenda…";}return;}
 Run.status="Aguardando a tela de edição/legenda do Kwai. Se necessário, avance manualmente. Volte ao Kwai Fácil para parar.";
 }
 AccessibilityNodeInfo kwaiRoot(){
  AccessibilityNodeInfo root=getRootInActiveWindow();
  if(root!=null&&Run.target.contentEquals(root.getPackageName()))return root;
  if(!keyboardVisible())return null;
  for(AccessibilityWindowInfo window:getWindows()){
   if(window.getType()!=AccessibilityWindowInfo.TYPE_APPLICATION)continue;
   root=window.getRoot();
   if(root!=null&&Run.target.contentEquals(root.getPackageName()))return root;
  }
  return null;
 }

 boolean keyboardVisible(){
  for(AccessibilityWindowInfo window:getWindows()){
   if(window.getType()==AccessibilityWindowInfo.TYPE_INPUT_METHOD)return true;
  }
  return false;
 }

 void resetCaptionEntry(){
  captionScreenSeen=false;
  captionEntryAttempts=0;
  captionMismatchSince=0;
 }

 AccessibilityNodeInfo captionField(List<AccessibilityNodeInfo> nodes){
  AccessibilityNodeInfo best=null;
  int bestScore=-1;
  boolean tied=false;
  for(AccessibilityNodeInfo node:nodes){
   if(!node.isVisibleToUser()||!node.isEnabled()||node.isPassword())continue;
   if(!node.isEditable()&&!node.getActionList().contains(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_TEXT))continue;
   if(sameCaption(node.getText()))return node;
   int score=captionFieldScore(node);
   if(score>bestScore){best=node;bestScore=score;tied=false;}
   else if(score==bestScore)tied=true;
  }
  return tied?null:best;
 }

 int captionFieldScore(AccessibilityNodeInfo node){
  String id=String.valueOf(node.getViewIdResourceName()).toLowerCase(Locale.ROOT);
  String hint=normalizeStatus(String.valueOf(node.getHintText()));
  String description=normalizeStatus(String.valueOf(node.getContentDescription()));
  String text=normalizeStatus(String.valueOf(node.getText()));
  if(isCaptionHint(hint)||isCaptionHint(description)||isCaptionHint(text))return 8;
  if(id.contains("caption")||id.contains("description")||id.contains("edit_desc")||id.contains("desc_edit"))return 4;
  return node.isFocused()?1:0;
 }

 boolean isCaptionHint(String text){
  return text.contains("descreva sua publicacao")||text.contains("descreva seu video")
      ||text.contains("adicionar legenda")||text.contains("escreva uma legenda")
      ||text.contains("escreva algo")||text.contains("write a caption")||text.contains("add a caption");
 }

 boolean setText(AccessibilityNodeInfo field,String value){
  Bundle args=new Bundle();
  args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,value);
  return field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,args);
 }

 void copyText(String text){
  ClipboardManager clipboard=getSystemService(ClipboardManager.class);
  clipboard.setPrimaryClip(ClipData.newPlainText("Texto do Kwai Fácil",text));
 }

 boolean prepareCaption(AccessibilityNodeInfo field,List<AccessibilityNodeInfo> nodes){
  if(field!=null&&sameCaption(field.getText())){
   Run.filled=true;captionMismatchSince=0;return true;
  }
  if(field==null&&captionEntryAttempts>0){
   for(AccessibilityNodeInfo node:nodes){
    if(node.isVisibleToUser()&&sameCaption(node.getText())){
     Run.filled=true;captionMismatchSince=0;return true;
    }
   }
  }

  long now=System.currentTimeMillis();
  if(captionMismatchSince==0)captionMismatchSince=now;
  if(now-captionMismatchSince>25000){
   finish("Não consegui conferir a legenda. O vídeo está aberto no Kwai para revisão.");return false;
  }
  if(captionEntryAttempts==0&&field!=null){
   setText(field,Run.caption);
   captionEntryAttempts=1;last=now;Run.status="Conferindo a legenda…";return false;
  }
  if(captionEntryAttempts==1&&field!=null){
   field.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
   field.performAction(AccessibilityNodeInfo.ACTION_CLICK);
   copyText(Run.caption);
   captionEntryAttempts=2;last=now;Run.status="Preparando a colagem da legenda…";return false;
  }
  if(captionEntryAttempts==2&&field!=null){
   if(!isEmptyInput(field)){
    Bundle selection=new Bundle();
    selection.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT,0);
    selection.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT,field.getText().length());
    if(field.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION,selection))
     field.performAction(AccessibilityNodeInfo.ACTION_PASTE);
   }else field.performAction(AccessibilityNodeInfo.ACTION_PASTE);
   captionEntryAttempts=3;last=now;Run.status="Conferindo a legenda colada…";return false;
  }
  if(captionEntryAttempts==0&&now-captionMismatchSince>=2500){
   copyText(Run.caption);captionEntryAttempts=3;
  }
  Run.status=captionEntryAttempts>=3?"Legenda copiada. Se necessário, cole no campo do Kwai para continuar."
      :"Aguardando reconhecer o campo da legenda…";
  return false;
 }

 boolean isEmptyInput(AccessibilityNodeInfo field){
  return field.getText()==null||field.getText().length()==0||field.isShowingHintText()
      ||isCaptionHint(normalizeStatus(String.valueOf(field.getText())));
 }

 // O título opcional do TXT é adicionado como texto editável no Kwai antes de avançar.
 void titleStep(AccessibilityNodeInfo root,List<AccessibilityNodeInfo> nodes){
  long now=System.currentTimeMillis();
  if(Run.titleStage==0){
   AccessibilityNodeInfo text=exact(nodes,"texto");
   if(text!=null&&click(text)){Run.titleStage=1;Run.titleAction=now;Run.started=now;Run.status="Abrindo o texto do título…";return;}
   if(tapEditorPoint(root,.925f,.14f)){Run.titleStage=1;Run.titleAction=now;Run.started=now;Run.status="Abrindo o texto do título…";return;}
   finish("Não consegui abrir Texto no Kwai. Revise o título manualmente.");return;
  }
  if(now-Run.titleAction>15000){finish("O Kwai não abriu a edição do título. Confira o vídeo antes de continuar.");return;}
  if(Run.titleStage==1){
   for(AccessibilityNodeInfo n:nodes)if(n.isVisibleToUser()&&n.isEnabled()&&n.isEditable()){
    n.performAction(AccessibilityNodeInfo.ACTION_CLICK);
    Bundle args=new Bundle();args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,Run.title);
    if(n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,args)){Run.titleStage=2;Run.titleAction=now;Run.status="Aplicando o título no vídeo…";return;}
   }
   Run.status="Aguardando o campo de texto do título…";return;
  }
  if(now-Run.titleAction<900)return;
  if(Run.titleStage==2){
   if(!tapEditorPoint(root,.87f,.53f))return; // Quinta opção Aa indicada pela usuária.
   Run.titleStage=3;Run.titleAction=now;return;
  }
  if(Run.titleStage==3){
   if(!tapEditorPoint(root,.07f,.53f))return; // Primeiro toque no botão T.
   Run.titleStage=4;Run.titleAction=now;return;
  }
  if(Run.titleStage==4){
   if(!tapEditorPoint(root,.07f,.53f))return; // Segundo toque no T para aplicar o formato correto, sem caixa.
   Run.titleStage=5;Run.titleAction=now;return;
  }
  if(Run.titleStage==5){
   if(!tapEditorPoint(root,.89f,.075f))return; // Toca em Feito antes de mover o título.
   Run.titleStage=6;Run.titleAction=now;return;
  }
  if(Run.titleStage==6){
   AccessibilityNodeInfo titleNode=null;
   for(AccessibilityNodeInfo n:nodes){String t=label(n);if(n.isVisibleToUser()&&t.contains(Run.title.toLowerCase(Locale.ROOT))){titleNode=n;break;}}
   android.graphics.Rect rootBounds=new android.graphics.Rect();root.getBoundsInScreen(rootBounds);
   android.graphics.Rect textBounds=new android.graphics.Rect();if(titleNode!=null)titleNode.getBoundsInScreen(textBounds);
   if(rootBounds.width()<=0||rootBounds.height()<=0){finish("Não consegui localizar a área do vídeo para posicionar o título.");return;}
   float sx=textBounds.isEmpty()?rootBounds.centerX():textBounds.centerX();
   float sy=textBounds.isEmpty()?rootBounds.top+rootBounds.height()*.34f:textBounds.centerY();
   float ex=rootBounds.left+rootBounds.width()*.50f,ey=rootBounds.top+rootBounds.height()*.20f;
   Run.titleStage=7;Run.titleAction=now;
   gesture(sx,sy,ex,ey,420,()->{Run.titleAction=System.currentTimeMillis();Run.status="Posicionando o título na parte superior do vídeo…";});
   return;
  }
  if(Run.titleStage==7){
   Run.titleDone=true;Run.titleStage=0;Run.titleAction=0;Run.started=now;last=now;
   Run.status="Título aplicado. Continuando a preparação do vídeo…";return;
  }
 }
 boolean tapEditorPoint(AccessibilityNodeInfo root,float x,float y){
  android.graphics.Rect r=new android.graphics.Rect();root.getBoundsInScreen(r);
  if(r.width()<=0||r.height()<=0)return false;
  float px=r.left+r.width()*x,py=r.top+r.height()*y;
  return navigationGesture(px,py,px,py,90);
 }
 // Each publication owns its timer and navigation state.
 void resetVerification(){
  verificationSentAt=Run.sentAt;verificationAttempts=0;lastProfileCheckAttempt=0;
  finalVerificationUntil=0;verificationActionAt=0;verificationStartedAt=0;
  verificationStage=0;profileScrolled=false;nextVerificationAt=0;verificationMatched=false;verificationFailure="";
 }
 void verifyAfterSend(AccessibilityNodeInfo root,List<AccessibilityNodeInfo> nodes){
  if(!readingTest&&verificationSentAt!=Run.sentAt)resetVerification();
  long now=SystemClock.uptimeMillis();
  if(verificationStage==0){
   long dueAt=nextVerificationAt>0?nextVerificationAt:Run.sentAt+FIRST_VERIFICATION_DELAY_MS;
   if(now<dueAt){Run.status="Aguardando confirmação; próxima conferência em "+((dueAt-now+999)/1000)+" s.";return;}
   if(uploadInProgress(nodes)){Run.status="Aguardando o envio do Kwai terminar…";return;}
   verificationAttempts++;verificationStartedAt=now;verificationStage=1;
   verificationActionAt=0;profileScrolled=false;verificationMatched=false;verificationFailure="";
  }
  // Video recognition always wins over stale profile nodes and navigation stages.
  // Opening a thumbnail is asynchronous: the next tick may already be in the player.
  if(verificationStage>=1&&verificationStage<=3&&
     (videoNode(nodes,"slide_play_tv_user_info_desc")!=null||
      videoNode(nodes,"slide_play_tv_user_info_desc_more_hide")!=null)){
   verificationStage=4;verificationActionAt=now;
   Run.status="Vídeo reconhecido. Localizando Mais pelo identificador…";return;
  }
  // Timeouts also leave the video through its top arrow before any next action.
  if(verificationStage<6&&now-verificationStartedAt>30000){
   verificationFailure="Não consegui expandir e ler a legenda completa.";
   verificationStage=6;verificationActionAt=0;
  }
  if(now-verificationActionAt<1600)return;
  if(verificationStage==6){
   if(isProfileScreen(nodes)||homeScreen(nodes)&&!publishedVideoScreen(nodes)){
    verificationStage=7;
   }else{
    if(now-verificationStartedAt>42000){
     if(readingTest)endReadingTest("Teste interrompido: não consegui voltar pela seta. "+verificationFailure);
     else QueueStore.pause("Não consegui voltar ao perfil. Confira a tela antes de continuar.");
     return;
    }
    clickVideoBack(root,nodes);verificationActionAt=now;Run.status="Voltando pela seta superior…";return;
   }
  }
  if(verificationStage==7){
   if(readingTest){endReadingTest(lastReadCaption.isEmpty()?"Teste: "+verificationFailure:"Legenda lida:\n"+lastReadCaption);return;}
   if(verificationMatched){QueueStore.success();return;}
   if(verificationAttempts>=3){QueueStore.pause("Legenda não confirmada após três conferências. Fila pausada neste vídeo.");return;}
   nextVerificationAt=now+NEXT_VERIFICATION_DELAY_MS;verificationStage=0;
   Run.status="Voltei ao perfil. Aguardando 30 segundos para conferir novamente.";return;
  }
  if(uploadInProgress(nodes)){Run.status="Aguardando o envio do Kwai terminar…";return;}
  if(verificationStage==1){
   if(isProfileScreen(nodes)){verificationStage=3;verificationActionAt=now;return;}
   AccessibilityNodeInfo tab=profileButton(nodes,root);
   if(tab!=null&&verificationClick(tab)){verificationActionAt=now;return;}
   if(homeScreen(nodes)){tapRelative(root,0.90f,0.95f);verificationActionAt=now;return;}
   performGlobalAction(GLOBAL_ACTION_BACK);verificationActionAt=now;return;
  }
  if(verificationStage==2){
   // No upward swipe is allowed during verification, even when the grid is missing.
   verificationStage=3;verificationActionAt=now;return;
  }
  if(verificationStage==3){
   if(!isProfileScreen(nodes)){verificationStage=1;return;}
   AccessibilityNodeInfo latest=latestVideo(nodes,root);
   if(latest!=null&&verificationClick(latest)||latest==null&&tapFirstPost(root,nodes)){
    verificationStage=4;verificationActionAt=now;return;
   }

   Run.status="Aguardando identificar o primeiro vídeo da grade…";return;
  }
  if(verificationStage==4||verificationStage==5){
   if(isProfileScreen(nodes)){verificationStage=3;return;}
   if(!publishedVideoScreen(nodes))return;
   AccessibilityNodeInfo more=captionMore(nodes,root);
   if(more!=null){
    if(tapCaptionMore(more,root)){verificationStage=5;verificationActionAt=now;}
    Run.status="Tocando em Mais; aguardando a legenda expandir…";return;
   }
   // A dispatched tap is not proof: require Ocultar or an exact complete caption.
   AccessibilityNodeInfo toggle=videoNode(nodes,"slide_play_tv_user_info_desc_more_hide");
   boolean expanded=toggle!=null&&normalizeStatus(label(toggle)).equals("ocultar");
   boolean matches=publishedCaptionMatches(nodes);
   String text=readCaption(nodes,root);
   boolean completeInTest=readingTest&&toggle==null&&!text.endsWith("…")&&!text.endsWith("...");
   if(!expanded&&!matches&&!completeInTest){Run.status="Aguardando confirmar a legenda inteira; ainda não li o texto.";return;}
   if(text.isEmpty()){Run.status="Legenda expandida; aguardando o texto acessível…";return;}
   lastReadCaption=text;verificationMatched=matches;
   verificationFailure=matches?"":"A legenda lida é diferente da legenda do TXT.";
   verificationStage=6;verificationActionAt=0;return;
  }
 }
 AccessibilityNodeInfo videoNode(List<AccessibilityNodeInfo> nodes,String name){
  String suffix=":id/"+name;
  for(AccessibilityNodeInfo n:nodes){
   String id=n.getViewIdResourceName();
   if(!n.isVisibleToUser()||!n.isEnabled()||id==null||!id.endsWith(suffix))continue;
   android.graphics.Rect bounds=new android.graphics.Rect();n.getBoundsInScreen(bounds);
   if(!bounds.isEmpty())return n;
  }return null;
 }
 AccessibilityNodeInfo captionMore(List<AccessibilityNodeInfo> nodes,AccessibilityNodeInfo root){
  AccessibilityNodeInfo n=videoNode(nodes,"slide_play_tv_user_info_desc_more_hide");
  if(n!=null&&normalizeStatus(label(n)).equals("mais"))return n;
  AccessibilityNodeInfo caption=videoNode(nodes,"slide_play_tv_user_info_desc");
  AccessibilityNodeInfo more=exact(nodes,"mais","ver mais");
  if(caption==null||more==null)return null;
  Rect text=new Rect(),button=new Rect();
  caption.getBoundsInScreen(text);more.getBoundsInScreen(button);
  text.inset(-text.height(),-text.height());
  return text.contains(button.centerX(),button.centerY())?more:null;
 }
 boolean clickExactVideoNode(AccessibilityNodeInfo n){
  if(n==null)return false;
  // Never climb to a clickable parent: that can select the player or playlist.
  if(n.isClickable()&&n.performAction(AccessibilityNodeInfo.ACTION_CLICK))return true;
  android.graphics.Rect bounds=new android.graphics.Rect();n.getBoundsInScreen(bounds);
  if(bounds.isEmpty())return false;
  return navigationGesture(bounds.centerX(),bounds.centerY(),bounds.centerX(),bounds.centerY(),90);
 }
 boolean tapCaptionMore(AccessibilityNodeInfo n,AccessibilityNodeInfo root){
  return clickExactVideoNode(n);
 }
 void clickVideoBack(AccessibilityNodeInfo root,List<AccessibilityNodeInfo> nodes){
  AccessibilityNodeInfo back=videoNode(nodes,"inside_stream_back_btn");
  if(back==null&&publishedVideoScreen(nodes))back=exact(nodes,"voltar","back","navegar para cima");
  clickExactVideoNode(back);
 }
 void startReadingTest(String target){
  readingTest=true;Run.target=target;Run.active=true;lastReadCaption="";
  resetVerification();verificationStage=1;verificationAttempts=1;
  verificationStartedAt=SystemClock.uptimeMillis();Run.status="Simulação sem rolagem: Perfil → vídeo mais recente → Mais…";
 }
 void endReadingTest(String result){readingTest=false;Run.active=false;Run.status=result;QueueStore.notice=result;
  if(!lastReadCaption.isEmpty())getSharedPreferences("diagnostic",0).edit()
      .putString("read_caption",lastReadCaption).apply();
  Toast.makeText(this,result,Toast.LENGTH_LONG).show();
  android.content.Intent i=new android.content.Intent(this,MainActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK|android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);startActivity(i);
 }
 boolean tapFirstPost(AccessibilityNodeInfo root,List<AccessibilityNodeInfo> nodes){
  // Screenshots: the horizontal playlist cards precede the three-column post grid.
  android.graphics.Rect win=new android.graphics.Rect();root.getBoundsInScreen(win);
  int playlistBottom=0;
  for(AccessibilityNodeInfo n:nodes){if(!n.isVisibleToUser())continue;
   android.graphics.Rect b=new android.graphics.Rect();n.getBoundsInScreen(b);
   if(label(n).contains("criar sua")&&label(n).contains("playlist"))playlistBottom=Math.max(playlistBottom,b.bottom);
  }
  if(playlistBottom<=0||playlistBottom+win.width()*0.22f>=win.bottom-win.height()*0.10f)return false;
  float x=win.left+win.width()/6f,y=playlistBottom+win.width()*0.22f;
  return navigationGesture(x,y,x,y,80);
 }
 String readCaption(List<AccessibilityNodeInfo> nodes,AccessibilityNodeInfo root){
  AccessibilityNodeInfo caption=videoNode(nodes,"slide_play_tv_user_info_desc");
  return caption==null||caption.getText()==null?"":caption.getText().toString().trim();
 }
 boolean homeScreen(List<AccessibilityNodeInfo> nodes){
  return exact(nodes,"perfil","profile","início","inicio","para você","para voce")!=null;
 }
 boolean publishedVideoScreen(List<AccessibilityNodeInfo> nodes){
  return videoNode(nodes,"inside_stream_back_btn")!=null&&videoNode(nodes,"slide_play_tv_user_info_desc")!=null||exact(nodes,"compartilhar","compart.","comentar","comentários","comentarios","análise de trabalhos","ver curtidas que recebeu")!=null;
 }
 boolean publishedCaptionMatches(List<AccessibilityNodeInfo> nodes){
  AccessibilityNodeInfo toggle=videoNode(nodes,"slide_play_tv_user_info_desc_more_hide");
  // While Mais is visible the caption is truncated and must never confirm a post.
  if((toggle!=null&&normalizeStatus(label(toggle)).equals("mais"))||captionMore(nodes,null)!=null)return false;
  String actual=normalizedCaption(readCaption(nodes,null));
  String expected=normalizedCaption(Run.caption);
  return !expected.isEmpty()&&expected.equals(actual);
 }
 AccessibilityNodeInfo latestVideo(List<AccessibilityNodeInfo> nodes,AccessibilityNodeInfo root){
  android.graphics.Rect win=new android.graphics.Rect();root.getBoundsInScreen(win);
  AccessibilityNodeInfo best=null;int top=Integer.MAX_VALUE,left=Integer.MAX_VALUE;
  for(AccessibilityNodeInfo n:nodes){
   if(!n.isVisibleToUser()||!n.isEnabled())continue;
   String id=n.getViewIdResourceName(),cls=String.valueOf(n.getClassName());
   android.graphics.Rect r=new android.graphics.Rect();n.getBoundsInScreen(r);
   boolean image=cls.contains("Image")||(id!=null&&(id.contains("cover")||id.contains("thumbnail")));
   if((!image&&!n.isClickable())||r.width()<win.width()*0.22||r.width()>win.width()*0.40||r.height()<win.height()*0.10||r.height()>win.height()*0.43)continue;
   if(r.top<win.top+win.height()*0.15||r.bottom>win.top+win.height()*0.94)continue;
   // Playlists are short landscape cards; post covers are portrait.
   if(r.height()<r.width()*1.1)continue;
   if(r.top<top-12||(Math.abs(r.top-top)<=12&&r.left<left)){best=n;top=r.top;left=r.left;}
  }
  return best;
 }
 boolean verificationClick(AccessibilityNodeInfo node){
  if(click(node))return true;
  android.graphics.Rect r=new android.graphics.Rect();node.getBoundsInScreen(r);
  if(r.isEmpty())return false;
  return navigationGesture(r.centerX(),r.centerY(),r.centerX(),r.centerY(),80);
 }
 void tapRelative(AccessibilityNodeInfo root,float x,float y){
  android.graphics.Rect r=new android.graphics.Rect();root.getBoundsInScreen(r);
  navigationGesture(r.left+r.width()*x,r.top+r.height()*y,r.left+r.width()*x,r.top+r.height()*y,80);
 }
 boolean navigationGesture(float x,float y,float ex,float ey,long duration){
  // Verification may tap but must never drag the player to another video.
  if((readingTest||Run.sent)&&(Math.abs(x-ex)>1f||Math.abs(y-ey)>1f)){
   Run.status="Rolagem bloqueada durante a conferência da legenda.";return false;
  }
  android.graphics.Path p=new android.graphics.Path();p.moveTo(x,y);if(x!=ex||y!=ey)p.lineTo(ex,ey);
  return dispatchGesture(new android.accessibilityservice.GestureDescription.Builder().addStroke(new android.accessibilityservice.GestureDescription.StrokeDescription(p,0,duration)).build(),null,null);
 }
 boolean uploadInProgressFromRoot(){AccessibilityNodeInfo root=getRootInActiveWindow();if(root==null||!Run.target.contentEquals(root.getPackageName()))return false;List<AccessibilityNodeInfo> nodes=new ArrayList<>();collect(root,nodes);return uploadInProgress(nodes);}
 boolean uploadInProgress(List<AccessibilityNodeInfo> nodes){for(AccessibilityNodeInfo n:nodes){String s=normalizeStatus(label(n));if(s.contains("enviando")||s.contains("publicando")||s.contains("processando")||s.contains("uploading")||s.contains("subindo video"))return true;}return false;}
 boolean isProfileScreen(List<AccessibilityNodeInfo> nodes){boolean edit=false,posts=false,followers=false;if(publishedVideoScreen(nodes))return false;for(AccessibilityNodeInfo n:nodes){if(!n.isVisibleToUser())continue;String s=normalizeStatus(label(n));if(s.contains("editar perfil")||s.equals("edit profile"))edit=true;if(s.equals("publicacoes")||s.equals("videos")||s.equals("posts"))posts=true;if(s.contains("seguidores")||s.contains("followers"))followers=true;}if(edit||(posts&&followers))return true;
 for(AccessibilityNodeInfo n:nodes)if(n.isVisibleToUser()&&n.isSelected()&&(label(n).equals("perfil")||label(n).equals("profile")))return true;
 return false;}
 AccessibilityNodeInfo profileButton(List<AccessibilityNodeInfo> nodes,AccessibilityNodeInfo root){android.graphics.Rect win=new android.graphics.Rect();root.getBoundsInScreen(win);for(AccessibilityNodeInfo n:nodes){String s=normalizeStatus(label(n));if(!n.isVisibleToUser()||!n.isEnabled()||!(s.equals("perfil")||s.equals("profile")||s.equals("eu")||s.equals("me")))continue;android.graphics.Rect b=new android.graphics.Rect();n.getBoundsInScreen(b);if(b.centerY()>=win.top+win.height()*0.70)return n;}return null;}
 boolean profileShowsCurrentCaption(List<AccessibilityNodeInfo> nodes){if(!isProfileScreen(nodes))return false;String caption=normalizeStatus(Run.caption);if(caption.length()<8)return false;String probe=caption.substring(0,Math.min(48,caption.length()));for(AccessibilityNodeInfo n:nodes){if(!n.isVisibleToUser())continue;String s=normalizeStatus(label(n));if(s.contains(probe))return true;}return false;}
 void refreshProfile(AccessibilityNodeInfo root){android.graphics.Rect b=new android.graphics.Rect();root.getBoundsInScreen(b);if(b.width()<=0||b.height()<=0)return;float x=b.centerX(),start=b.top+b.height()*0.24f,end=b.top+b.height()*0.63f;navigationGesture(x,start,x,end,450);}
 // A faixa por gesto usa o layout vertical do Kwai.
 void coverStep(AccessibilityNodeInfo root,List<AccessibilityNodeInfo> nodes,AccessibilityNodeInfo publish,AccessibilityNodeInfo editorNext){
  long now=System.currentTimeMillis();
  AccessibilityNodeInfo cover=coverPanel(nodes,root);
  if(Run.coverStage==0){
   coverAdjustments=0;coverNameVerified=false;coverOpenAttempts=0;
   if(cover!=null||openCover(root,nodes,publish,editorNext,false)){
    coverOpenAttempts=1;
    setCoverStage(COVER_OPEN,"Abrindo a seleção da capa…");Run.started=now;
   }else Run.status="Aguardando o botão de edição da capa…";
   return;
  }

  if(Run.coverStage==COVER_OPEN&&cover==null){
   if(now-Run.coverAction>20000){finish("Não consegui abrir Capa no Kwai. Confira a tela de edição.");return;}
   if(coverOpenAttempts<3&&now-Run.coverAction>=coverOpenAttempts*1800L
       &&openCover(root,nodes,publish,editorNext,true))coverOpenAttempts++;
   return;
  }

  if(Run.coverStage==COVER_CONFIRMED||Run.coverStage==COVER_MANUAL){
   if(cover==null&&(publish!=null||editorNext!=null)){
    if(!Run.coverName.isEmpty()&&!coverNameVerified){
     finish("Confira se o nome da novela ficou na capa antes de publicar.");return;
    }
    Run.coverDone=true;Run.started=now;last=now;Run.status="Capa selecionada; preparando a legenda…";
    return;
   }
   if(Run.coverStage==COVER_MANUAL)return;
  }
  if(now-Run.coverAction>20000){
   finish("Não consegui concluir a capa. Confira o editor de capa no Kwai.");return;
  }

  if(Run.coverStage==COVER_INPUT){
   AccessibilityNodeInfo field=coverInput(nodes);
   if(field==null)return;
   String name=CoverLayout.formatName(Run.coverName);
   if(!setText(field,name)){
    copyText(name);field.performAction(AccessibilityNodeInfo.ACTION_PASTE);
   }
   setCoverStage(COVER_INPUT_CONFIRM,"Conferindo o nome da novela na capa…");
   return;
  }
  if(Run.coverStage==COVER_INPUT_CONFIRM){
   AccessibilityNodeInfo field=coverInput(nodes);
   String expected=normalizedCaption(Run.coverName);
   if(field==null||!expected.equals(normalizedCaption(field.getText())))return;
   coverNameVerified=true;
   AccessibilityNodeInfo done=exact(nodes,"feito","concluir","confirmar","pronto","ok","done","✓","✔");
   if(done!=null&&clickExactVideoNode(done)){
    setCoverStage(COVER_POSITION,"Posicionando o nome na capa…");return;
   }
   if(keyboardVisible()&&performGlobalAction(GLOBAL_ACTION_BACK)){
    setCoverStage(COVER_POSITION,"Posicionando o nome na capa…");return;
   }
   if(cover!=null)setCoverStage(COVER_POSITION,"Posicionando o nome na capa…");
   return;
  }
  if(cover==null)return;
  Rect r=new Rect(),cr=new Rect();
  root.getBoundsInScreen(r);cover.getBoundsInScreen(cr);
  if(r.width()<=0||r.height()<r.width())return;

  if(Run.coverStage==COVER_OPEN){
   if(now-Run.coverAction<1200)return;
   if(Run.coverSeconds<0){setCoverStage(COVER_STYLE,"Mantendo o quadro selecionado…");return;}
   for(AccessibilityNodeInfo n:nodes){
    AccessibilityNodeInfo.RangeInfo range=n.getRangeInfo();
    if(range==null||!n.isVisibleToUser())continue;
    Rect b=new Rect();n.getBoundsInScreen(b);
    if(b.centerY()<r.top+r.height()*.78f)continue;
    Bundle args=new Bundle();
    args.putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE,(float)(range.getMin()+(range.getMax()-range.getMin())*Run.coverSeconds/Run.coverDuration));
    if(n.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.getId(),args)){
     setCoverStage(COVER_STYLE,"Quadro da capa selecionado.");return;
    }
   }
   float start=r.left+r.width()*.094f;
   float end=start+r.width()*.812f*(float)(Run.coverSeconds/Run.coverDuration);
   float y=cr.exactCenterY()-r.width()*.15f;
   setCoverStage(COVER_TIMELINE,"Selecionando o quadro da capa…");
   gesture(start,y,end,y,500,()->setCoverStage(COVER_STYLE,"Quadro da capa selecionado."));
   return;
  }

  if(now-Run.coverAction<1200)return;
  if(Run.coverStage==COVER_STYLE){
   if(Run.coverName.isEmpty()){setCoverStage(COVER_SAVE,"Confirmando a capa…");return;}
   float x=r.left+r.width()*.115f,y=cr.exactCenterY()-r.width()*.315f;
   if(navigationGesture(x,y,x,y,85))setCoverStage(COVER_INPUT,"Abrindo o primeiro estilo de letra da capa…");
   return;
  }
  if(Run.coverStage==COVER_POSITION){
   if(keyboardVisible())return;
   if(Build.VERSION.SDK_INT<30){manualCover();return;}
   setCoverStage(COVER_CAPTURE,"Conferindo a posição e a inclinação do nome…");
   takeScreenshot(Display.DEFAULT_DISPLAY,getMainExecutor(),new TakeScreenshotCallback(){
    @Override public void onSuccess(ScreenshotResult result){
     try(HardwareBuffer buffer=result.getHardwareBuffer()){
      Bitmap hardware=Bitmap.wrapHardwareBuffer(buffer,result.getColorSpace());
      if(hardware==null){if(coverCaptureActive())manualCover();return;}
      Bitmap screen=hardware.copy(Bitmap.Config.ARGB_8888,false);
      hardware.recycle();
      if(screen==null){if(coverCaptureActive())manualCover();return;}
      try{
       if(coverCaptureActive())adjustCoverName(CoverLayout.findSelection(screen,r,cr),r,cr);
      }finally{screen.recycle();}
     }
    }
    @Override public void onFailure(int code){if(coverCaptureActive())manualCover();}
   });
   return;
  }
  if(Run.coverStage==COVER_SAVE){
   for(AccessibilityNodeInfo node:nodes){
    if(!node.isVisibleToUser()||!node.isEnabled())continue;
    String text=label(node);
    if(!Arrays.asList("concluir","confirmar","salvar","pronto","✓","✔").contains(text))continue;
    Rect button=new Rect();node.getBoundsInScreen(button);
    if(button.centerX()>r.left+r.width()*.72f&&Math.abs(button.centerY()-cr.centerY())<r.width()*.10f&&clickExactVideoNode(node)){
     setCoverStage(COVER_CONFIRMED,"Confirmando a capa…");return;
    }
   }
   float x=r.left+r.width()*.915f;
   setCoverStage(COVER_CONFIRMED,"Confirmando a capa…");
   gesture(x,cr.exactCenterY(),x,cr.exactCenterY(),80,()->{});
  }
 }

 AccessibilityNodeInfo editorCoverControl(List<AccessibilityNodeInfo> nodes,AccessibilityNodeInfo root){
  Rect window=new Rect();root.getBoundsInScreen(window);
  for(AccessibilityNodeInfo node:nodes){
   if(!node.isVisibleToUser()||!node.isEnabled())continue;
   String text=label(node);
   if(!Arrays.asList("capa","editar capa","selecionar capa","adicionar capa","cover").contains(text))continue;
   Rect button=new Rect();node.getBoundsInScreen(button);
   if(!button.isEmpty()&&button.centerX()>window.left+window.width()*.72f
       &&button.centerY()>window.top+window.height()*.22f
       &&button.centerY()<window.top+window.height()*.58f)return node;
  }
  return null;
 }

 boolean openCover(AccessibilityNodeInfo root,List<AccessibilityNodeInfo> nodes,
                   AccessibilityNodeInfo publish,AccessibilityNodeInfo next,boolean retry){
  if(keyboardVisible())return false;
  Rect window=new Rect();root.getBoundsInScreen(window);
  AccessibilityNodeInfo sideCover=editorCoverControl(nodes,root);
  if(sideCover!=null){
   Rect button=new Rect();sideCover.getBoundsInScreen(button);
   if(!retry&&sideCover.isClickable()&&sideCover.performAction(AccessibilityNodeInfo.ACTION_CLICK))return true;
   float x=window.left+window.width()*.927f;
   float y=button.exactCenterY();
   return navigationGesture(x,y,x,y,90);
  }
  if(next!=null&&exact(nodes,"texto","stickers","filtro","melhorar")!=null){
   Rect nextBounds=new Rect();next.getBoundsInScreen(nextBounds);
   PointF icon=CoverLayout.editorCoverIcon(window,nextBounds);
   if(window.width()>0&&window.height()>window.width()){
    if(!nextBounds.isEmpty()&&icon.y>window.top+window.height()*.22f
        &&icon.y<window.top+window.height()*.58f)
     return navigationGesture(icon.x,icon.y,icon.x,icon.y,90);
    android.util.DisplayMetrics display=new android.util.DisplayMetrics();
    ((android.view.WindowManager)getSystemService(WINDOW_SERVICE)).getDefaultDisplay().getRealMetrics(display);
    float x=display.widthPixels*.927f,y=display.heightPixels*.374f;
    return navigationGesture(x,y,x,y,90);
   }
  }
  if(publish==null)return false;
  AccessibilityNodeInfo edit=exact(nodes,"editar capa","editar capa…","editar capa...","selecionar capa","adicionar capa","capa","cover");
  return clickExactVideoNode(edit);
 }

 AccessibilityNodeInfo coverInput(List<AccessibilityNodeInfo> nodes){
  AccessibilityNodeInfo field=null;
  for(AccessibilityNodeInfo node:nodes){
   if(!node.isVisibleToUser()||!node.isEnabled()||!node.isEditable()||node.isPassword())continue;
   if(node.isFocused())return node;
   if(field!=null)return null;
   field=node;
  }
  return field;
 }

 boolean coverCaptureActive(){return Run.active&&Run.coverStage==COVER_CAPTURE;}

 void setCoverStage(int stage,String status){
  Run.coverStage=stage;Run.coverAction=System.currentTimeMillis();Run.status=status;
 }

 void manualCover(){
  setCoverStage(COVER_MANUAL,"Confira o nome na capa. Centralize, incline para cima à direita e toque em ✓ no Kwai.");
  Run.started=System.currentTimeMillis();
  Toast.makeText(this,Run.status,Toast.LENGTH_LONG).show();
 }

 void adjustCoverName(CoverLayout.Selection selection,Rect window,Rect label){
  if(selection==null||coverAdjustments>=4){manualCover();return;}
  PointF target=CoverLayout.center(window,label);
  float dx=target.x-selection.center.x,dy=target.y-selection.center.y;
  float delta=CoverLayout.TILT-selection.angle;
  if(Math.hypot(dx,dy)>window.width()*.035f){
   coverAdjustments++;
   gesture(selection.center.x,selection.center.y,target.x,target.y,380,
       ()->setCoverStage(COVER_POSITION,"Conferindo a posição do nome…"));
   return;
  }
  if(Math.abs(delta)<=4f){setCoverStage(COVER_SAVE,"Nome da novela aplicado na capa.");return;}
  coverAdjustments++;
  float x=selection.rotate.x-selection.center.x,y=selection.rotate.y-selection.center.y;
  android.graphics.Path path=new android.graphics.Path();
  path.moveTo(selection.rotate.x,selection.rotate.y);
  for(int step=1;step<=8;step++){
   double angle=Math.toRadians(delta*step/8f);
   path.lineTo(selection.center.x+(float)(x*Math.cos(angle)-y*Math.sin(angle)),
       selection.center.y+(float)(x*Math.sin(angle)+y*Math.cos(angle)));
  }
  dispatchCoverGesture(path,460,()->setCoverStage(COVER_POSITION,"Conferindo a inclinação do nome…"));
 }

 AccessibilityNodeInfo coverPanel(List<AccessibilityNodeInfo> nodes,AccessibilityNodeInfo root){
 android.graphics.Rect r=new android.graphics.Rect();root.getBoundsInScreen(r);
 for(AccessibilityNodeInfo n:nodes)if(n.isVisibleToUser()&&label(n).equals("capa")){
  android.graphics.Rect b=new android.graphics.Rect();n.getBoundsInScreen(b);
  if(b.centerY()>r.top+r.height()*0.85&&b.centerX()>r.left+r.width()*0.25&&b.centerX()<r.left+r.width()*0.75)return n;
 }return null;
 }
 void gesture(float x,float y,float ex,float ey,long duration,Runnable complete){
 android.graphics.Path path=new android.graphics.Path();path.moveTo(x,y);if(x!=ex||y!=ey)path.lineTo(ex,ey);
 dispatchCoverGesture(path,duration,complete);
 }
 void dispatchCoverGesture(android.graphics.Path path,long duration,Runnable complete){
 GestureDescription g=new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(path,0,duration)).build();
 boolean accepted=dispatchGesture(g,new GestureResultCallback(){public void onCompleted(android.accessibilityservice.GestureDescription d){if(Run.active)complete.run();}public void onCancelled(android.accessibilityservice.GestureDescription d){if(coverCaptureActive())manualCover();else if(Run.active)finish("Gesto interrompido. Confira o Kwai antes de continuar.");}},null);
 if(!accepted){if(coverCaptureActive())manualCover();else finish("O Android recusou o gesto. Confira a acessibilidade.");}
 }
 void finish(String s){QueueStore.pause(s);Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
 void collect(AccessibilityNodeInfo n,List<AccessibilityNodeInfo> out){if(n==null||out.size()>500)return;out.add(n);for(int i=0;i<n.getChildCount();i++)collect(n.getChild(i),out);}
 String label(AccessibilityNodeInfo n){CharSequence t=n.getText();if(t==null||t.length()==0)t=n.getContentDescription();return t==null?"":t.toString().trim().toLowerCase(Locale.ROOT);}
 AccessibilityNodeInfo exact(List<AccessibilityNodeInfo> list,String... labels){for(AccessibilityNodeInfo n:list)if(n.isVisibleToUser()&&n.isEnabled())for(String s:labels)if(label(n).equals(s))return n;return null;}
 AccessibilityNodeInfo publishButton(List<AccessibilityNodeInfo> nodes,AccessibilityNodeInfo root){
 android.graphics.Rect window=new android.graphics.Rect();root.getBoundsInScreen(window);AccessibilityNodeInfo best=null;int bottom=-1;
 for(AccessibilityNodeInfo n:nodes){String t=label(n);if(!t.equals("publicar")&&!t.equals("postar")&&!t.equals("compartilhar"))continue;
 if(!n.isVisibleToUser()||!n.isEnabled())continue;android.graphics.Rect r=new android.graphics.Rect();n.getBoundsInScreen(r);if(r.centerY()<window.top+window.height()/2)continue;
 AccessibilityNodeInfo parent=n;for(int i=0;parent!=null&&i<4;i++,parent=parent.getParent())if(parent.isClickable()&&parent.isEnabled()&&parent.isVisibleToUser()){if(r.bottom>bottom){best=parent;bottom=r.bottom;}break;}}
 return best;
 }
 boolean click(AccessibilityNodeInfo n){for(int i=0;n!=null&&i<4;i++,n=n.getParent())if(n.isClickable()&&n.isEnabled())return n.performAction(AccessibilityNodeInfo.ACTION_CLICK);return false;}
}

