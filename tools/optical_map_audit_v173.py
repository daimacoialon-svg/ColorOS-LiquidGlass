import math,re,glob,os,statistics
from pathlib import Path
p=Path(__file__).resolve().parents[1]
java=(p/'app/src/main/java/top/liquidglass/vector/GlassLens173.java').read_text()
shader=''.join(re.findall(r'^\s*"((?:[^"\\]|\\.)*)"(?:\s*\+|;)',java,flags=re.M))
# analyze Java code via syntax-only brace balancing outside strings/line comments, focus on both core and new file
for f in [p/'app/src/main/java/top/liquidglass/vector/HookEntry.java',p/'app/src/main/java/top/liquidglass/vector/GlassLens173.java']:
    t=f.read_text(); t=re.sub(r'"(?:\\.|[^"\\])*"','""',t)
    t=re.sub(r'//.*?$','',t,flags=re.M);t=re.sub(r'/\*.*?\*/','',t,flags=re.S)
    cnt=0;minimum=0
    for c in t:
        if c=='{':cnt+=1
        elif c=='}':cnt-=1;minimum=min(cnt,minimum)
    print('Java braces',f.name,cnt,minimum)

# Standalone CPU reference of the V173 shader formula, NOT an Android compile test.
# Geometry probes: full-strength local area ratio (Jacobian det) and max warp.
# Positive determinant is a necessary, not sufficient, condition for a valid warp.
def smooth(a,b,t):
    x=max(0,min(1,(t-a)/(b-a)))
    return x*x*(3-2*x)
def depth(x,y,w,h,r):
    xx=x-w/2; yy=y-h/2
    a=abs(xx)-(w/2-r); b=abs(yy)-(h/2-r)
    return r-math.hypot(max(a,0),max(b,0))-min(max(a,b),0)
def delta(x,y,w,h,r,st):
    small=min(w,h); midw=w/2; midh=h/2
    ux=(x-midw)/midw;uy=(y-midh)/midh
    d=max(depth(x,y,w,h,r),0);band=min(max(12,min(29,small*0.175)),max(4,r*0.70))
    t=max(0,min(1,d/band));e=math.sin(math.pi*t)**2
    gx=0.5*(depth(x+1,y,w,h,r)-depth(x-1,y,w,h,r))
    gy=0.5*(depth(x,y+1,w,h,r)-depth(x,y-1,w,h,r))
    rimx=gx*(band*0.210)*e;rimy=gy*(band*0.210)*e
    dome=math.exp(-0.9*(ux*ux+uy*uy)); ramp=smooth(0,band*1.15,d)
    a=max(5.0,min(16.0,small*0.060))
    dx=(rimx-ux*a*dome*ramp)*st;dy=(rimy-uy*a*dome*ramp)*st
    cap=max(10,min(23,small*0.12));f=cap/math.sqrt(cap*cap+dx*dx+dy*dy)
    return dx*f,dy*f

def det_jac(x,y,w,h,r,st):
    p=delta(x+1,y,w,h,r,st);m=delta(x-1,y,w,h,r,st)
    a=delta(x,y+1,w,h,r,st);b=delta(x,y-1,w,h,r,st)
    ax=1+(p[0]-m[0])*0.5;ay=(p[1]-m[1])*0.5
    bx=(a[0]-b[0])*0.5;by=1+(a[1]-b[1])*0.5
    return ax*by-ay*bx

allShapeMins=[]
for title,w,h,r in [('small',105,105,18),('large',290,290,30),('horizontal',300,105,22),('vertical',105,290,22),('dock',660,160,53),('very tight',90,90,8)]:
    d=[];maxl=0;bad=[]
    for i in range(3,99):
        x=w*i/100
        for j in range(3,99):
            y=h*j/100
            if depth(x,y,w,h,r) <=1.5:continue
            J=det_jac(x,y,w,h,r,1)
            d.append(J)
            if J<=0: bad.append((x,y,J))
            vx,vy=delta(x,y,w,h,r,1)
            maxl=max(maxl,math.hypot(vx,vy))
    allShapeMins.append(min(d))
    print(title,'area min/median/max',round(min(d),3),round(statistics.median(d),3),round(max(d),3),'maxWarp',round(maxl,2),'foldPixels',len(bad))
print('Test finished')

assert all(m>0.0 for m in allShapeMins),"Optical mapping folds in a tested shape"
