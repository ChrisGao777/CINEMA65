"""Generate hand-specified synthetic masks, NEVER to be conflated with ML inference."""
from PIL import Image,ImageDraw,ImageFilter
from pathlib import Path
base=Path('/mnt/data/CINEMA65_v084_research')
specs={
 'test_5363':(.65,.45,.075,.15),
 'test_5364':(.65,.53,.26,.37),
 'test_5365':(.49,.47,.24,.45),
 'test_5366':(.55,.53,.19,.34),
 'test_5367':(.49,.52,.14,.38),
 'test_5368':(.60,.49,.21,.44),
 'test_5369':(.67,.56,.17,.36),
 'IMG_6034':(.50,.48,.29,.38),
 'IMG_6062':(.50,.52,.20,.36),
}
for name,(cx,cy,rx,ry) in specs.items():
 m=Image.new('L',(48,48));d=ImageDraw.Draw(m)
 d.ellipse(((cx-rx)*48,(cy-ry)*48,(cx+rx)*48,(cy+ry)*48),fill=255)
 m=m.filter(ImageFilter.GaussianBlur(1.35));m.save(base/f'audit/{name}_synthetic_roi.png')
 # This is deliberately fake relative depth: a spatial gradient, not real MiDaS.
 dep=Image.new('L',(48,48))
 for y in range(48):
  for x in range(48):dep.putpixel((x,y),round(255*(.17+.70*y/47)))
 dep.save(base/f'audit/{name}_synthetic_depth.png')