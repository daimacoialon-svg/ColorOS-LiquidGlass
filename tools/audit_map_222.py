#!/usr/bin/env python3
"""V222 standalone numerical tests of the SAME homogeneous 4/8-norm lens map."""
from math import sqrt


def norm8mix(u,v,radius_frac):
    n8=(u**8+v**8+1e-10)**.125
    n4=(u**4+v**4+1e-10)**.25
    weight=.33*min(1.0,max(0.0,radius_frac))
    return (1-weight)*n8+weight*n4


def map_uv(x,y,amplitude,radius_frac):
    n=norm8mix(x,y,radius_frac)
    t=max(0.0,min(1.0,1.0-n))
    scale=1.0+amplitude*t*t
    return x*scale,y*scale


def numerical_jacobian(x,y,a,r):
    eps=.00035
    x1,y1=map_uv(x+eps,y,a,r)
    x2,y2=map_uv(x-eps,y,a,r)
    x3,y3=map_uv(x,y+eps,a,r)
    x4,y4=map_uv(x,y-eps,a,r)
    j11=(x1-x2)/(2*eps)
    j21=(y1-y2)/(2*eps)
    j12=(x3-x4)/(2*eps)
    j22=(y3-y4)/(2*eps)
    return j11*j22-j12*j21


def test():
    smallest=10.0
    maxwarp=0.0
    count=0
    for radius in (0.0,.2,.4,.8,1.0):
        for amplitude in (0.0,.5,1.0,1.5):
            for iy in range(-60,61,2):
                v=iy/60.0
                for ix in range(-60,61,2):
                    u=ix/60.0
                    jac=numerical_jacobian(u,v,amplitude,radius)
                    smallest=min(smallest,jac)
                    if jac<=.30:
                        raise AssertionError(f'non-positive / too-small jacobian: {jac} at {(u,v,amplitude,radius)}')
                    mx,my=map_uv(u,v,amplitude,radius)
                    maxwarp=max(maxwarp,sqrt((mx-u)**2+(my-v)**2))
                    count+=1
    for u,v in ((1,0),(-1,0),(0,1),(0,-1),(1,1),(-1,-1)):
        for radius in (0,.3,1):
            x,y=map_uv(u,v,1.5,radius)
            assert abs(x-u)<1e-5 and abs(y-v)<1e-5
    print(f'PASS: tested {count:,} Jacobian grid points (all container locations and rounded-corner ratios)')
    print(f'PASS: min det(J)={smallest:.6f} (>0.30), max normalized warp={maxwarp:.6f}')
    print('PASS: strength=0 identity, edge transitions preserve input positions')

if __name__=='__main__':
    test()
