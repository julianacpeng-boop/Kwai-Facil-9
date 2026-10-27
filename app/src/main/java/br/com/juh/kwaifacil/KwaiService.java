package br.com.juh.kwaifacil;
import android.accessibilityservice.AccessibilityService; import android.view.accessibility.*; import android.os.*; import android.widget.Toast; import java.util.*;
public class KwaiService extends AccessibilityService {
 static KwaiService instance; Handler h=new Handler(Looper.getMainLooper()); long captionMismatchSince=0; boolean hashtagRetry=false; int hashtagRetries=0; long warningUntil=0; long last=0; String lastScreen=""; int verificationAttempts=0,lastProfileCheckAttempt=0; long finalVerificationUntil=0;
 protected void onServiceConnected(){instance=this;QueueStore.init(this);h.post(tick);}
 public void onDestroy(){instance=null;h.removeCallbacks(tick);QueueStore.pause("Assistente desativado. Revise o item em andamento.");super.onDestroy();}
 public void onInterrupt(){QueueStore.pause("Assistente interrompido. Revise o item em andamento.");}
 public void onAccessibilityEvent(AccessibilityEvent e){
 if(!Run.active||e.getPackageName()==null)return;
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
 Runnable tick=new Runnable(){public void run(){if(Run.active)step();h.postDelayed(this,800);}};
 void step(){if(System.currentTimeMillis()-Run.started>(Run.sent?210000:90000)){if(Run.sent&&!uploadInProgressFromRoot())QueueStore.retryUnconfirmed();else finish(Run.sent?"O Kwai ainda não terminou de enviar ou confirmar este vídeo. Confira antes de continuar.":"Tempo limite ao preparar o vídeo. Confira a tela do Kwai; nenhuma nova tentativa será feita.");return;}
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
 if(publish!=null){Run.sentAt=SystemClock.uptimeMillis();if(click(publish)){Run.sent=true;Run.started=System.currentTimeMillis();Run.status="Enviando vídeo; aguardando confirmação explícita do Kwai…";}else Run.sentAt=0;}return;}
 AccessibilityNodeInfo next=exact(nodes,"próximo","avançar","seguinte","next");if(next!=null&&Run.advances<3&&!screen.equals(lastScreen)){if(click(next)){Run.advances++;lastScreen=screen;last=System.currentTimeMillis();Run.status="Avançando para a legenda…";}return;}
 Run.status="Aguardando a tela de edição/legenda do Kwai. Se necessário, avance manualmente. Volte ao Kwai Fácil para parar.";
 }
 void verifyAfterSend(AccessibilityNodeInfo root,List<AccessibilityNodeInfo> nodes){
  long now=SystemClock.uptimeMillis(),elapsed=now-Run.sentAt;
  boolean profile=isProfileScreen(nodes);
  int due=elapsed<60000?0:elapsed<90000?1:elapsed<120000?2:3;
  if(due>verificationAttempts)verificationAttempts=due;
  if(verificationAttempts>0&&profile&&profileShowsCurrentCaption(nodes)){QueueStore.success();return;}
  if(uploadInProgress(nodes)){Run.status=verificationAttempts==0?"Envio iniciado. A primeira checagem será feita após 1 minuto.":"O Kwai ainda mostra envio em andamento; farei a próxima checagem no intervalo programado.";return;}
  if(verificationAttempts>lastProfileCheckAttempt){
   lastProfileCheckAttempt=verificationAttempts;
   if(profile){
    if(verificationAttempts<3){refreshProfile(root);Run.status="Checagem "+verificationAttempts+"/3: atualizando o perfil do Kwai…";}
    else{refreshProfile(root);finalVerificationUntil=now+8000;Run.status="Checagem 3/3: conferindo a publicação mais recente…";}
   }else{
    AccessibilityNodeInfo tab=profileButton(nodes,root);
    if(tab!=null&&click(tab)){finalVerificationUntil=verificationAttempts==3?now+8000:0;Run.status="Checagem "+verificationAttempts+"/3: abrindo o perfil para localizar a legenda deste vídeo…";}
    else{finalVerificationUntil=verificationAttempts==3?now+8000:0;Run.status="Checagem "+verificationAttempts+"/3: aguardando uma tela do Kwai que permita verificar o perfil…";}
   }
  }
  if(verificationAttempts>=3&&finalVerificationUntil>0&&now>=finalVerificationUntil&&!profileShowsCurrentCaption(nodes)){QueueStore.retryUnconfirmed();return;}
  if(verificationAttempts==0)Run.status="Envio iniciado. Vou conferir o Kwai após 1 minuto; se necessário, farei mais duas checagens a cada 30 segundos.";
  else if(verificationAttempts<3&&lastProfileCheckAttempt==verificationAttempts)Run.status="Checagem "+verificationAttempts+"/3 concluída; vou conferir novamente em 30 segundos.";
 }
 boolean uploadInProgressFromRoot(){AccessibilityNodeInfo root=getRootInActiveWindow();if(root==null||!Run.target.contentEquals(root.getPackageName()))return false;List<AccessibilityNodeInfo> nodes=new ArrayList<>();collect(root,nodes);return uploadInProgress(nodes);}
 boolean uploadInProgress(List<AccessibilityNodeInfo> nodes){for(AccessibilityNodeInfo n:nodes){String s=normalizeStatus(label(n));if(s.contains("enviando")||s.contains("publicando")||s.contains("processando")||s.contains("uploading")||s.contains("subindo video"))return true;}return false;}
 boolean isProfileScreen(List<AccessibilityNodeInfo> nodes){boolean edit=false,posts=false,followers=false;for(AccessibilityNodeInfo n:nodes){String s=normalizeStatus(label(n));if(s.contains("editar perfil")||s.equals("edit profile"))edit=true;if(s.equals("publicacoes")||s.equals("videos")||s.equals("posts"))posts=true;if(s.contains("seguidores")||s.contains("followers"))followers=true;}return edit||(posts&&followers);}
 AccessibilityNodeInfo profileButton(List<AccessibilityNodeInfo> nodes,AccessibilityNodeInfo root){android.graphics.Rect win=new android.graphics.Rect();root.getBoundsInScreen(win);for(AccessibilityNodeInfo n:nodes){String s=normalizeStatus(label(n));if(!n.isVisibleToUser()||!n.isEnabled()||!(s.equals("perfil")||s.equals("profile")||s.equals("eu")||s.equals("me")))continue;android.graphics.Rect b=new android.graphics.Rect();n.getBoundsInScreen(b);if(b.centerY()>=win.top+win.height()*0.70)return n;}return null;}
 boolean profileShowsCurrentCaption(List<AccessibilityNodeInfo> nodes){if(!isProfileScreen(nodes))return false;String caption=normalizeStatus(Run.caption);if(caption.length()<8)return false;String probe=caption.substring(0,Math.min(48,caption.length()));for(AccessibilityNodeInfo n:nodes){if(!n.isVisibleToUser())continue;String s=normalizeStatus(label(n));if(s.contains(probe))return true;}return false;}
 void refreshProfile(AccessibilityNodeInfo root){android.graphics.Rect b=new android.graphics.Rect();root.getBoundsInScreen(b);if(b.width()<=0||b.height()<=0)return;float x=b.centerX(),start=b.top+b.height()*0.24f,end=b.top+b.height()*0.63f;gesture(x,start,x,end,450,()->{});}
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
