from pathlib import Path
import csv
from PIL import Image,ImageDraw,ImageFont
import numpy as np
r=Path(__file__).resolve().parent
out=r/'audit_v086';old=r/'audit_v085_replayed'
oldrows={(x['photo'],x['style']):x for x in csv.DictReader(open(old/'paired_metrics.csv',encoding='utf-8'))}
newrows={(x['photo'],x['style']):x for x in csv.DictReader(open(out/'audit.csv',encoding='utf-8'))}
labels={'test_5364':'Clock','test_5365':'Nested doors','test_5368':'Hourglass','test_5369':'Purple door'}
lines=[]
for stem,title in labels.items():
    for style in ('0','1','2'):
        x=oldrows[(stem+'.jpg',style)];y=newrows[(stem+'.jpg',style)]
        lines.append({'scene':title,'style':style,'old_delta':float(x['model_pixel_difference']),
             'new_delta':float(y['model_delta_rgb']),'old_white':float(x['edited_new_white']),
             'new_white':float(y['model_new_white']),'pure_white':float(y['pure_new_white'])})
with open(r/'same_conditions_comparison.csv','w',newline='',encoding='utf-8') as fh:
    wr=csv.DictWriter(fh,fieldnames=lines[0]);wr.writeheader();wr.writerows(lines)
# same candidate0: original, pure, legacy U2NETP, allocator-gated U2NETP
W=1360; panel=300;titleheight=38;rh=235
canvas=Image.new('RGB',(W,titleheight+rh*len(labels)),(247,247,245));draw=ImageDraw.Draw(canvas)
for j,t in enumerate(('Input','Traditional','v0.8.5 Mask-Guided','v0.8.6 Exposure-Budgeted')):
    draw.text((j*panel+20,9),t,fill=(32,40,47))
for i,(stem,label) in enumerate(labels.items()):
    ims=[]
    for path in [out/(stem+'_original.png'),out/(stem+'_pure.png'),old/(stem+'_model-guided.png'),out/(stem+'_guided.png')]:
        im=Image.open(path).convert('RGB');im.thumbnail((panel-12,rh-35),Image.Resampling.LANCZOS);ims.append(im)
    yy=titleheight+i*rh
    draw.text((4*panel+8,yy+14),label,fill=(33,41,49))
    for j,im in enumerate(ims):
        canvas.paste(im,(j*panel+(panel-im.width)//2,yy+25+(rh-35-im.height)//2))
canvas.save(r/'v086_real_ab_contact.jpg',quality=91,subsampling=0)
for t in lines:print(t)