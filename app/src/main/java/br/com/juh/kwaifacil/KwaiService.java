package br.com.juh.kwaifacil;
import android.accessibilityservice.AccessibilityService; import android.view.accessibility.*; import android.os.*; import android.widget.Toast; import java.util.*;
public class KwaiService extends AccessibilityService {
 static KwaiService instance; Handler h=new Handler(Looper.getMainLooper()); long captionMismatchSince=0; boolean hashtagRetry=false; int hashtagRetries=0; long warningUntil=0; long last=0; String lastScreen=""; int verificationAttempts=0,lastProfileCheckAttempt=0; long finalVerificationUntil=0; long verificationSentAt=-1,verificationActionAt=0,verificationStartedAt=0; int verificationStage=0; boolean profileScrolled=false; boolean readingTest=false; String lastReadCaption=""; boolean verificationMatched=false; long nextVerificationAt=0; String verificationFailure="";
 boolean diagnosticPending=false;
 void scheduleDiagnostic(String phase){
  if(diagnosticPending||Run.active||QueueStore.running)return;
  diagnosticPending=true;
  Toast.makeText(this,"Diagnóstico em 10 segundos. Abra o vídeo no Kwai e deixe a tela parada.",Toast.LENGTH_LONG).show();
  h.postDelayed(()->captureDiagnostic(phase),10000);
 }
 void captureDiagnostic(String phase){
  diagnosticPending=false;
  AccessibilityNodeInfo root=getRootInActiveWindow();
  String pkg=root==null||root.getPackageName()==null?"":root.getPackageName().toString();
  if(!pkg.equals("com.kwai.video")&&!pkg.equals("com.smile.gifmaker")){
   getSharedPreferences("diagnostic",0).edit().putString("error","Não capturei: o Kwai não estava na frente. Tente novamente e abra o vídeo durante os 10 segundos.").putBoolean("show",true).commit();
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
 AccessibilityNodeInfo root=getRootInActiveWindow();if(root==null||!Run.target.contentEquals(root.getPackageName()))return;
 List<AccessibilityNodeInfo> nodes=new ArrayList<>();collect(root,nodes);for(AccessibilityNodeInfo n:nodes)if(label(n).contains("máximo de 5 hashtags"))onHashtagWarning();String screen="";for(AccessibilityNodeInfo n:nodes)screen+=label(n)+"|";
 if(hashtagRetry&&System.currentTimeMillis()>=warningUntil){
 AccessibilityNodeInfo button=publishButton(nodes,root);boolean sameCaption=false;for(AccessibilityNodeInfo n:nodes)if(n.isEditable()&&sameCaption(n.getText()))sameCaption=true;
 if(button!=null&&sameCaption){if(hashtagRetries>=2){finish("O aviso de hashtags voltou. Revise a legenda antes de continuar.");return;}hashtagRetries++;hashtagRetry=false;Run.sent=false;last=0;}
 }
 if(Run.sent){for(AccessibilityNodeInfo n:nodes)if(confirmed(label(n))){QueueStore.success();return;}verifyAfterSend(root,nodes);return;}
 if(System.currentTimeMillis()-last<1600||System.currentTimeMillis()<warningUntil)return;
 // Não atravessa confirmação, login ou avisos de conta.
 for(AccessibilityNodeInfo n:nodes){String t=label(n);if(t.contains("verifique")||t.contains("captcha")||t.contains("fazer login")||t.contains("aceitar termos")){finish("O Kwai pede sua atenção. A execução foi interrompida.");return;}}
 List<AccessibilityNodeInfo> fields=new ArrayList<>();for(AccessibilityNodeInfo n:nodes)if(n.isEditable()&&n.isVisibleToUser())fields.add(n);
 AccessibilityNodeInfo publish=publishButton(nodes,root);
 AccessibilityNodeInfo editorNext=exact(nodes,"próximo","avançar","seguinte","next");
 boolean trimControls=false,editorControls=false,captionControls=false;
 for(AccessibilityNodeInfo node:nodes){if(!node.isVisibleToUser())continue;String t=label(node);
  if(t.equals("velocidade")||t.equals("girar")||t.contains("selecionados"))trimControls=true;
  if(t.equals("texto")||t.equals("stickers")||t.equals("filtro")||t.equals("melhorar"))editorControls=true;
  if(t.contains("descreva sua publicação")||t.equals("adicionar local")||t.equals("configurações avançadas")||t.equals("hashtag"))captionControls=true;
 }
 if(!fields.isEmpty()&&editorNext==null)captionControls=true;
 int kind=ScreenKind.detect(editorNext!=null,publish!=null,trimControls,editorControls,captionControls,coverPanel(nodes,root)!=null);
 if(kind==ScreenKind.TRIM){if(click(editorNext)){last=System.currentTimeMillis();Run.started=last;Run.status="Etapa 1: avançando do corte para a edição…";}return;}
 if(editorNext!=null||kind!=ScreenKind.CAPTION)publish=null;
 if(!Run.coverDone&&(kind==ScreenKind.EDITOR||kind==ScreenKind.CAPTION||Run.coverStage>0)){coverStep(root,nodes,publish,kind==ScreenKind.EDITOR?editorNext:null);return;}
 if(!Run.coverDone&&editorNext!=null){Run.status="Aguardando reconhecer corte ou edição; nenhum gesto de capa foi feito.";return;}
 if(!Run.filled&&publish!=null&&fields.size()==1){AccessibilityNodeInfo field=fields.get(0);Bundle args=new Bundle();args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,Run.caption);if(field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,args)){Run.filled=true;last=System.currentTimeMillis();Run.status="Conferindo a legenda…";}else finish("O Kwai não permitiu preencher o campo. Cole a legenda manualmente.");return;}
 if(Run.filled){boolean verified=false;for(AccessibilityNodeInfo n:fields)if(sameCaption(n.getText()))verified=true;
 if(!verified){for(AccessibilityNodeInfo n:nodes)if(n.isVisibleToUser()&&sameCaption(n.getText())){verified=true;break;}}
 if(!verified){if(captionMismatchSince==0)captionMismatchSince=System.currentTimeMillis();Run.status="Aguardando a legenda estabilizar no Kwai…";if(System.currentTimeMillis()-captionMismatchSince>12000)finish("A legenda exibida difere do arquivo. Revise antes de publicar.");return;}captionMismatchSince=0;
 if(!Run.auto){finish("Legenda preenchida. Revise e toque em Publicar no Kwai.");return;}
 if(publish!=null){Run.sentAt=SystemClock.uptimeMillis();if(click(publish)){resetVerification();Run.sent=true;Run.started=System.currentTimeMillis();Run.status="Enviando vídeo; aguardando confirmação explícita do Kwai…";}else Run.sentAt=0;}return;}
 AccessibilityNodeInfo next=exact(nodes,"próximo","avançar","seguinte","next");if(next!=null&&Run.advances<3&&!screen.equals(lastScreen)){if(click(next)){Run.advances++;lastScreen=screen;last=System.currentTimeMillis();Run.status="Avançando para a legenda…";}return;}
 Run.status="Aguardando a tela de edição/legenda do Kwai. Se necessário, avance manualmente. Volte ao Kwai Fácil para parar.";
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
   long dueAt=nextVerificationAt>0?nextVerificationAt:Run.sentAt+60000L;
   if(now<dueAt){Run.status="Aguardando confirmação; próxima conferência em "+((dueAt-now+999)/1000)+" s.";return;}
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
   nextVerificationAt=now+30000L;verificationStage=0;
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
   if(!expanded&&!matches){Run.status="Aguardando confirmar a legenda inteira; ainda não li o texto.";return;}
   String text=readCaption(nodes,root);
   if(text.isEmpty()){Run.status="Legenda expandida; aguardando o texto acessível…";return;}
   lastReadCaption=text;verificationMatched=matches;
   verificationFailure=matches?"":"A legenda lida é diferente da legenda do TXT.";
   verificationStage=6;verificationActionAt=0;return;
  }
 }
 AccessibilityNodeInfo videoNode(List<AccessibilityNodeInfo> nodes,String name){
  String wanted="com.kwai.video.basis:id/"+name;
  for(AccessibilityNodeInfo n:nodes){
   if(!n.isVisibleToUser()||!n.isEnabled()||!wanted.equals(n.getViewIdResourceName()))continue;
   android.graphics.Rect bounds=new android.graphics.Rect();n.getBoundsInScreen(bounds);
   if(!bounds.isEmpty())return n;
  }return null;
 }
 AccessibilityNodeInfo captionMore(List<AccessibilityNodeInfo> nodes,AccessibilityNodeInfo root){
  AccessibilityNodeInfo n=videoNode(nodes,"slide_play_tv_user_info_desc_more_hide");
  return n!=null&&normalizeStatus(label(n)).equals("mais")?n:null;
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
  clickExactVideoNode(videoNode(nodes,"inside_stream_back_btn"));
 }
 void startReadingTest(String target){
  readingTest=true;Run.target=target;Run.active=true;lastReadCaption="";
  resetVerification();verificationStage=1;verificationAttempts=1;
  verificationStartedAt=SystemClock.uptimeMillis();Run.status="Simulação sem rolagem: Perfil → vídeo mais recente → Mais…";
 }
 void endReadingTest(String result){readingTest=false;Run.active=false;Run.status=result;QueueStore.notice=result;
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
  if(toggle!=null&&normalizeStatus(label(toggle)).equals("mais"))return false;
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
 // A faixa por gesto usa o layout vertical observado na gravação; não há precisão de quadro.
 void coverStep(AccessibilityNodeInfo root,List<AccessibilityNodeInfo> nodes,AccessibilityNodeInfo publish,AccessibilityNodeInfo editorNext){
 long now=System.currentTimeMillis();
 AccessibilityNodeInfo cover=coverPanel(nodes,root);
 if(Run.coverStage==0){
  AccessibilityNodeInfo edit=exact(nodes,"editar capa","editar capa…","editar capa...");
  if(edit==null&&editorNext!=null)edit=exact(nodes,"capa","selecionar capa","adicionar capa","cover");
  if(edit!=null&&click(edit)){Run.coverStage=1;Run.coverAction=now;Run.started=now;Run.status="Abrindo a seleção da capa…";return;}
  if(edit!=null){
   android.graphics.Rect target=new android.graphics.Rect();edit.getBoundsInScreen(target);
   if(!target.isEmpty()){Run.coverStage=1;Run.coverAction=now;Run.started=now;gesture(target.centerX(),target.centerY(),target.centerX(),target.centerY(),80,()->{});return;}
  }
  if(editorNext!=null){
   android.graphics.Rect bounds=new android.graphics.Rect();root.getBoundsInScreen(bounds);
   // Fallback restrito à edição com Próximo, baseado no ícone mostrado pela usuária.
   if(bounds.width()<=0||bounds.height()<bounds.width()){finish("Não reconheci o layout de edição. Abra a capa manualmente.");return;}
   Run.coverStage=1;Run.coverAction=now;Run.started=now;Run.status="Abrindo a capa pelo ícone de imagem +…";
   android.util.DisplayMetrics display=new android.util.DisplayMetrics();((android.view.WindowManager)getSystemService(WINDOW_SERVICE)).getDefaultDisplay().getRealMetrics(display);
   float x=display.widthPixels*0.927f,y=display.heightPixels*0.374f;
   gesture(x,y,x,y,80,()->{});return;
  }
  Run.status="Aguardando o botão de edição da capa…";return;
 }
 if(now-Run.coverAction>20000){finish("Não consegui concluir a capa. Confira o marcador no Kwai antes de continuar.");return;}
 if(Run.coverStage==4){if((publish!=null||editorNext!=null)&&cover==null){Run.coverDone=true;Run.started=now;last=now;Run.status="Capa selecionada; preparando a legenda…";}return;}
 if(cover==null){Run.status="Aguardando a tela Capa…";return;}
 android.graphics.Rect r=new android.graphics.Rect();root.getBoundsInScreen(r);
 android.graphics.Rect cr=new android.graphics.Rect();cover.getBoundsInScreen(cr);
 if(r.width()<=0||r.height()<r.width()||cr.centerY()<r.top+r.height()*0.85){finish("Layout da capa diferente do esperado. Selecione a capa manualmente.");return;}
 if(Run.coverStage==1){
  if(now-Run.coverAction<1200)return;
  // Prefere um controle de progresso acessível quando o Kwai o fornece.
  for(AccessibilityNodeInfo n:nodes){AccessibilityNodeInfo.RangeInfo range=n.getRangeInfo();if(range==null||!n.isVisibleToUser())continue;
   android.graphics.Rect b=new android.graphics.Rect();n.getBoundsInScreen(b);if(b.centerY()<r.top+r.height()*0.78)continue;
   android.os.Bundle args=new android.os.Bundle();args.putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE,(float)(range.getMin()+(range.getMax()-range.getMin())*Run.coverSeconds/Run.coverDuration));
   if(n.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.getId(),args)){Run.coverStage=3;Run.coverAction=now;return;}
  }
  float start=r.left+r.width()*0.094f,end=r.left+r.width()*(0.094f+0.812f*(float)(Run.coverSeconds/Run.coverDuration)),y=cr.centerY()-r.height()*0.077f;
  Run.coverStage=2;gesture(start,y,end,y,500,()->{Run.coverStage=3;Run.coverAction=System.currentTimeMillis();});return;
 }
 if(Run.coverStage==3&&now-Run.coverAction>1500){
  AccessibilityNodeInfo ok=exact(nodes,"concluir","confirmar","salvar","pronto","✓");
  Run.coverStage=4;Run.coverAction=now;
  if(ok!=null&&click(ok))return;
  gesture(r.left+r.width()*0.91f,cr.centerY(),r.left+r.width()*0.91f,cr.centerY(),80,()->{});
 }
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
 android.accessibilityservice.GestureDescription g=new android.accessibilityservice.GestureDescription.Builder().addStroke(new android.accessibilityservice.GestureDescription.StrokeDescription(path,0,duration)).build();
 boolean accepted=dispatchGesture(g,new GestureResultCallback(){public void onCompleted(android.accessibilityservice.GestureDescription d){if(Run.active)complete.run();}public void onCancelled(android.accessibilityservice.GestureDescription d){if(Run.active)finish("Gesto da capa interrompido. Confira a capa antes de continuar.");}},null);
 if(!accepted)finish("O Android recusou o gesto da capa. Confira a acessibilidade.");
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

