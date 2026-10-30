#!/usr/bin/env python3
"""Pinned pretrained U2NETP ONNX contract and CPU smoke test.

No user photo is sent to network. Model preflight uses synthetic arrays only.
Production candidate review is a separate local/offline step.
"""
import argparse, json, os, time, hashlib
from pathlib import Path
import numpy as np
import onnxruntime as ort

SHA256 = '309c8469258dda742793dce0ebea8e6dd393174f89934733ecc8b14c76f4ddd8'

def load_model(path):
    digest=hashlib.sha256(Path(path).read_bytes()).hexdigest()
    if digest != SHA256: raise ValueError('Unpinned model SHA-256: '+digest)
    return ort.InferenceSession(str(path),providers=['CPUExecutionProvider'])

def preprocess_rgb(array):
    """Input uint8 RGB 320 x 320, official U2NETP preprocessing convention."""
    assert array.shape==(320,320,3),array.shape
    pix=np.asarray(array,dtype=np.float32)/255.0
    v=np.maximum(1e-6,float(np.max(pix)))
    pix=pix/v
    pix=(pix-np.array([.485,.456,.406],np.float32))/np.array([.229,.224,.225],np.float32)
    return pix.transpose(2,0,1)[None,:,:,:].astype(np.float32)

def predict(session,rgb):
    inp=session.get_inputs()[0];out=session.get_outputs()[0]
    data=preprocess_rgb(rgb)
    t0=time.monotonic();y=session.run([out.name],{inp.name:data})[0]
    elapsed=(time.monotonic()-t0)*1000
    mask=np.asarray(y)[0,0]
    if not np.isfinite(mask).all():raise ValueError('Nonfinite U2NETP saliency mask')
    lo,hi=np.quantile(mask,[.02,.98])
    normalized=np.clip((mask-lo)/max(float(hi-lo),1e-6),0,1)
    return normalized,elapsed,{'raw_min':float(mask.min()),'raw_max':float(mask.max()),'p02':float(lo),'p98':float(hi)}

def main():
    ap=argparse.ArgumentParser();ap.add_argument('--model',required=True);ap.add_argument('--json',default='u2netp_smoke.json');args=ap.parse_args()
    sess=load_model(args.model)
    inp=sess.get_inputs()[0];out=sess.get_outputs()[0]
    assert list(inp.shape)==[1,3,320,320],inp.shape
    assert list(out.shape)==[1,1,320,320],out.shape
    x=np.full((320,320,3),190,np.uint8)
    x[72:255,113:235,:]=[46,64,143]
    m,ms,stats=predict(sess,x)
    report={'sha256':SHA256,'onnxruntime':ort.__version__,'input_name':inp.name,'input_shape':inp.shape,'output_name':out.name,'output_shape':out.shape,'cpu_ms':round(ms,2),'mask_range':[float(m.min()),float(m.max())],**stats,'test':'PASS'}
    Path(args.json).write_text(json.dumps(report,ensure_ascii=False,indent=2))
    print(json.dumps(report,ensure_ascii=False))

if __name__=='__main__':main()