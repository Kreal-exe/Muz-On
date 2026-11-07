import zlib, struct, sys
N=512; S=4
# viewBox 18..90 -> 0..512
k=N/72.0
bars=[(34,48,60),(46,36,72),(58,42,66),(70,50,58)]  # x, ytop-center, ybottom-center ; width 8, radius 4
def inside(x,y):
    for bx,y0,y1 in bars:
        cx=bx+4
        yy=min(max(y,y0),y1)
        if (x-cx)**2+(y-yy)**2<=16: return True
    return False
bg=(0xE8,0x54,0x2E)
rows=[]
for py in range(N):
    row=bytearray([0])
    for px in range(N):
        c=0
        for sy in range(S):
            for sx in range(S):
                x=18+(px+(sx+.5)/S)/k; y=18+(py+(sy+.5)/S)/k
                if 32<=x<=80 and 30<=y<=78 and inside(x,y): c+=1
        a=c/(S*S)
        row+=bytes(round(b+(255-b)*a) for b in bg)+b'\xff'
    rows.append(bytes(row))
raw=b''.join(rows)
def chunk(t,d): return struct.pack('>I',len(d))+t+d+struct.pack('>I',zlib.crc32(t+d)&0xffffffff)
png=b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',N,N,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(raw,9))+chunk(b'IEND',b'')
open(sys.argv[1],'wb').write(png)
