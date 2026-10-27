package br.com.juh.kwaifacil;
final class ScreenKind {
 static final int UNKNOWN=0,TRIM=1,EDITOR=2,CAPTION=3,COVER=4;
 static int detect(boolean next,boolean publish,boolean trimControls,boolean editorControls,boolean captionControls,boolean coverPanel){
  if(coverPanel&&!next)return COVER;
  if(next&&trimControls)return TRIM;
  if(next&&(editorControls||publish))return EDITOR;
  if(!next&&publish&&captionControls)return CAPTION;
  return UNKNOWN;
 }
}
