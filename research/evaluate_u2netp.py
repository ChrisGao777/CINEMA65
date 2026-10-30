#!/usr/bin/env python3
"""Private, offline image-mask evaluation for pretrained U2NETP. No photo uploads.
Usage: python research/evaluate_u2netp.py MODEL OUTDIR photos...
Creates PNG masks, a contact sheet, and a review-only metrics.csv.
"""
import argparse,csv,time,sys
from pathlib import Path
import numpy as np,cv2
from PIL import Image, ImageOps, ImageDraw, ImageFont
from u2netp_probe import load_model,predict

def metrics(mask, raw):
    positive=mask>.50;coverage=float(positive.mean())
    num,labels,stats,centers=cv2.connectedComponentsWithStats(positive.astype('uint8'),8)
    areas=stats[1:,cv2.CC_STAT_AREA] if num>1 else np.array([0])
    total=max(1,positive.sum());largest=float(areas.max()/total)
    perimeter=np.r_[positive[0,:],positive[-1,:],positive[:,0],positive[:,-1]]
    # Counts pixels on image boundary, so sky/horizon touching is suspect as a standalone object.
    border=float(perimeter.mean())
    near=mask>0.8
    raw_sep=float(np.quantile(raw,.90)-np.quantile(raw,.10))
    return {'coverage':coverage,'largest_cc':largest,'border_occupancy':border,'high_conf_coverage':float(near.mean()),'raw_dynamic':raw_sep,'components':int(num-1)}

def gate(measure):
    coverage=measure['coverage'];coh=measure['largest_cc'];border=measure['border_occupancy']
    # Empirical geometric screening, NOT semantic probability of the correct subject.
    if coverage<.006 or coverage>.72: return 'REJECT', 'object occupancy implausible'
    if coh<.48: return 'REJECT','fragmented mask'
    if border>.64: return 'REJECT','border-dominated'
    if coverage>.55 or border>.30 or coh<.73: return 'REVIEW','whole-scene or ambiguous foreground'
    return 'REVIEW','candidate object; require optical safeguards'

def draw_tag(img,tag):
    d=ImageDraw.Draw(img);d.rectangle((0,0,img.width,31),fill=(247,247,242));d.text((8,9),tag,fill=(39,49,60))
    return img

def main():
    ap=argparse.ArgumentParser();ap.add_argument('model');ap.add_argument('output');ap.add_argument('images',nargs='+');args=ap.parse_args()
    out=Path(args.output);out.mkdir(parents=True,exist_ok=True)
    session=load_model(args.model)
    cw=420;panels=[];rows=[]
    for file in map(Path,args.images):
        im=ImageOps.exif_transpose(Image.open(file)).convert('RGB');w,h=im.size
        scaled=im.resize((320,320),Image.Resampling.BILINEAR)
        inp=np.asarray(scaled)
        raw_mask,ms,extra=predict(session,inp)
        restored=cv2.resize(raw_mask,(w,h),interpolation=cv2.INTER_LINEAR)
        raw_stats=metrics(raw_mask,raw_mask)
        verdict,reason=gate(raw_stats)
        mask_u8=np.uint8(np.round(restored*255))
        Image.fromarray(mask_u8).save(out/(file.stem+'_mask.png'))
        Image.fromarray(np.uint8(np.round(raw_mask*255))).save(out/(file.stem+'_mask320.png'))
        heat=np.stack([.96*restored+.15*(1-restored), .35*restored+.5*(1-restored), .05*restored+.48*(1-restored)],axis=2)
        color=np.asarray(im,dtype=np.float32)/255
        overlay=Image.fromarray(np.uint8(np.clip((color*.48+heat*.52)*255,0,255)))
        outline=np.asarray(im).copy()
        edges=cv2.Canny(mask_u8,100,180)
        # Canny gradient is only a visual annotation, not exact subject boundary.
        outline[edges>0]=[255,99,30]
        src=im.copy();contour=Image.fromarray(outline)
        panels.append((file.name,verdict,ms,[src,overlay,contour]))
        rows.append({'photo':file.name,'status':verdict,'reason':reason,'elapsed_ms':round(ms,1),**{k:round(v,4) if isinstance(v,float) else v for k,v in raw_stats.items()}})
        print(file.name,verdict,reason,{k:round(v,3) if isinstance(v,float) else v for k,v in raw_stats.items()},'ms',round(ms,1))
    with (out/'mask_audit.csv').open('w',newline='',encoding='utf-8') as f:
        wr=csv.DictWriter(f,fieldnames=rows[0].keys());wr.writeheader();wr.writerows(rows)
    # 3 columns, photo | overlay | outlined photo. Resize with aspect preservation.
    ph=262;grid=Image.new('RGB',(cw*3,ph*len(panels)),(249,249,247))
    for i,(name,status,ms,images) in enumerate(panels):
        for j,img in enumerate(images):
            img.thumbnail((cw-14,ph-43),Image.Resampling.LANCZOS)
            x=j*cw+(cw-img.width)//2;y=i*ph+38+(ph-43-img.height)//2
            grid.paste(img,(x,y));draw=ImageDraw.Draw(grid)
            draw.text((j*cw+9,i*ph+11),f'{name} | {status} | {int(ms)}ms' if j==0 else ('ONNX saliency overlay' if j==1 else 'mask edge (orange)'),fill=(38,49,62))
    grid.save(out/'mask_audit_contact.jpg',quality=89)
if __name__=='__main__':main()