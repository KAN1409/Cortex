from pathlib import Path

p=Path('/tmp/pd/PromptDeck/app/src/main/java/com/kareem/promptdeck/MainActivity.java')
s=p.read_text()

# Add the newly supplied visual command codes to the Photo category without
# replacing the user's slash token with our own wording.
old='''"NeonCity","GoldenHour","MiniWorld","Fog","LuxuryAd","LowAngleHero","VintageFilm","DroneView","Magazine","RainyNight","ProHeadshot","SnowWorld","DoubleExposure","OldMoney","StudioPro","Autumn","MovieScene")'''
new='''"NeonCity","GoldenHour","MiniWorld","Fog","LuxuryAd","LowAngleHero","VintageFilm","DroneView","Magazine","RainyNight","ProHeadshot","SnowWorld","DoubleExposure","OldMoney","StudioPro","Autumn","MovieScene","hdreal","cinematicportrait","doubleexposureviral","Travelstory","storymytravel","cinematicTravel","documentrytravel","Travelvlog","FixFaceResolution")'''
if old not in s:
    raise SystemExit('photo group list not found')
s=s.replace(old,new,1)

# Seed extra codes independently so the previous RC stays stable.
s=s.replace('seedPhotoCommands();}', 'seedPhotoCommands();seedExtraPhotoCommands();}', 1)

insert='''  void base(String title,String sub,boolean showStack){'''
method=r'''  void seedExtraPhotoCommands(){
    String[][] defs=new String[][]{
      {"hdreal","HD Real","/hdreal\nCreate a highly realistic, sharply captured version of the supplied image. Recover believable micro-detail, texture, depth and natural contrast while preserving the exact subject identity, face, pose, framing and scene geometry."},
      {"cinematicportrait","Cinematic Portrait","/cinematicportrait\nTransform the supplied portrait into a cinematic movie still with controlled dramatic lighting, dimensional contrast, realistic skin and strong subject separation. Preserve the person's exact identity, expression, pose and composition."},
      {"doubleexposureviral","Double Exposure","/doubleexposureviral\nCreate a polished double-exposure portrait that blends the subject with a complementary city or environmental story layer. Preserve the subject's recognizable face/profile and keep the composite coherent, photographic and intentional."},
      {"Travelstory","Travel Story","/Travelstory\nTurn the supplied travel photograph into a warm visual travel-story frame with cinematic natural light, stronger atmosphere and narrative depth while preserving the actual subject, vehicle and location structure."},
      {"storymytravel","Rainy Travel Story","/storymytravel\nRestyle the supplied travel image as an atmospheric rainy-night travel story with realistic wet surfaces, reflections, depth and moody practical lighting while keeping the original subject and scene recognizable."},
      {"cinematicTravel","Cinematic Travel","/cinematicTravel\nCreate a cinematic travel photograph from the supplied image using dramatic natural light, filmic contrast, atmospheric depth and premium travel-editorial treatment while preserving the original scene and subject."},
      {"documentrytravel","Documentary Travel","/documentrytravel\nRender the supplied travel image as believable documentary travel photography: natural light, observational composition, realistic texture and restrained processing. Preserve scene authenticity and avoid artificial glamour."},
      {"Travelvlog","Travel Vlog","/Travelvlog\nTurn the supplied image into a polished travel-vlog frame with inviting golden-hour light, vivid but realistic detail and social-ready storytelling while preserving the original subject and location."},
      {"FixFaceResolution","Fix Face Resolution","/FixFaceResolution\nRestore and humanise this image in one pass. Reconstruct lost detail from soft or degraded areas, rebuild sharp edges and fine texture, recover hair strand and iris definition. Then remove all artificial AI smoothness, add visible pores, natural skin unevenness, peach fuzz catching light, subtle blemishes and realistic colour variation. Result should read as a sharply captured real photograph. Keep the exact same face, expression, angle, lighting and composition."}
    };
    for(String[] d:defs){
      if(find(d[0])!=null)continue;
      try{
        JSONObject o=new JSONObject();
        o.put("id",24000+all.size());
        o.put("command",d[0]);
        o.put("category","Photo Editing & Image Generation");
        o.put("description",photoDescription(d[1]));
        o.put("instruction",d[2]);
        all.add(new Cmd(o,false));
      }catch(Exception ignored){}
    }
  }

'''
if insert not in s:
    raise SystemExit('base insertion point not found')
s=s.replace(insert,method+insert,1)

# Place the new entries into meaningful subcategories.
old_tail='''if(group.contains("Photo Editing")){if(has(c,"ProHeadshot,StudioPro,Magazine,OldMoney,LowAngleHero"))return"Portrait & Editorial";if(has(c,"NeonCity,GoldenHour,Fog,RainyNight,SnowWorld,Autumn,MovieScene"))return"Cinematic & Environment";if(has(c,"LuxuryAd,DroneView,VintageFilm"))return"Commercial & Camera Styles";return"Creative Effects";}'''
new_tail='''if(group.contains("Photo Editing")){if(has(c,"ProHeadshot,StudioPro,Magazine,OldMoney,LowAngleHero,hdreal,cinematicportrait,FixFaceResolution"))return"Portrait & Editorial";if(has(c,"NeonCity,GoldenHour,Fog,RainyNight,SnowWorld,Autumn,MovieScene,Travelstory,storymytravel,cinematicTravel,documentrytravel,Travelvlog"))return"Cinematic & Travel";if(has(c,"LuxuryAd,DroneView,VintageFilm"))return"Commercial & Camera Styles";return"Creative Effects";}'''
if old_tail not in s:
    raise SystemExit('photo subcategory block not found')
s=s.replace(old_tail,new_tail,1)

p.write_text(s)
print('PromptDeck RC9 extra photo/travel command codes applied')
