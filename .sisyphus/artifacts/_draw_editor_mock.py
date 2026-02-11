from PIL import Image, ImageDraw, ImageFont

W, H = 1600, 1000
img = Image.new('RGB', (W, H), '#111214')
d = ImageDraw.Draw(img)

# Colors
surface = '#3A3A3C'
surface2 = '#1C1C1E'
text = '#F5F5F7'
muted = '#98989D'
accent = '#0A84FF'
divider = '#38383A'

# Fonts
try:
    f_title = ImageFont.truetype('arial.ttf', 26)
    f = ImageFont.truetype('arial.ttf', 18)
    f_small = ImageFont.truetype('arial.ttf', 14)
except:
    f_title = ImageFont.load_default()
    f = ImageFont.load_default()
    f_small = ImageFont.load_default()

# Main editor frame
d.rounded_rectangle((30, 30, 1570, 970), radius=22, fill=surface2, outline=divider, width=2)

# Top unified toolbar (48dp concept)
d.rounded_rectangle((60, 60, 1540, 130), radius=14, fill=surface, outline=divider, width=1)
d.text((80, 82), '←  Physics Notes', fill=text, font=f)

# Tool cluster
x = 360
for label in ['H', 'Pen', 'Hi', 'Er', 'Las']:
    d.rounded_rectangle((x, 74, x+42, 116), radius=8, fill='#2C2C2E', outline=divider)
    d.text((x+8, 87), label, fill=text, font=f_small)
    x += 50

# Inline color dots
colors = ['#000000', '#0D47A1', '#B71C1C', '#1B5E20', '#8E24AA']
for i, c in enumerate(colors):
    cx = 635 + i*38
    cy = 95
    d.ellipse((cx-12, cy-12, cx+12, cy+12), fill=c, outline='#DDE6FF' if i==1 else None, width=3)

# Right actions
rx = 900
for label in ['Undo', 'Redo', '147%', 'Lock', '⋮']:
    w = 56 if label=='147%' else 52
    d.rounded_rectangle((rx, 74, rx+w, 116), radius=8, fill='#2C2C2E', outline=divider)
    d.text((rx+8, 87), label, fill=text, font=f_small)
    rx += w + 10

# Canvas area
canvas = (70, 150, 1530, 930)
d.rounded_rectangle(canvas, radius=16, fill='#F8F8F6', outline=divider, width=1)

# Fake handwriting strokes
strokes = [
    [(180,240),(260,220),(340,260),(420,235),(500,270)],
    [(210,330),(280,360),(350,345),(460,372),(560,360)],
    [(700,250),(760,220),(840,210),(920,245)],
    [(980,530),(1090,500),(1180,540),(1260,520),(1370,560)]
]
for pts in strokes:
    d.line(pts, fill='#0E1A38', width=5)

# Floating tool settings panel (anchored near pen)
panel = (420, 180, 790, 600)
d.rounded_rectangle(panel, radius=18, fill='#202124', outline=divider, width=2)
d.text((445, 202), 'Pen Settings', fill=text, font=f)
d.text((740, 202), 'X', fill=muted, font=f)
# Preview
pr = (445, 240, 765, 300)
d.rounded_rectangle(pr, radius=10, fill='#48484A')
d.arc((470,250,740,296), start=190, end=350, fill='#D6D9E0', width=4)

def slider(y, label, val):
    d.text((445, y), f'{label}', fill=text, font=f_small)
    d.text((740, y), str(val), fill=muted, font=f_small)
    y2 = y+24
    d.line((455, y2, 745, y2), fill='#55585D', width=4)
    d.line((455, y2, 565, y2), fill=accent, width=4)
    d.ellipse((560-8, y2-8, 560+8, y2+8), fill='#DDE6FF', outline=accent, width=2)

slider(320, 'Thickness', 7)
slider(385, 'Stabilization', 3)
slider(450, 'Pressure', 0)

d.text((445, 515), 'Colors', fill=text, font=f_small)
for i,c in enumerate(['#111111','#D32F2F','#2E7D32','#1565C0','#FDD835','#8E24AA','#FB8C00','#EEEEEE']):
    cx = 455 + i*34
    cy = 548
    d.ellipse((cx-12,cy-12,cx+12,cy+12), fill=c, outline='#FFFFFF' if i==3 else None, width=2)

# Template picker bottom sheet
sheet = (870, 590, 1490, 910)
d.rounded_rectangle(sheet, radius=18, fill='#202124', outline=divider, width=2)
d.text((895, 612), 'Template', fill=text, font=f)
d.text((1450, 612), 'X', fill=muted, font=f)
# tabs
for i,(lbl,sel) in enumerate([('Built-in',True),('Custom',False)]):
    x0 = 895 + i*120
    d.rounded_rectangle((x0,650,x0+105,686), radius=16, fill=accent if sel else '#2C2C2E', outline=divider)
    d.text((x0+20,661), lbl, fill='white' if sel else muted, font=f_small)
d.text((1145, 662), 'Size: A4 ▼', fill=text, font=f_small)
# template cards
for i,lbl in enumerate(['Blank','Rule','Grid','Dot']):
    x0 = 900 + i*90
    d.rounded_rectangle((x0,742,x0+74,802), radius=8, fill='#2C2C2E', outline=divider)
    d.text((x0+14, 810), lbl, fill=muted, font=f_small)
# apply button
d.rounded_rectangle((1080, 850, 1330, 892), radius=20, fill=accent)
d.text((1138, 862), 'Apply to current', fill='white', font=f_small)

# Full-screen color picker mini mock (floating preview)
cp = (1010, 170, 1490, 520)
d.rounded_rectangle(cp, radius=18, fill='#1B1B1D', outline=divider, width=2)
d.text((1035, 192), 'Color Picker', fill=text, font=f)
# tabs
for i,(lbl,sel) in enumerate([('Swatches',False),('Spectrum',True)]):
    x0 = 1035 + i*120
    d.text((x0, 226), lbl, fill=text if sel else muted, font=f_small)
d.line((1158, 245, 1238, 245), fill=accent, width=3)
# gradient box approximation
for i in range(260):
    r = int(255 - i*0.4)
    g = int(40 + i*0.6)
    b = int(80 + i*0.2)
    d.line((1040+i, 260, 1040+i, 380), fill=(max(0,min(255,r)), max(0,min(255,g)), max(0,min(255,b))))
d.rectangle((1040,260,1300,380), outline=divider, width=1)
d.text((1040, 395), 'Hex: #FF3636   R:255 G:54 B:54', fill=text, font=f_small)

# Behavior callout
d.rounded_rectangle((90, 940, 1530, 967), radius=8, fill='#0f2138')
d.text((105, 946), 'Key behaviors: toolbar always visible, no collapse on draw; long-press tools opens floating panels; long-press color dot opens full-screen picker; view mode disables drawing but keeps toolbar.', fill='#DDE9FF', font=f_small)

# Header
d.text((40, 8), 'Milestone UI Overhaul - Editor UI Understanding (wireframe)', fill='#D6DBE7', font=f)

out = r'C:\onyx\.sisyphus\artifacts\editor-ui-understanding.png'
img.save(out)
print(out)
