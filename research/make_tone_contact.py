from PIL import Image,ImageDraw,ImageFont,ImageChops,ImageEnhance
from pathlib import Path
import csv
root=Path(__file__).resolve().parent/'tone_ab'
labels=['IMG_6062','5364','5363','5365','5366','5367','IMG_6034']
try: ft=ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf',17)
except: ft=ImageFont.load_default()
try: title=ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf',22)
except:title=ft
thumb=(240,195)
margin=10;pad=7;head=42;rowheight=217;cols=4
out=Image.new('RGB',(cols*thumb[0]+(cols+1)*margin,len(labels)*rowheight+head+22),(247,247,245))
d=ImageDraw.Draw(out)
headers=['Original','v0.8.6 baseline','v0.8.7 tone safety','Difference x12 (visualization)']
for i,t in enumerate(headers):d.text((margin+i*(thumb[0]+margin),12),t,font=title if i<3 else ft,fill=(35,43,46))
for k,name in enumerate(labels):
  imgs=[Image.open(root/f'{name}-{lab}.png').convert('RGB') for lab in ['original','v086','v087']]
  delta=ImageChops.difference(imgs[1],imgs[2]);delta=ImageEnhance.Contrast(delta).enhance(12)
  imgs.append(delta)
  for i,im in enumerate(imgs):
    im.thumbnail((thumb[0],thumb[1]),Image.Resampling.LANCZOS)
    x=margin+i*(thumb[0]+margin);y=head+k*rowheight
    out.paste(im,(x+(thumb[0]-im.width)//2,y+(thumb[1]-im.height)//2))
  d.text((margin,head+k*rowheight+thumb[1]+2),name,font=ft,fill=(38,52,56))
out.save(root/'v087_tone_contact.jpg',quality=90,optimize=True)
print(root/'v087_tone_contact.jpg')