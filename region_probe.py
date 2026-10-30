"""Research-only SLIC region proposal, not trained neural segmentation or an Android module."""
import os,cv2,numpy as np
from PIL import Image, ImageDraw
from skimage.segmentation import slic
from skimage.color import rgb2lab
from scipy.ndimage import gaussian_filter, sobel
from scipy.spatial.distance import cdist

def analyze(img):
    w,h=img.size
    maxside=600; scale=min(1,maxside/max(w,h));w1=max(1,round(w*scale));h1=max(1,round(h*scale))
    arr=np.asarray(img.resize((w1,h1),Image.Resampling.LANCZOS)).astype(np.float32)/255.
    lab=rgb2lab(arr); light=lab[:,:,0]; sigma=1.4
    gy,gx=np.gradient(gaussian_filter(light,sigma)); edges=np.hypot(gx,gy)
    labels=slic(arr,n_segments=125,compactness=12,sigma=1.0,start_label=0,convert2lab=True)
    k=int(labels.max()+1)
    features=np.zeros((k,3));centers=np.zeros((k,2));area=np.zeros(k); ed=np.zeros(k); border=np.zeros(k)
    ys,xs=np.mgrid[:h1,:w1]
    for z in range(k):
        m=labels==z;n=m.sum();area[z]=n/(w1*h1);features[z]=lab[m].mean(axis=0);centers[z]=[xs[m].mean()/w1,ys[m].mean()/h1];ed[z]=edges[m].mean()
        border[z]=float(m[:max(1,h1//24),:].any() or m[-max(1,h1//24):,:].any() or m[:,:max(1,w1//24)].any() or m[:,-max(1,w1//24):].any())
    # locally distinctive clusters have high local color contrast and are less likely to be homogeneous sky/background.
    d=cdist(features,features);sp=cdist(centers,centers)
    local = (d*np.exp(-sp**2/(2*.13**2))).sum(axis=1)/np.maximum(1e-8,np.exp(-sp**2/(2*.13**2)).sum(axis=1))
    global_diff = (d*area[None,:]*np.exp(-sp**2/(2*.43**2))).sum(axis=1)/np.maximum(1e-8,(area[None,:]*np.exp(-sp**2/(2*.43**2))).sum(axis=1))
    # corner sky cap: large homogeneous high-position pale blue patches should not dominate.
    blue=np.maximum(0,(arr[:,:,2]-arr[:,:,0])-(arr[:,:,0]-arr[:,:,1])*.2)
    blueMean=np.array([blue[labels==z].mean() for z in range(k)])
    border_penalty = .26 * border
    center=np.exp(-((centers[:,0]-.51)/.64)**2-((centers[:,1]-.53)/.67)**2)
    score=((.47*np.tanh(local/19)+.34*np.tanh(global_diff/35)+.19*np.tanh(ed/8))* (.70+.30*center) * (1-border_penalty) * (1-.55*(blueMean>.13)*(centers[:,1]<.53)))
    # Graph adjacency of related patches so contiguous subject portions support each other.
    adj=[set() for _ in range(k)]
    for m,n in [(labels[:,1:],labels[:,:-1]),(labels[1:,:],labels[:-1,:])]:
        for a,b in zip(m[m!=n],n[m!=n]):adj[int(a)].add(int(b));adj[int(b)].add(int(a))
    smooth=np.array([.65*score[z]+.35*np.mean([score[t] for t in adj[z]]) if adj[z] else score[z] for z in range(k)])
    top=np.argsort(smooth)[-5:][::-1];peak=smooth[top[0]];q75=np.quantile(smooth,.75)
    # Confidence based on distinctiveness of strongest contiguous patch and nonborder evidence.
    conf=max(0,min(1,(peak-q75)/.11)) * max(0,min(1,(peak-.22)/.18))
    # High contrast between nearby patches indicates object parts. Keep fractional evidence mask.
    mask=smooth[labels];mask=np.clip((mask-q75)/max(.08,peak-q75),0,1)**1.8
    mask=gaussian_filter(mask,sigma=1.2)
    return mask,conf,top,centers,smooth

def run(paths,out):
    images=[]
    for name,path in paths:
        image=Image.open(path).convert('RGB'); mask,confidence,top,centers,scores=analyze(image)
        rgb=np.asarray(image.resize((mask.shape[1],mask.shape[0]))).astype(np.float32)
        heat=np.empty_like(rgb);heat[:,:,0]=255;heat[:,:,1]=145;heat[:,:,2]=70
        blend=np.uint8(np.clip(rgb*(1-.68*mask[:,:,None])+heat*(.68*mask[:,:,None]),0,255))
        tiled=Image.new('RGB',(2*500,360),'#eeeeee')
        for z,im in enumerate([image,Image.fromarray(blend)]):
            im.thumbnail((488,320));tiled.paste(im,(6+z*500+(488-im.width)//2,24+(320-im.height)//2))
        ImageDraw.Draw(tiled).text((10,5),f'{name} ; region confidence {confidence:.2f}; top {scores[top[0]]:.2f}',fill='#101010')
        images.append(tiled)
    final=Image.new('RGB',(1000,360*len(images)),'white')
    for i,x in enumerate(images):final.paste(x,(0,i*360))
    final.save(out,quality=93)

if __name__=='__main__':
    from glob import glob
    originals=[]
    for p in sorted(glob('/mnt/data/536[3-9].jpg')):
      i=Image.open(p);w,h=i.size;dest='/mnt/data/CINEMA65_v084_research/test_'+os.path.basename(p)
      i.crop((0,0,w,int(.49*h))).save(dest,quality=95);originals.append((os.path.basename(p),dest))
    for p in ['/mnt/data/IMG_6034.jpeg','/mnt/data/IMG_6062.jpeg']: originals.append((os.path.basename(p),p))
    run(originals,'/mnt/data/CINEMA65_v084_research/region_proposal_contact.jpg')